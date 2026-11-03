"""Local-only launcher: never initializes or clears a database. Emits an ownership receipt."""
from pathlib import Path
import argparse,json,subprocess,os,socket,time,hashlib
from urllib.request import build_opener,ProxyHandler
R=Path(__file__).resolve().parents[1]
def main():
 a=argparse.ArgumentParser();a.add_argument('--output-dir',default='deliverables/week4');a.add_argument('--schema',default='bug_monster');a.add_argument('--backend-port',type=int,default=8080);a.add_argument('--frontend-port',type=int,default=5173);a.add_argument('--backend-only',action='store_true');a.add_argument('--interval',type=int,default=1000);x=a.parse_args()
 import re
 assert re.fullmatch(r'bug_monster(?:_[a-zA-Z0-9_]+)?',x.schema);assert x.interval>0
 for port in ([x.backend_port] if x.backend_only else [x.backend_port,x.frontend_port]):
  with socket.socket() as s:s.bind(('127.0.0.1',port))
 out=(R/x.output_dir).resolve();assert out.is_relative_to(R/'deliverables');out.mkdir(parents=True,exist_ok=True);jar=R/'backend/target-week4/sanctuary-0.1.0.jar';assert jar.is_file()
 env=os.environ.copy();env.update(DB_URL=f'jdbc:mysql://127.0.0.1:3307/{x.schema}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USERNAME='root',DB_PASSWORD='',SERVER_PORT=str(x.backend_port),SCAN_MIN_INTERVAL_MS=str(x.interval),API_PROXY_TARGET=f'http://127.0.0.1:{x.backend_port}')
 backend_env=env.copy()
 if not backend_env.get('DEEPSEEK_API_KEY','').strip():
  private=R/'.tools/private/deepseek-api-key.txt'
  if private.is_file():backend_env['DEEPSEEK_API_KEY']=private.read_text(encoding='utf-8-sig').strip()
 cmd=[str(R/'.tools/jdk/jdk21.0.13_0/bin/java.exe'),'-jar',str(jar)];p=subprocess.Popen(cmd,cwd=R,env=backend_env,stdout=(out/f'backend-{x.backend_port}.log').open('ab'),stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 receipt={'schema':x.schema,'jar':str(jar),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'backendPort':x.backend_port,'backendPid':p.pid,'backendCommand':cmd,'launchedAt':time.time(),'interval':x.interval}
 try:
  deadline=time.monotonic()+60
  while time.monotonic()<deadline:
   if p.poll() is not None:raise RuntimeError('Backend exited')
   try:
    receipt['health']=json.load(build_opener(ProxyHandler({})).open(f'http://127.0.0.1:{x.backend_port}/api/health',timeout=2));break
   except OSError:time.sleep(.2)
  else:raise RuntimeError('Backend startup timeout')
  assert receipt['health']['matchingPolicyVersion']=='week3-match-v2'
  if not x.backend_only:
   env.pop('DEEPSEEK_API_KEY',None)  # Never pass the backend secret to Vite.
   cmd=['node',str(R/'frontend/node_modules/vite/bin/vite.js'),'preview','--host','127.0.0.1','--port',str(x.frontend_port),'--strictPort'];front=subprocess.Popen(cmd,cwd=R/'frontend',env=env,stdout=(out/f'frontend-{x.frontend_port}.log').open('ab'),stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   receipt.update(frontendPort=x.frontend_port,frontendPid=front.pid,frontendCommand=cmd)
  (out/f'runtime-{x.backend_port}.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False))
 except Exception:p.terminate();p.wait(20);raise
if __name__=='__main__':main()
