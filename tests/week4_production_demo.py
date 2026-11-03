from pathlib import Path
from urllib.request import build_opener,ProxyHandler,Request
import json
R=Path.cwd();O=R/'deliverables/week4';op=build_opener(ProxyHandler({}))
def call(path,method='GET',body=None):
 with op.open(Request('http://127.0.0.1:8080/api'+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})) as r:return json.load(r)
before=call('/users/me/stats');code=(R/'tests/samples/WeekTwoDemo.java').read_text(encoding='utf-8');project=call('/projects','POST',{'name':'W4 最终展示 · 五规则实验','sourceCode':code});scan=call(f'/projects/{project["id"]}/scans','POST',{'sourceCode':code});assert scan['score']==49 and scan['xpEarned']==0;after=call('/users/me/stats');assert after['totalXp']==before['totalXp'];(O/'production-demo.json').write_text(json.dumps({'project':project,'scan':scan,'statsBefore':before,'statsAfter':after,'health':call('/health')},ensure_ascii=False,indent=2),encoding='utf-8');print(project['id'],scan['scanId'])
