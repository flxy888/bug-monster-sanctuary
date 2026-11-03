from pathlib import Path
import subprocess,json,datetime,hashlib,xml.etree.ElementTree as ET,re
R=Path.cwd();O=R/'deliverables/week4';O.mkdir(exist_ok=True)
MYSQL=R/'.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe'
def sql(q):
 p=subprocess.run([str(MYSQL),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','--default-character-set=utf8mb4','--xml'],input=q,text=True,encoding='utf-8',capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW)
 if p.returncode:raise RuntimeError(p.stderr)
 return [{f.attrib['name']:f.text for f in row} for row in ET.fromstring('<results>'+re.sub(r'<\?xml[^>]*\?>','',p.stdout)+'</results>').findall('resultset/row')] if p.stdout.strip().startswith('<?xml') else []
def init(schema):
 assert schema.startswith('bug_monster_w4_')
 for n in ('01-schema.sql','04-week2-upgrade.sql','02-seed.sql','05-week3-upgrade.sql','06-week4-upgrade.sql'):
  sql((R/'database'/n).read_text(encoding='utf-8').replace('bug_monster',schema))
def digest_rows(schema):
 return {t:sql(f'SELECT * FROM `{schema}`.`{t}` ORDER BY 1,2') for t in ('users','projects','scan_runs','scan_issues','issue_lineages','project_identity_state','reward_logs','user_monsters')}
stamp=datetime.datetime.now().strftime('%Y%m%d_%H%M%S');schema='bug_monster_w4_'+stamp;old=schema+'_upgrade'
# Keep original dump, restore without touching original schema. mysql-data existed; never initialize it.
dump=subprocess.run([str(MYSQL.with_name('mysqldump.exe')),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','--single-transaction','--set-gtid-purged=OFF','--skip-add-drop-table','bug_monster'],capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW)
assert dump.returncode==0,dump.stderr
(O/'pre-week4-backup.sql').write_bytes(dump.stdout)
sql(f'CREATE DATABASE `{old}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; USE `{old}`;'+dump.stdout.decode('utf-8'))
before=digest_rows(old);init(old);init(old);after=digest_rows(old)
for t in before:
 assert len(before[t])==len(after[t]),t
 for a,b in zip(before[t],after[t]): assert all(b[k]==v for k,v in a.items()),(t,a)
init(schema);init(schema)
env={'schema':schema,'upgradeSchema':old,'backendPort':18087,'mysqlPort':3307,'frontendPort':5174}
(O/'test-environment.json').write_text(json.dumps(env,indent=2),encoding='utf-8')
(O/'migration-evidence.json').write_text(json.dumps({'status':'PASS','initializationsPerSchema':2,'environment':env,'preserved':{t:len(v) for t,v in before.items()},'backupSha256':hashlib.sha256(dump.stdout).hexdigest(),'server':sql('SELECT VERSION() version;')},indent=2),encoding='utf-8')
protected=[R/'AGENTS.md',R/'README.md',R/'docs/02-任务看板.md',R/'docs/10-W3-R01复验与第三周验收.md',R/'tasks/第四周开发派工单.md']
protected+= [p for d in ('week1','week2','week3','week3-fix','reviews') for p in (R/'deliverables'/d).rglob('*') if p.is_file()]
(O/'protected-after-commander-sync.json').write_text(json.dumps({str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in protected},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(env))

