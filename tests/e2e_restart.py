"""Real HTTP + backend and bundled MySQL process restart proof. Python standard library only.
Requires initialized dedicated bundled MySQL on 127.0.0.1:3307 and tools/start-mysql.ps1 mapping.
Never runs/compiles the submitted Java sources. Writes evidence under deliverables/week1.
"""
import ctypes
from ctypes import wintypes
import json
import os
import socket
import subprocess
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, ProxyHandler

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "deliverables" / "week1"
JAVA = ROOT / ".tools" / "jdk" / "jdk21.0.13_0" / "bin" / "java.exe"
JAR = ROOT / "backend" / "target" / "sanctuary-0.1.0.jar"
BASE = "http://127.0.0.1:8082/api"
OPENER = build_opener(ProxyHandler({}))
events = []
backend = None
db_log = None

def record(name, value):
    events.append({"check": name, "result": value})
    print(name + ": PASS", flush=True)

def call(path, method="GET", body=None, expected=200):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = Request(BASE + path, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with OPENER.open(request, timeout=20) as response:
            status, content = response.status, response.read()
    except HTTPError as error:
        status, content = error.code, error.read()
    assert status == expected, (path, status, content[:500])
    return json.loads(content.decode("utf-8"))

def wait_for(predicate, timeout=30):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except (OSError, URLError, AssertionError) as error:
            last = error
        time.sleep(0.2)
    raise RuntimeError("Service did not reach expected state: " + str(last))

def start_backend():
    global backend
    env = os.environ.copy()
    env.update(DB_URL="jdbc:mysql://127.0.0.1:3307/bug_monster?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
               DB_USERNAME="root", DB_PASSWORD="", SERVER_PORT="8082", SCAN_MIN_INTERVAL_MS="0")
    log = (OUT / "http-backend.log").open("ab")
    backend = subprocess.Popen([str(JAVA), "-jar", str(JAR)], cwd=ROOT / "backend", env=env,
                               stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    log.close()
    wait_for(lambda: call("/health")["status"] == "UP")

def stop_backend():
    global backend
    if backend is not None:
        backend.terminate()
        backend.wait(timeout=15)
        backend = None

def restart_bundled_mysql():
    global db_log
    alias = Path(os.environ.get("MYSQL_RESTART_ALIAS", "R:/"))
    assert alias.exists() and os.path.samefile(alias, ROOT), "Alias must point to this exact project directory"
    home = alias / ".tools" / "mysql" / "mysql-8.4.7-winx64"
    data = alias / ".tools" / "mysql-data"
    assert data.resolve() == (ROOT / ".tools" / "mysql-data").resolve()
    env = os.environ.copy()
    env["MYSQL_PWD"] = ""
    query = subprocess.run([str(home / "bin" / "mysql.exe"), "--no-defaults",
                            "--host=127.0.0.1", "--port=3307", "--user=root",
                            "--batch", "--skip-column-names", "--execute=SELECT @@pid_file"],
                           check=True, capture_output=True, env=env)
    pid_file = Path(query.stdout.decode("utf-8").strip())
    old_pid = int(pid_file.read_text().strip())
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x00100000, False, old_pid)
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    subprocess.run([str(home / "bin" / "mysqladmin.exe"), "--no-defaults", "--host=127.0.0.1",
                    "--port=3307", "--user=root", "shutdown"], check=True, env=env, capture_output=True)
    try:
        # mysqladmin may return before the Windows server releases InnoDB files.
        assert kernel.WaitForSingleObject(handle, 30000) == 0, "Old MySQL process did not finish shutdown"
    finally:
        kernel.CloseHandle(handle)
    db_log = (OUT / "mysql-restart.log").open("ab")
    process = subprocess.Popen([str(home / "bin" / "mysqld.exe"), "--no-defaults",
                                "--basedir=" + str(home), "--datadir=" + str(data),
                                "--port=3307", "--bind-address=127.0.0.1", "--mysqlx=0", "--console"],
                               stdout=db_log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    def ready():
        if process.poll() is not None:
            raise RuntimeError("MySQL restart failed")
        with socket.create_connection(("127.0.0.1", 3307), timeout=1):
            return True
    wait_for(ready)
    record("mysql_process_restarted", {"newPid": process.pid, "version": "8.4.7", "sameDataDirectory": True})

try:
    OUT.mkdir(parents=True, exist_ok=True)
    start_backend()
    draft = "class SavedDraft { int value() { return 42; } }"
    project = call("/projects", "POST", {"name": "HTTP restart proof", "sourceCode": ""}, 201)
    project_id = project["id"]
    project = call("/projects/" + str(project_id), "PUT", {"name": "重启持久化证明", "sourceCode": draft})
    assert project["sourceCode"] == draft
    record("create_and_save_project", {"projectId": project_id})
    demo = (ROOT / "tests" / "samples" / "WeekOneDemo.java").read_text(encoding="utf-8")
    first = call("/projects/" + str(project_id) + "/scans", "POST", {"sourceCode": demo}, 201)
    assert first["status"] == "SUCCESS" and first["score"] == 75 and len(first["issues"]) == 2
    assert first["baselineScanId"] is None
    record("two_real_ast_rules_and_score", first)
    failed = call("/projects/" + str(project_id) + "/scans", "POST", {"sourceCode": "class A { void broken("}, 201)
    assert failed["status"] == "PARSE_ERROR" and failed["score"] is None and failed["parseProblems"]
    assert failed["baselineScanId"] == first["scanId"]
    record("parse_error_is_persisted_without_replacing_baseline", failed)
    third = call("/projects/" + str(project_id) + "/scans", "POST", {"sourceCode": "class Fine {}"}, 201)
    assert third["baselineScanId"] == first["scanId"] and third["score"] == 100
    record("next_success_uses_last_successful_scan", third)
    before = call("/projects/" + str(project_id) + "/scans")
    assert len(before) == 3
    for bad in [" \n\t", "x" * 20001]:
        call("/projects/" + str(project_id) + "/scans", "POST", {"sourceCode": bad}, 400)
    call("/projects", "POST", {"name": "spoof", "owner_id": 9}, 400)
    call("/projects/" + str(project_id) + "/scans", "POST", {"sourceCode": "x" * 270000}, 413)
    assert call("/projects/" + str(project_id) + "/scans") == before
    record("invalid_input_owner_spoofing_and_http_body_limit", {"historyUnchanged": True})
    old_pid = backend.pid
    stop_backend()
    restart_bundled_mysql()
    start_backend()
    assert backend.pid != old_pid
    after_projects = call("/projects")
    saved = next(item for item in after_projects if item["id"] == project_id)
    assert saved["sourceCode"] == draft and saved["lastSuccessfulScanId"] == third["scanId"]
    assert call("/projects/" + str(project_id) + "/scans") == before
    for original in [first, failed, third]:
        assert call("/scans/" + str(original["scanId"])) == original
    record("backend_and_database_restart_preserves_draft_snapshots_issues_errors_scores_history",
           {"oldBackendPid": old_pid, "newBackendPid": backend.pid, "projectId": project_id,
            "scanIds": [item["scanId"] for item in [first, failed, third]]})
    (OUT / "http-restart-evidence.json").write_text(json.dumps({"status": "PASS", "checks": events},
                                                    ensure_ascii=False, indent=2), encoding="utf-8")
    print("All HTTP/restart assertions passed.", flush=True)
finally:
    stop_backend()
    if db_log:
        db_log.close()
