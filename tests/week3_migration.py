"""Verify upgrade on a retained copy of the pre-week3 backup and a fresh schema. Never clear the live DB."""
from pathlib import Path
import subprocess, json, hashlib, xml.etree.ElementTree as ET, datetime, re

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'deliverables/week3'
MYSQL=ROOT/'.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe'
def sql(text):
    p=subprocess.run([str(MYSQL),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','--default-character-set=utf8mb4','--xml'],input=text,text=True,encoding='utf-8',stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    if p.returncode:raise RuntimeError(p.stderr)
    return p.stdout
def rows(schema,table):
    return [{f.attrib['name']:f.text for f in row} for row in ET.fromstring(sql(f'SELECT * FROM `{schema}`.`{table}` ORDER BY 1,2')).findall('row')]
def digest(x):return hashlib.sha256(json.dumps(x,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
def initialize(schema):
    for name in ('01-schema.sql','04-week2-upgrade.sql','02-seed.sql','05-week3-upgrade.sql'):
        sql((ROOT/'database'/name).read_text(encoding='utf-8').replace('bug_monster',schema))
def main():
    stamp=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    upgraded='bug_monster_week3_upgrade_'+stamp
    dump=(OUT/'pre-upgrade.sql').read_text(encoding='utf-8')
    # Restore into a new named verification schema; remove dump's unnecessary DROP TABLE statements.
    assert not ET.fromstring(sql(f"SELECT schema_name FROM information_schema.schemata WHERE schema_name='{upgraded}'")).findall('row')
    dump=re.sub(r'^CREATE DATABASE .*$',f'CREATE DATABASE `{upgraded}`;',dump,flags=re.M)
    dump=re.sub(r'^USE `bug_monster`;$',f'USE `{upgraded}`;',dump,flags=re.M)
    dump=re.sub(r'^DROP TABLE IF EXISTS .*;\s*$', '',dump,flags=re.M)
    assert 'USE `bug_monster`;' not in dump and 'DROP DATABASE' not in dump
    sql(dump)
    tables=('users','projects','scan_runs','scan_issues','user_monsters','reward_logs')
    before={t:rows(upgraded,t) for t in tables}
    initialize(upgraded);initialize(upgraded)
    after={t:rows(upgraded,t) for t in tables}
    for t in tables:
        projected=[{k:r.get(k) for k in old} for old,r in zip(before[t],after[t])]
        assert len(before[t])==len(after[t]) and projected==before[t],t
    assert all(r['containment_snapshot'] is None for r in after['scan_runs'])
    assert all(r['lineage_key'] is None and r['candidate_lineage_keys'] is None for r in after['scan_issues'])
    live={t:{r['id']:r for r in rows('bug_monster',t)} for t in ('scan_runs','scan_issues')}
    for t in live:
        for old in before[t]: assert {k:live[t][old['id']][k] for k in old}==old,(t,old['id'])
    fresh='bug_monster_week3_empty_'+stamp;initialize(fresh);initialize(fresh)
    assert len(rows(fresh,'monster_types'))==5 and not rows(fresh,'scan_runs') and not rows(fresh,'issue_lineages')
    report={'status':'PASS','realMySql':True,'upgradedSchemaRetained':upgraded,'freshSchemaRetained':fresh,'initializationsPerSchema':2,'preserved':{t:{'rows':len(v),'sha256':digest(v)} for t,v in before.items()},'liveHistoricalScansUnchanged':len(before['scan_runs']),'liveHistoricalIssuesUnchanged':len(before['scan_issues']),'oldSettlementAndLineageFieldsRemainNull':True}
    (OUT/'migration-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
