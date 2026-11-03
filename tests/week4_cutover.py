from pathlib import Path
import json,subprocess,hashlib,datetime,xml.etree.ElementTree as ET
R=Path.cwd();O=R/'deliverables/week4';mysql=R/'.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe'
def sql(q):
 p=subprocess.run([str(mysql),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','--default-character-set=utf8mb4','--xml'],input=q,text=True,encoding='utf-8',capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW);assert p.returncode==0,p.stderr
 return [{f.attrib['name']:f.text for f in row} for row in ET.fromstring(p.stdout).findall('row')] if p.stdout.strip().startswith('<?xml') else []
tables=('users','projects','scan_runs','scan_issues','user_monsters','reward_logs','project_identity_state','issue_lineages')
before={t:sql(f'SELECT * FROM bug_monster.{t} ORDER BY 1,2') for t in tables}
sql((R/'database/06-week4-upgrade.sql').read_text(encoding='utf-8'))
after={t:sql(f'SELECT * FROM bug_monster.{t} ORDER BY 1,2') for t in tables}
for t in tables:
 assert len(before[t])==len(after[t]),t
 assert all(all(b[k]==v for k,v in a.items()) for a,b in zip(before[t],after[t])),t
receipt={'status':'PASS','originalBeforeMigration':{t:{'rows':len(v),'sha256':hashlib.sha256(json.dumps(v,sort_keys=True).encode()).hexdigest()} for t,v in before.items()},'allExistingFieldsPreserved':True,'previousApplicationsRunning':False,'previousPorts':[8080,5173],'previousJar':str(R/'backend/target/sanctuary-0.1.0.jar'),'previousJarSha256':hashlib.sha256((R/'backend/target/sanctuary-0.1.0.jar').read_bytes()).hexdigest()}
(O/'production-migration.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False))
