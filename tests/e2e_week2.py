"""Real HTTP and backend restart evidence for week two. Leaves week-one evidence untouched."""
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import HTTPError, URLError
import subprocess, os, json, time

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'deliverables/week2'
BASE='http://127.0.0.1:8083/api'
opener=build_opener(ProxyHandler({}))

def call(path,method='GET',body=None,expected=200):
    data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
    req=Request(BASE+path,data=data,method=method,headers={'Content-Type':'application/json'})
    try:
        with opener.open(req,timeout=15) as response: status,content=response.status,response.read()
    except HTTPError as e:status,content=e.code,e.read()
    assert status==expected,(path,status,content[:300])
    return json.loads(content)

def start(log):
    env=os.environ.copy();env.update(DB_URL='jdbc:mysql://127.0.0.1:3307/bug_monster?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USERNAME='root',DB_PASSWORD='',SERVER_PORT='8083')
    process=subprocess.Popen([str(ROOT/'.tools/jdk/jdk21.0.13_0/bin/java.exe'),'-jar',str(ROOT/'backend/target/sanctuary-0.1.0.jar')],
                             cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(150):
        if process.poll() is not None:raise RuntimeError('Backend exited during start')
        try:
            if call('/health')['status']=='UP':return process
        except (URLError,OSError):pass
        time.sleep(.2)
    process.terminate();process.wait(15);raise RuntimeError('Backend start timed out')

def main():
    process=None
    evidence={}
    with (OUT/'e2e-backend.log').open('ab') as log:
        try:
            process=start(log);evidence['firstBackendPid']=process.pid
            health,catalog=call('/health'),call('/monsters/catalog')
            assert health['enabledRuleCodes']==catalog['enabledRuleCodes'] and len(catalog['definitions'])==5
            before=json.loads((OUT/'database-before.json').read_text(encoding='utf-8'))
            owner_ids={int(p['id']) for p in before['projects'] if p['owner_id']=='1'}
            legacy=[]
            for stored in before['scan_runs']:
                if int(stored['project_id']) not in owner_ids:continue
                current=call('/scans/'+stored['id']);legacy.append(current)
                for field,column in [('sourceCode','source_code'),('rulesetVersion','ruleset_version'),('status','status')]:assert current[field]==stored[column]
                assert current['score']==(int(stored['score']) if stored['score'] is not None else None)
                assert current['enabledRuleCodes']==json.loads(stored['enabled_rule_codes'])
                assert current['metadataSnapshot'] is None
                old_issues=[i for i in before['scan_issues'] if i['scan_id']==stored['id']]
                assert len(old_issues)==len(current['issues'])
                for old,issue in zip(sorted(old_issues,key=lambda i:(int(i['start_line']),i['rule_code'],int(i['id']))),current['issues']):
                    for f,c in [('ruleCode','rule_code'),('monsterName','monster_name'),('suggestion','suggestion'),('message','message'),('astFingerprint','ast_fingerprint')]:assert issue[f]==old[c]
            project=call('/projects','POST',{'name':'第二周 HTTP 重启验证','sourceCode':'class SavedDraft {}'},201)
            sample=(ROOT/'tests/samples/WeekTwoDemo.java').read_text(encoding='utf-8')
            first=call(f"/projects/{project['id']}/scans",'POST',{'sourceCode':sample},201)
            assert first['score']==49 and len(first['issues'])==5
            assert sorted(i['ruleCode'] for i in first['issues'])==catalog['enabledRuleCodes']
            assert first['metadataSnapshot']==catalog and first['rulesetVersion']=='week2-v1'
            failure=call(f"/projects/{project['id']}/scans",'POST',{'sourceCode':'class A {'},201)
            clean=call(f"/projects/{project['id']}/scans",'POST',{'sourceCode':'class A {}'},201)
            assert failure['score'] is None and failure['status']=='PARSE_ERROR' and failure['baselineScanId']==first['scanId']
            assert clean['score']==100 and not clean['issues'] and clean['baselineScanId']==first['scanId']
            expected=[call('/scans/'+str(s['scanId'])) for s in (first,failure,clean)]
            process.terminate();process.wait(20);process=start(log);evidence['secondBackendPid']=process.pid
            assert evidence['firstBackendPid']!=process.pid
            for old in legacy+expected:assert call('/scans/'+str(old['scanId']))==old
            saved=next(p for p in call('/projects') if p['id']==project['id'])
            assert saved['sourceCode']=='class SavedDraft {}' and saved['lastSuccessfulScanId']==clean['scanId']
            evidence.update(status='PASS',realDatabase=True,legacyScansUnchanged=len(legacy),health=health,catalog=catalog,newScans=expected,projectAfterRestart=saved)
            (OUT/'e2e-http-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
            print(json.dumps({'status':'PASS','legacyScansUnchanged':len(legacy),'newScanIds':[s['scanId'] for s in expected], 'backendPids':[evidence['firstBackendPid'],process.pid]},ensure_ascii=False))
        finally:
            if process and process.poll() is None:process.terminate();process.wait(20)

if __name__=='__main__':main()
