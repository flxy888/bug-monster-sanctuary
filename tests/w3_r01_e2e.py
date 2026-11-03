"""W3-R01: isolated real HTTP/MySQL evidence. Restarts only its own backend; never MySQL."""
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import URLError
import argparse, hashlib, itertools, json, os, re, socket, subprocess, time, traceback
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'deliverables/week3-fix'
ENV=json.loads((OUT/'test-environment.json').read_text(encoding='utf-8-sig'))
SCHEMA=ENV['schema'];PORT=int(ENV['backendPort'])
assert re.fullmatch(r'bug_monster_w3_r01_[0-9_]+',SCHEMA), 'Only the developer-owned verification schema is allowed'
BASE=f'http://127.0.0.1:{PORT}/api'
MYSQL=Path('R:/.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe')
OPENER=build_opener(ProxyHandler({}))

def sql_rows(query):
    p=subprocess.run([str(MYSQL),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root',f'--database={SCHEMA}','--default-character-set=utf8mb4','--xml'],
        input=query,text=True,encoding='utf-8',stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    if p.returncode:raise RuntimeError(p.stderr)
    return [{f.attrib['name']:f.text for f in row} for row in ET.fromstring(p.stdout).findall('row')]
def call(path,method='GET',body=None):
    request=Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})
    with OPENER.open(request,timeout=20) as r:return json.loads(r.read())
def method(n,fixed=False):
    return 'void m('+','.join('int a'+str(i) for i in range(n))+'){try{work();}catch(Exception ex){'+('handle(ex);' if fixed else '')+'}}'
def source(*methods):return 'class A {'+'\n'.join(methods)+'}'
def metrics(project):
    project=int(project)
    row=sql_rows(f"SELECT (SELECT total_xp FROM users WHERE id=1) user_xp,(SELECT COALESCE(SUM(contained_count),0) FROM user_monsters WHERE owner_id=1) user_contained,(SELECT COUNT(*) FROM reward_logs WHERE project_id={project}) reward_rows,(SELECT COALESCE(SUM(xp),0) FROM reward_logs WHERE project_id={project}) project_xp,(SELECT GROUP_CONCAT(lineage_key ORDER BY lineage_key) FROM issue_lineages WHERE project_id={project}) lineage_keys,(SELECT state_json FROM project_identity_state WHERE project_id={project}) state_json")[0]
    for k in ('user_xp','user_contained','reward_rows','project_xp'):row[k]=int(row[k])
    row['lineage_keys']=row['lineage_keys'].split(',') if row['lineage_keys'] else []
    stored=row.pop('state_json');row['state']=json.loads(stored) if stored else None
    row['rewards']=sql_rows(f'SELECT lineage_key,xp,identity_policy,source_scan_id,resolved_scan_id FROM reward_logs WHERE project_id={project} ORDER BY id')
    return row
def start(jar,log):
    with socket.socket() as s:s.bind(('127.0.0.1',PORT)) # Reject an occupied port instead of adopting another server.
    env=os.environ.copy();env.update(DB_URL=f'jdbc:mysql://127.0.0.1:3307/{SCHEMA}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USERNAME='root',DB_PASSWORD='',SERVER_PORT=str(PORT))
    p=subprocess.Popen([str(ROOT/'.tools/jdk/jdk21.0.13_0/bin/java.exe'),'-jar',str(jar)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(120):
        if p.poll() is not None:raise RuntimeError('Owned backend exited during startup')
        try:
            if call('/health')['status']=='UP':return p
        except (URLError,OSError):time.sleep(.2)
    stop(p);raise RuntimeError('Owned backend startup timeout')
def stop(p):
    if p is not None and p.poll() is None:p.terminate();p.wait(20)
def create(label):return call('/projects','POST',{'name':'W3-R01 HTTP '+label})['id']
def scan(project,code,records,label):
    before=metrics(project);response=call(f'/projects/{project}/scans','POST',{'sourceCode':code});after=metrics(project)
    record={'label':label,'projectId':project,'source':code,'response':response,'mysqlBefore':before,'mysqlAfter':after};records.append(record);return record
def prepare(records,label):
    project=create(label)
    first=scan(project,source(method(6)),records,label+' baseline')
    second=scan(project,source(method(5)),records,label+' valid 6-to-5 repair')
    assert first['response']['comparisonStatus']=='BASELINE_CREATED' and first['response']['xpEarned']==0
    assert second['response']['comparisonStatus']=='COMPARED' and second['response']['xpEarned']==20
    key=next(i['lineageKey'] for i in first['response']['issues'] if i['ruleCode']=='EMPTY_CATCH')
    return {'projectId':project,'catchKey':key,'lineageKeys':second['mysqlAfter']['lineage_keys']}
def frozen(case,record,policy='week3-match-v2'):
    r=record['response'];before=record['mysqlBefore'];after=record['mysqlAfter']
    assert r['comparisonStatus']=='COMPARED',r['comparisonStatus']
    assert r['xpEarned']==0 and r['containedCount']==0
    assert not any(x['lineageKey']==case['catchKey'] and x['resolution']=='CONFIRMED_FIXED' for x in r['resolutions'])
    assert after['user_xp']==before['user_xp'] and after['user_contained']==before['user_contained']
    assert after['reward_rows']==before['reward_rows'] and after['rewards']==before['rewards']
    assert after['lineage_keys']==case['lineageKeys']
    assert after['state']['policy']==policy
def main(prepare_v1):
    evidence={'schema':SCHEMA,'port':PORT,'status':'RUNNING','records':[]};records=evidence['records'];p=None
    jar=OUT/'baseline-v1.jar' if prepare_v1 else ROOT/'backend/target-w3-r01/sanctuary-0.1.0.jar'
    evidence['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
    target=OUT/('legacy-v1-evidence.json' if prepare_v1 else 'http-mysql-evidence.json')
    with (OUT/('backend-v1.log' if prepare_v1 else 'backend-fixed.log')).open('ab') as log:
        try:
            p=start(jar,log);evidence['backendPidBefore']=p.pid
            if prepare_v1:
                evidence['cases']=[]
                for clean_first in (True,False):
                    case=prepare(records,'legacy clean-first='+str(clean_first));case['cleanFirst']=clean_first
                    third=scan(case['projectId'],source(*( (method(5,True),method(6)) if clean_first else (method(6),method(5,True)) )),records,'legacy conflicting aliases')
                    assert third['response']['score']==77 and third['response']['xpEarned']==(20 if clean_first else 0)
                    assert third['mysqlAfter']['state']['policy']=='week3-match-v1'
                    case['thirdStepXp']=third['response']['xpEarned'];case['history']=[r['response'] for r in records if r['projectId']==case['projectId']]
                    evidence['cases'].append(case)
            else:
                evidence['cases']=[]
                for clean_first in (True,False):
                    case=prepare(records,'v2 clean-first='+str(clean_first));case['cleanFirst']=clean_first
                    for i in range(4):
                        order=(method(5,True),method(6)) if (clean_first == (i%2==0)) else (method(6),method(5,True))
                        third=scan(case['projectId'],source(*order),records,'v2 conflict/reorder '+str(i));frozen(case,third)
                        assert third['response']['score']==77 and len(third['response']['issues'])==2
                    case['thirdStepXp']=0;case['projectXp']=metrics(case['projectId'])['project_xp'];assert case['projectXp']==20
                    evidence['cases'].append(case)
                triple=prepare(records,'three historical aliases')
                assert scan(triple['projectId'],source(method(4)),records,'extend unique alias to four parameters')['response']['xpEarned']==0
                for order in itertools.permutations((method(4,True),method(5),method(6))):
                    frozen(triple,scan(triple['projectId'],source(*order),records,'three-alias permutation'))
                saved={c['projectId']:metrics(c['projectId']) for c in evidence['cases']}
                prior_responses=[r['response'] for r in records]
                stop(p);p=start(jar,log);evidence['backendPidAfter']=p.pid;assert p.pid!=evidence['backendPidBefore']
                for c in evidence['cases']:
                    assert metrics(c['projectId'])==saved[c['projectId']]
                    frozen(c,scan(c['projectId'],source(method(6),method(5,True)),records,'after backend restart/reorder'))
                for old in prior_responses:assert call('/scans/'+str(old['scanId']))==old
                evidence['historyResponsesUnchangedAfterRestart']=len(prior_responses)
                for c in evidence['cases']:
                    for code in (source(method(5,True)),source(method(6)),source(method(6,True)),source(method(5)),source(method(5,True)),source(),source(method(5)),source(method(5,True)),source(method(5,True),method(6),method(7)),source(method(7,True)),source(method(7)),source(method(7,True)),source(method(5)),source(method(5,True))):
                        frozen(c,scan(c['projectId'],code,records,'contraction/migration/removal/recurrence'))
                    c['finalMetrics']=metrics(c['projectId']);assert c['finalMetrics']['project_xp']==20
                legacy=json.loads((OUT/'legacy-v1-evidence.json').read_text(encoding='utf-8'))
                evidence['legacyUpgrades']=[]
                for c in legacy['cases']:
                    before=metrics(c['projectId']);upgrade=scan(c['projectId'],source(method(5,True)),records,'real v1 winner/placeholder upgrade with only one current candidate')
                    assert upgrade['response']['comparisonStatus']=='INCOMPATIBLE_RULESET' and upgrade['response']['xpEarned']==0
                    assert upgrade['mysqlAfter']['rewards']==before['rewards'] and upgrade['mysqlAfter']['lineage_keys']==before['lineage_keys']
                    for code in (source(method(5)),source(method(5,True)),source(method(6),method(5,True)),source(method(5,True),method(6))):
                        frozen(c,scan(c['projectId'],code,records,'real v1 post-upgrade comparable regression'))
                    for old in c['history']:assert call('/scans/'+str(old['scanId']))==old
                    evidence['legacyUpgrades'].append({'projectId':c['projectId'],'priorProjectXp':before['project_xp'],'finalProjectXp':metrics(c['projectId'])['project_xp'],'oldHistoryUnchanged':True})
            evidence['status']='PASS'
        except Exception:
            evidence['status']='FAILED';evidence['error']=traceback.format_exc();raise
        finally:
            stop(p);target.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:evidence[k] for k in ('status','schema','port','backendPidBefore')},ensure_ascii=False))
    if not prepare_v1:print(json.dumps({'backendPidAfter':evidence['backendPidAfter'],'cases':[{'projectId':c['projectId'],'thirdStepXp':c['thirdStepXp'],'projectXp':c['projectXp']} for c in evidence['cases']],'records':len(records),'legacyUpgrades':evidence['legacyUpgrades']},ensure_ascii=False))
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--prepare-v1',action='store_true');main(parser.parse_args().prepare_v1)
