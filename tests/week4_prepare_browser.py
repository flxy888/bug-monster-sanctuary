from pathlib import Path
from urllib.request import build_opener,ProxyHandler,Request
import json,time
R=Path.cwd();out=R/'deliverables/week4';op=build_opener(ProxyHandler({}));events=[]
def api(path,method='GET',body=None):
 with op.open(Request('http://127.0.0.1:18087/api'+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})) as r:v=json.load(r)
 events.append({'path':path,'body':body,'response':v});return v
def m(n,fixed=False):return 'void m('+','.join('int a'+str(i) for i in range(n))+'){try{work();}catch(Exception e){'+('handle(e);' if fixed else '')+'}}'
code=lambda *m:'class A {'+'\n'.join(m)+'}'
p=api('/projects','POST',{'name':'W4 浏览器争用冻结专用','sourceCode':code(m(5,True),m(6))})['id']
for s in [code(m(6)),code(m(5)),code(m(5,True),m(6))]:
 time.sleep(1.03);r=api(f'/projects/{p}/scans','POST',{'sourceCode':s})
assert r['score']==77 and r['xpEarned']==0 and r['ambiguousCount']>0
(out/'browser-conflict-preparation.json').write_text(json.dumps(events,ensure_ascii=False,indent=2),encoding='utf-8');print(p,r['scanId'])
