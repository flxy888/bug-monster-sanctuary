"""Real MySQL additive migration evidence; retains all schemas and rows. No mock database."""
from pathlib import Path
import subprocess, json, hashlib, xml.etree.ElementTree as ET, datetime

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'deliverables/week2'
MYSQL = ROOT / '.tools/mysql/mysql-8.4.7-winx64/bin/mysql.exe'

def sql(text):
    p = subprocess.run([str(MYSQL), '--no-defaults', '--host=127.0.0.1', '--port=3307', '--user=root',
                        '--default-character-set=utf8mb4', '--xml'], input=text, text=True, encoding='utf-8',
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
    if p.returncode: raise RuntimeError(p.stderr)
    return p.stdout

def rows(schema, table):
    doc = ET.fromstring(sql(f'SELECT * FROM `{schema}`.`{table}` ORDER BY 1,2;'))
    return [{f.attrib['name']: f.text for f in row} for row in doc.findall('row')]

def digest(value): return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True).encode()).hexdigest()

def initialize(schema):
    for name in ('01-schema.sql','04-week2-upgrade.sql','02-seed.sql'):
        sql((ROOT/'database'/name).read_text(encoding='utf-8').replace('bug_monster',schema))

def main():
    tables = ('users','projects','scan_runs','scan_issues','user_monsters','reward_logs')
    before = {t:rows('bug_monster',t) for t in tables}
    (OUT/'database-before.json').write_text(json.dumps(before,ensure_ascii=False,indent=2),encoding='utf-8')
    assert before['scan_runs'], 'Expected real week-one records for preservation check'
    initialize('bug_monster'); initialize('bug_monster')
    after = {t:rows('bug_monster',t) for t in tables}
    for t in tables:
        projected = [{k:r.get(k) for k in old} for old,r in zip(before[t],after[t])]
        assert len(before[t]) == len(after[t]) and projected == before[t], f'Historical rows changed: {t}'
    definitions = rows('bug_monster','monster_types')
    assert len(definitions)==5 and all(d['enabled']=='1' and d['suggestion'] for d in definitions)
    fresh = 'bug_monster_week2_empty_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    initialize(fresh); initialize(fresh)
    assert len(rows(fresh,'monster_types'))==5 and all(d['enabled']=='1' for d in rows(fresh,'monster_types'))
    assert not rows(fresh,'scan_runs') and not rows(fresh,'projects')
    report = {'realDatabase':True, 'version':sql('SELECT VERSION();'), 'freshSchemaRetained':fresh,
              'initializationsPerSchema':2, 'preserved':{t:{'rows':len(before[t]),'sha256':digest(before[t])} for t in tables},
              'legacyScans':len([s for s in before['scan_runs'] if s['ruleset_version']=='week1-v1']),
              'newColumnsOnOldRows':{'metadata_snapshot':None}, 'definitions':definitions}
    (OUT/'migration-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'status':'PASS','legacyScans':report['legacyScans'],'freshSchema':fresh,'preservedRows':{t:len(before[t]) for t in tables}},ensure_ascii=False))

if __name__=='__main__':main()
