"""Real HTTP repair, backend restart and MySQL restart. Keeps original data and test projects."""
from pathlib import Path
from urllib.request import Request,build_opener,ProxyHandler
from urllib.error import URLError
import subprocess,os,json,time,ctypes
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'deliverables/week3'
BASE='http://127.0.0.1:8083/api'
opener=build_opener(ProxyHandler({}))
def call(path,method='GET',body=None,expected=200):
    req=Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})
    with opener.open(req,timeout=20) as r:
        assert r.status==expected
        return json.loads(r.read())
def start(log):
    env=os.environ.copy();env.update(DB_URL='jdbc:mysql://127.0.0.1:3307/bug_monster?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USERNAME='root',DB_PASSWORD='',SERVER_PORT='8083')
    p=subprocess.Popen([str(ROOT/'.tools/jdk/jdk21.0.13_0/bin/java.exe'),'-jar',str(ROOT/'backend/target/sanctuary-0.1.0.jar')],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(120):
        if p.poll() is not None:raise RuntimeError('Backend exited')
        try:
            if call('/health')['status']=='UP':return p
        except (URLError,OSError):time.sleep(.25)
    p.terminate();p.wait(15);raise RuntimeError('Backend start timeout')
def mysql_pid():
    path=ROOT/'.tools/mysql-data'
    return {p.name:p.read_text().strip() for p in path.glob('*.pid')}
def main():
    process=None;db=None;evidence={'status':'RUNNING'}
    with (OUT/'e2e-backend.log').open('ab') as log,(OUT/'mysql-restart.log').open('ab') as dblog:
        try:
            process=start(log);evidence['backendPidBefore']=process.pid
            initial=call('/users/me/stats');evidence['growthBefore']=initial
            saved='class SavedDraft {}'
            p=call('/projects','POST',{'name':'第三周真实修复HTTP演示','sourceCode':saved},201)
            def scan(src):return call(f"/projects/{p['id']}/scans",'POST',{'sourceCode':src},201)
            bad=(ROOT/'tests/samples/week3/EmptyCatch-before.java').read_text()
            good=(ROOT/'tests/samples/week3/EmptyCatch-after.java').read_text()
            b=scan(bad);f=scan('class Broken { void m(');r=scan(good);repeat=scan(good);relapse=scan(bad);again=scan(good)
            assert (b['score'],b['xpEarned'],b['comparisonStatus'])==(85,0,'BASELINE_CREATED')
            assert f['status']=='PARSE_ERROR' and f['baselineScanId']==b['scanId']
            assert (r['score'],r['containedCount'],r['xpEarned'])==(100,1,20)
            assert repeat['xpEarned']==0 and again['xpEarned']==0 and again['resolvedCount']==1
            assert again['resolutions'][0]['reason']=='ALREADY_REWARDED'
            assert relapse['issues'][0]['lineageKey']==b['issues'][0]['lineageKey']
            assert call('/users/me/stats')['totalXp']==initial['totalXp']+20
            five=[]
            for name in ('EmptyCatch','EmptyIf','Parameters','LongMethod','DeepNesting'):
                q=call('/projects','POST',{'name':'第三周独立样例 '+name},201)
                pair=[]
                for kind in ('before','after'):
                    src=(ROOT/f'tests/samples/week3/{name}-{kind}.java').read_text()
                    pair.append(call(f"/projects/{q['id']}/scans",'POST',{'sourceCode':src},201))
                assert len(pair[0]['issues'])==1 and pair[0]['xpEarned']==0 and pair[1]['xpEarned']==20
                five.extend(pair)
            expected=[call('/scans/'+str(s['scanId'])) for s in [b,f,r,repeat,relapse,again]+five]
            legacy=call('/scans/1');assert legacy['comparisonStatus']=='NOT_RECORDED' and legacy['xpEarned'] is None
            growth=call('/users/me/stats');monsters=call('/users/me/monsters')
            assert growth['totalXp']==initial['totalXp']+120
            assert growth['level']==1+growth['totalXp']//100 and growth['levelXp']==growth['totalXp']%100
            for snapshot in expected:call('/scans/'+str(snapshot['scanId']))
            assert call('/users/me/stats')==growth
            process.terminate();process.wait(20);process=None
            evidence['mysqlPidBefore']=mysql_pid()
            old_pid=int(next(iter(evidence['mysqlPidBefore'].values())))
            kernel=ctypes.WinDLL('kernel32',use_last_error=True)
            kernel.OpenProcess.restype=ctypes.c_void_p
            handle=kernel.OpenProcess(0x00100000,False,old_pid)
            if not handle:raise RuntimeError('Cannot wait for owned MySQL process exit')
            admin=ROOT/'.tools/mysql/mysql-8.4.7-winx64/bin/mysqladmin.exe'
            subprocess.run([str(admin),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','shutdown'],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
            kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint]
            kernel.CloseHandle.argtypes=[ctypes.c_void_p]
            wait=kernel.WaitForSingleObject(handle,20000);kernel.CloseHandle(handle)
            if wait!=0:raise RuntimeError('Original MySQL has not fully exited; do not start another process against its data')
            server=Path('R:/.tools/mysql/mysql-8.4.7-winx64/bin/mysqld.exe')
            db=subprocess.Popen([str(server),'--no-defaults','--basedir=R:/.tools/mysql/mysql-8.4.7-winx64','--datadir=R:/.tools/mysql-data','--port=3307','--bind-address=127.0.0.1','--mysqlx=0','--console'],stdout=dblog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            for _ in range(100):
                if db.poll() is not None:raise RuntimeError('MySQL exited during restart; inspect mysql-restart.log')
                ping=subprocess.run([str(admin),'--no-defaults','--host=127.0.0.1','--port=3307','--user=root','ping'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
                if ping.returncode==0:break
                time.sleep(.2)
            else:raise RuntimeError('MySQL restart failed')
            evidence['mysqlPidAfter']=mysql_pid();assert evidence['mysqlPidAfter']!=evidence['mysqlPidBefore']
            process=start(log);evidence['backendPidAfter']=process.pid
            for snapshot in expected+[legacy]:assert call('/scans/'+str(snapshot['scanId']))==snapshot
            assert call('/users/me/stats')==growth and call('/users/me/monsters')==monsters
            # A further recurrence after both process restarts still cannot earn another reward.
            scan(bad);assert scan(good)['xpEarned']==0
            current=next(x for x in call('/projects') if x['id']==p['id']);assert current['sourceCode']==saved
            evidence.update(status='PASS',projectId=p['id'],realMySql=True,baseline=b,repair=r,repeat=repeat,recurrenceRepair=again,fiveRulePairs=five,growthAfter=growth,monsters=monsters,historySnapshotsUnchanged=len(expected)+1,draftPreserved=True,rewardStillDeduplicatedAfterRestart=True)
            (OUT/'e2e-http-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
            print(json.dumps({k:evidence[k] for k in ('status','projectId','backendPidBefore','backendPidAfter','mysqlPidBefore','mysqlPidAfter','historySnapshotsUnchanged')},ensure_ascii=False))
        finally:
            if process and process.poll() is None:process.terminate();process.wait(20)
            # Keep the restored local MySQL running for the developer/user; never remove its data.
if __name__=='__main__':main()
