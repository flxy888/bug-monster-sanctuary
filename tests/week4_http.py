"""Real HTTP/MySQL week4 verification. Only self-owned port/schema/process; never clears original DB."""
from pathlib import Path
from urllib.request import Request,build_opener,ProxyHandler
from urllib.error import HTTPError
import subprocess,json,os,time,socket,hashlib,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1];O=R/'deliverables/week4';E=json.loads((O/'test-environment.json').read_text(encoding='utf-8'));SCHEMA=E['schema'];PORT=E['backendPort'];assert SCHEMA.startswith('bug_monster_w4_')
OP=build_opener(ProxyHandler({}));records=[];BASE=f'http://127.0.0.1:{PORT}/api';MYSQL=R/'.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe';JAR=R/'backend/target-week4/sanctuary-0.1.0.jar';P=None
BAD='class A {void m(){try{work();}catch(Exception e){}}}';GOOD=BAD.replace('catch(Exception e){}','catch(Exception e){handle(e);}')
def sql(q):
 p=subprocess.run([str(MYSQL),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root',f'--database={SCHEMA}','--default-character-set=utf8mb4','--xml'],input=q,text=True,encoding='utf-8',capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW);assert p.returncode==0,p.stderr
 return [{f.attrib['name']:f.text for f in row} for row in ET.fromstring(p.stdout).findall('row')]
def call(path,method='GET',body=None,expected=200):
 data=None if body is None else json.dumps(body).encode();req=Request(BASE+path,data=data,method=method,headers={'Content-Type':'application/json'})
 try:
  with OP.open(req,timeout=30) as r: status=r.status;raw=r.read()
 except HTTPError as ex:status=ex.code;raw=ex.read()
 response=json.loads(raw) if raw else None;records.append({'time':time.time(),'path':path,'method':method,'status':status,'body':body,'response':response});assert status==expected,(status,response,path);return response
def start():
 global P
 with socket.socket() as s:s.bind(('127.0.0.1',PORT))
 env=os.environ.copy();env.update(DB_URL=f'jdbc:mysql://127.0.0.1:3307/{SCHEMA}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USERNAME='root',DB_PASSWORD='',SERVER_PORT=str(PORT),SCAN_MIN_INTERVAL_MS='1000')
 log=(O/'backend-http.log').open('ab');P=subprocess.Popen([str(R/'.tools/jdk/jdk21.0.13_0/bin/java.exe'),'-jar',str(JAR)],cwd=R,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 for _ in range(120):
  try:
   h=call('/health');assert h['matchingPolicyVersion']=='week3-match-v2';return h
  except OSError:time.sleep(.2)
 raise RuntimeError('Owned backend failed to start')
def stop():
 global P
 if P and P.poll() is None:P.terminate();P.wait(20)
def create(label,source=BAD):return call('/projects','POST',{'name':'W4 HTTP '+label,'sourceCode':source},201)['id']
def scan(id,source,wait=True):
 if wait:time.sleep(1.03)
 return call(f'/projects/{id}/scans','POST',{'sourceCode':source},201)
def counts(id):return sql(f"SELECT (SELECT total_xp FROM users WHERE id=1) xp,(SELECT COUNT(*) FROM reward_logs WHERE project_id={id}) rewards,(SELECT COUNT(*) FROM scan_runs WHERE project_id={id}) scans,(SELECT COUNT(*) FROM scan_issues WHERE project_id={id}) issues,(SELECT state_json FROM project_identity_state WHERE project_id={id}) state")[0]
def main():
 evidence={'schema':SCHEMA,'jarSha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'records':records}
 try:
  evidence['health']=start();evidence['pidBefore']=P.pid
  id=create('baseline repair');xp=call('/users/me/stats')['totalXp'];values=[]
  for source in (BAD,GOOD,GOOD,BAD,GOOD):values.append(scan(id,source))
  assert [s['xpEarned'] for s in values]==[0,20,0,0,0];assert [s['score'] for s in values]==[85,100,100,85,100]
  evidence['normal']={'projectId':id,'xpBefore':xp,'xpAfter':call('/users/me/stats')['totalXp'],'scanIds':[s['scanId'] for s in values]}
  before=counts(id);tree=call('/scans/'+str(values[0]['scanId'])+'/ast');assert tree['scanId']==values[0]['scanId'] and tree['nodesReturned']>0;assert counts(id)==before
  call(f'/projects/{id}/scans','POST',{'sourceCode':GOOD},429);assert counts(id)==before
  five=create('five rules',(R/'tests/samples/WeekTwoDemo.java').read_text(encoding='utf-8'));f=scan(five,(R/'tests/samples/WeekTwoDemo.java').read_text(encoding='utf-8'),False);assert f['score']==49 and len(f['issues'])==5
  failed=scan(five,'class A {void broken(');a=call('/scans/'+str(failed['scanId'])+'/ast');assert a['root'] is None and a['parseProblems']
  # Distinct snapshots, unknown metadata and saved score visible in actual history.
  hist=call(f'/projects/{five}/scans');assert hist[0]['score'] is None and hist[1]['score']==49 and hist[1]['scoringConfigId']
  label='<script>alert(1)</script>';safe=create('HTML text','class A {String label="'+label+'";}');safeScan=scan(safe,'class A {String label="'+label+'";}',False);assert safeScan['score']==100;assert label in json.dumps(call('/scans/'+str(safeScan['scanId'])+'/ast'))
  budget=create('AST budget');b=scan(budget,'class A {void m(){'+'work();'*1500+'}}',False);a=call('/scans/'+str(b['scanId'])+'/ast');assert a['nodesReturned']==500 and a['truncated']
  time.sleep(1.03);call(f'/projects/{id}/scans','POST',{'sourceCode':'x'*20001},400);call(f'/projects/{id}/scans','POST',{'sourceCode':'x'*270000},413)
  # Retain normal project for UI demo. Deletion only explicitly created dedicated project.
  d=create('DELETE ONLY');db=scan(d,BAD,False);dg=scan(d,GOOD);scan(d,'class SensitiveName {void broken(');stats=call('/users/me/stats');audit=sql(f'SELECT * FROM reward_logs WHERE project_id={d}')
  call('/projects/'+str(d),'DELETE',expected=204);call('/projects/'+str(d),'DELETE',expected=204)
  for path in (f'/projects/{d}/scans',f'/scans/{db["scanId"]}',f'/scans/{dg["scanId"]}/ast'):call(path,expected=404)
  call(f'/projects/{d}','PUT',{'name':'revive','sourceCode':BAD},404);call(f'/projects/{d}/scans','POST',{'sourceCode':BAD},404)
  assert call('/users/me/stats')==stats;assert sql(f'SELECT * FROM reward_logs WHERE project_id={d}')==audit
  evidence['deleteSql']=sql(f"SELECT p.id,p.source_code,p.deleted_at,(SELECT COUNT(*) FROM scan_runs WHERE project_id=p.id AND source_code<>'') residual_sources,(SELECT COUNT(*) FROM scan_issues WHERE project_id=p.id) residual_issues,(SELECT COUNT(*) FROM project_identity_state WHERE project_id=p.id) residual_states FROM projects p WHERE p.id={d}")
  assert all(evidence['deleteSql'][0][k]=='0' for k in ('residual_sources','residual_issues','residual_states'))
  snapshots={s['scanId']:call('/scans/'+str(s['scanId'])) for s in values};state=counts(id);stop();start();evidence['pidAfter']=P.pid;assert P.pid!=evidence['pidBefore'];assert counts(id)==state
  for sid,s in snapshots.items():assert call('/scans/'+str(sid))==s
  evidence['historyUnchangedAfterRestart']=len(snapshots);evidence['browserProjects']={'normal':id,'five':five,'html':safe};evidence['status']='PASS'
 finally:
  stop();(O/'http-mysql-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({k:v for k,v in evidence.items() if k!='records'},ensure_ascii=False))
if __name__=='__main__':main()
