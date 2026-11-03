from pathlib import Path
from urllib.request import build_opener,ProxyHandler,Request
import json,time,sys
R=Path.cwd();O=R/'deliverables/week4';op=build_opener(ProxyHandler({}));records=[]
def call(path,method='GET',body=None):
 with op.open(Request('http://127.0.0.1:18088/api'+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})) as r:v=json.load(r)
 records.append({'path':path,'body':body,'response':v});return v
if '--after-restart' in sys.argv:
 old=json.loads((O/'rehearsal-evidence.json').read_text(encoding='utf-8'));assert call('/scans/'+str(old['baseline']['scanId']))==old['baseline'];assert call('/users/me/stats')['totalXp']==20;old['restartHistoryUnchanged']=True;old['restartHealth']=call('/health');old['status']='PASS';(O/'rehearsal-evidence.json').write_text(json.dumps(old,ensure_ascii=False,indent=2),encoding='utf-8')
else:
 initial=call('/users/me/stats');assert initial['totalXp']==0 and initial['level']==1
 p=call('/projects','POST',{'name':'W4 干净新库启动演练','sourceCode':'class A {void m(){try{work();}catch(Exception e){}}}'})['id'];runs=[]
 for code in ['class A {void m(){try{work();}catch(Exception e){}}}','class A {void m(){try{work();}catch(Exception e){handle(e);}}}','class A {void m(){try{work();}catch(Exception e){handle(e);}}}']:
  time.sleep(1.03);runs.append(call(f'/projects/{p}/scans','POST',{'sourceCode':code}))
 assert [r['xpEarned'] for r in runs]==[0,20,0];assert [r['score'] for r in runs]==[85,100,100]
 evidence={'schema':'bug_monster_w4_rehearsal_20261027','initial':initial,'baseline':runs[0],'records':records,'status':'RESTART_PENDING'};(O/'rehearsal-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS')
