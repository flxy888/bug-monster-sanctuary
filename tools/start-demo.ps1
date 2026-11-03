param([switch]$NoBrowser)
$ErrorActionPreference='Stop'
# Select the child PowerShell edition's own modules; no persistent environment change.
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$root=Split-Path $PSScriptRoot -Parent
$base=Join-Path $root 'deliverables\demo-ready'
$runId=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$run=Join-Path $base ('runs\'+$runId)
New-Item -ItemType Directory -Force $run | Out-Null
$log=Join-Path $run 'launcher.log'
$lock=$null
function Note([string]$Text) { $line='['+(Get-Date -Format 'HH:mm:ss')+'] '+$Text; Add-Content -LiteralPath $log -Value $line -Encoding UTF8; Write-Host $line }
function Listener([int]$Port) {
 $items=@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
 $owners=@($items | Select-Object -ExpandProperty OwningProcess -Unique)
 if($owners.Count -gt 1) {throw "端口 $Port 有多个进程监听，拒绝操作。"}
 if($owners.Count -eq 0) {return $null}
 if(@($items | Where-Object { $_.LocalAddress -notin @('127.0.0.1','::1') }).Count -gt 0) {throw "端口 $Port 不是仅本机监听，拒绝复用。"}
 return Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])" -ErrorAction Stop
}
function Canonical([string]$Path) {
 $v=$Path.Replace('/','\')
 if($v.StartsWith('R:\',[StringComparison]::OrdinalIgnoreCase)) {
  if(-not $script:aliasValid) {throw 'R:未核对为本项目，拒绝复用该路径。'}
  $v=$root+$v.Substring(2)
 }
 return [IO.Path]::GetFullPath($v).TrimEnd('\')
}
function CheckAlias {
 if(-not ('DemoAliasQuery' -as [type])) {
  Add-Type -TypeDefinition @"
using System; using System.Text; using System.Runtime.InteropServices;
public static class DemoAliasQuery {
 [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
 public static extern uint QueryDosDevice(string name,StringBuilder target,int length);
}
"@
 }
 $buffer=New-Object Text.StringBuilder 4096
 $length=[DemoAliasQuery]::QueryDosDevice('R:',$buffer,$buffer.Capacity)
 $script:aliasValid=($length -gt 0 -and [string]::Equals($buffer.ToString(),('\??\'+$root),[StringComparison]::OrdinalIgnoreCase))
 if($length -gt 0 -and -not $script:aliasValid) {throw 'R:已属于其他位置；不会覆盖盘符。请查看日志并手动协调。'}
}
function Hidden([string]$Exe,[string[]]$Arguments,[string]$Name,[string]$Working=$root) {
 return Start-Process -FilePath $Exe -ArgumentList $Arguments -WorkingDirectory $Working -WindowStyle Hidden -RedirectStandardOutput (Join-Path $run ($Name+'.stdout.log')) -RedirectStandardError (Join-Path $run ($Name+'.stderr.log')) -PassThru
}
function WaitReady([scriptblock]$Check,[int]$Seconds,[string]$Description,$Child=$null) {
 $until=(Get-Date).AddSeconds($Seconds);$last='尚未就绪'
 do {
  try {if(& $Check) {return}} catch {$last=$_.Exception.Message}
  if($Child -and $Child.HasExited -and $Child.ExitCode -ne 0) {throw "$Description 启动器退出（$($Child.ExitCode)）；查看 $run"}
  Start-Sleep -Milliseconds 300
 } while((Get-Date) -lt $until)
 throw "$Description 等待超时：$last；查看 $run"
}
function Health([int]$Port) {
 $h=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/health" -TimeoutSec 3
 if($h.status -ne 'UP' -or $h.database -ne 'UP' -or $h.matchingPolicyVersion -ne 'week3-match-v2' -or $h.rulesetVersion -ne 'week2-v1' -or $h.buildId -ne $script:buildId) {throw "端口 $Port 运行版本/数据库不符合本项目第四周产物。"}
 return $h
}
function MysqlOwned($Proc) {
 if($Proc.Name -ne 'mysqld.exe' -or (Canonical $Proc.ExecutablePath) -ne (Join-Path $root '.tools\mysql\mysql-8.4.7-winx64\bin\mysqld.exe')) {throw '3307进程并非本项目MySQL，拒绝复用。'}
 $cmd=$Proc.CommandLine.Replace('/','\').ToLowerInvariant()
 if(-not ($cmd.Contains((Join-Path $root '.tools\mysql-data').ToLowerInvariant()) -or ($script:aliasValid -and $cmd.Contains('r:\.tools\mysql-data')))) {throw '3307数据目录归属不符，拒绝复用。'}
}
function BackendOwned($Proc,[switch]$RequireReady) {
 if($Proc.Name -ne 'java.exe') {throw '8080被其他进程占用。'}
 $tokens=@([regex]::Matches($Proc.CommandLine,'"[^"]+"|\S+') | ForEach-Object {$_.Value.Trim('"')})
 $jarIndex=[array]::IndexOf($tokens,'-jar')
 if($jarIndex -lt 0 -or $jarIndex+1 -ge $tokens.Count -or (Canonical $tokens[$jarIndex+1]) -ne $script:jar) {throw '8080并非target-week4 JAR，拒绝复用。'}
 $receipts=@(Get-ChildItem -LiteralPath $base -Filter 'runtime-8080.json' -Recurse -File -ErrorAction SilentlyContinue)
 $old=Join-Path $root 'deliverables\week4\runtime-8080.json'
 if(Test-Path -LiteralPath $old) {$receipts+=Get-Item -LiteralPath $old}
 $valid=$false
 foreach($f in $receipts) {
  try {
   $r=Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json
   $launch=[DateTimeOffset]::FromUnixTimeMilliseconds([long]($r.launchedAt*1000)).UtcDateTime
   if($r.backendPid -eq $Proc.ProcessId -and $r.schema -eq 'bug_monster' -and $r.jarSha256 -eq $script:jarHash -and [Math]::Abs(($Proc.CreationDate.ToUniversalTime()-$launch).TotalSeconds) -lt 40) {$valid=$true;break}
  } catch {continue}
 }
 if(-not $valid) {throw '8080缺少匹配PID/启动时间/原库/JAR哈希的启动收据，拒绝冒认。'}
 if($RequireReady){$null=Health 8080}
}
function FrontendOwned($Proc,[switch]$RequireReady) {
 if($Proc.Name -ne 'node.exe' -or $Proc.CommandLine -notmatch '(?:^|\s)preview(?:\s|$)' -or $Proc.CommandLine -notmatch '--port[ =]+5173(?:\s|$)') {throw '5173不是本项目preview进程，拒绝复用。'}
 $tokens=@([regex]::Matches($Proc.CommandLine,'"[^"]+"|\S+') | ForEach-Object {$_.Value.Trim('"')})
 $matches=@($tokens | Where-Object {$_ -match 'vite\.js$'} | Where-Object {(Canonical $_) -eq (Join-Path $root 'frontend\node_modules\vite\bin\vite.js')})
 if($matches.Count -ne 1) {throw '5173前端目录不符，拒绝复用。'}
 $html=(Invoke-WebRequest 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 3).Content
 $disk=Get-Content -LiteralPath (Join-Path $root 'frontend\dist\index.html') -Raw
 $entry=[regex]::Match($disk,'/assets/index-[^" ]+\.js').Value
 if(-not $entry -or -not $html.Contains($entry)) {throw '5173提供的不是当前dist入口。'}
 if($RequireReady){$null=Health 5173}
}
try {
 try {$lock=[IO.File]::Open((Join-Path $base 'start-demo.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)} catch {throw '已有启动操作正在进行，请稍后再双击；未启动第二套服务。'}
 Note '检查本项目现有数据与已构建产物，不安装、不构建、不初始化数据库。'
 foreach($p in @('.tools\mysql-data\auto.cnf','.tools\mysql-data\ibdata1','.tools\mysql-data\bug_monster','backend\target-week4\sanctuary-0.1.0.jar','frontend\dist\index.html','frontend\node_modules\vite\bin\vite.js')) {
  if(-not (Test-Path -LiteralPath (Join-Path $root $p))) {throw "缺少原数据/产物：$p。停止；不会新建空库或安装依赖。"}
 }
 $script:jar=Join-Path $root 'backend\target-week4\sanctuary-0.1.0.jar'
 $stream=[IO.File]::OpenRead($script:jar);$hash=[Security.Cryptography.SHA256]::Create()
 try {$script:jarHash=([BitConverter]::ToString($hash.ComputeHash($stream))).Replace('-','').ToLowerInvariant()} finally {$stream.Dispose();$hash.Dispose()}
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $zip=[IO.Compression.ZipFile]::OpenRead($script:jar)
 try {$reader=New-Object IO.StreamReader ($zip.GetEntry('BOOT-INF/classes/build-info.properties').Open());try {$props=$reader.ReadToEnd()} finally {$reader.Dispose()}} finally {$zip.Dispose()}
 $script:buildId=[regex]::Match($props,'(?m)^build\.id=(.+)$').Groups[1].Value.Trim()
 if(-not $script:buildId.StartsWith('local-build-')) {throw 'JAR构建标识不正确。'}
 CheckAlias
 # Refuse all unknown occupied app ports before starting anything else.
 $backend=Listener 8080;$front=Listener 5173;$db=Listener 3307
 if($db) {MysqlOwned $db}
 if($backend) {BackendOwned $backend}
 if($front) {FrontendOwned $front}
 $started=@()
 if(-not $db) {
  Note '隐藏启动原数据目录MySQL（3307）。'
  $child=Hidden 'powershell.exe' @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $root 'tools\start-mysql.ps1')+'"')) 'mysql'
  WaitReady { $p=Listener 3307;if($p){CheckAlias;MysqlOwned $p;return $true};return $false } 60 'MySQL3307' $child
  $started+='mysql'
 } else {Note "复用本项目MySQL PID $($db.ProcessId)。"}
 $mysql=Join-Path $root '.tools\mysql\mysql-8.4.7-winx64\bin\mysql.exe'
 WaitReady { & $mysql --no-defaults --host=127.0.0.1 --port=3307 --user=root --database=bug_monster --batch --skip-column-names '--execute=SELECT 1;' 1>> (Join-Path $run 'mysql-ready.log') 2>> (Join-Path $run 'mysql-ready.stderr.log');return ($LASTEXITCODE -eq 0) } 30 '原bug_monster库连接'
 if(-not $backend) {
  Note '隐藏启动target-week4 v2后端及缺失的前端。'
  $args=@(('"'+(Join-Path $root 'tools\start-week4.py')+'"'),'--output-dir',('deliverables/demo-ready/runs/'+$runId))
  if($front) {$args+='--backend-only'}
  $child=Hidden 'python.exe' $args 'application'
  WaitReady { $p=Listener 8080;if(-not $p){return $false};$receipt=Join-Path $run 'runtime-8080.json';if(-not (Test-Path -LiteralPath $receipt)){return $false};BackendOwned $p -RequireReady;return $true } 90 'v2后端8080' $child
  $started+='backend';if(-not $front){$started+='frontend'}
 } else {Note "复用v2后端 PID $($backend.ProcessId)。"}
 # start-week4 may have spawned Node before it begins listening; wait for that child instead of spawning twice.
 if((-not (Listener 5173)) -and ('frontend' -notin $started)) {
  Note '隐藏启动缺失的当前dist前端（5173）。'
  $previousProxy=$env:API_PROXY_TARGET
  $previousAiKey=$env:DEEPSEEK_API_KEY
  try {$env:DEEPSEEK_API_KEY=$null;$env:API_PROXY_TARGET='http://127.0.0.1:8080';$child=Hidden 'node.exe' @(('"'+(Join-Path $root 'frontend\node_modules\vite\bin\vite.js')+'"'),'preview','--host','127.0.0.1','--port','5173','--strictPort') 'frontend' (Join-Path $root 'frontend')} finally {$env:API_PROXY_TARGET=$previousProxy;$env:DEEPSEEK_API_KEY=$previousAiKey}
  if('frontend' -notin $started){$started+='frontend'}
 }
 WaitReady { $p=Listener 5173;if(-not $p){return $false};FrontendOwned $p -RequireReady;return $true } 30 '前端5173及API代理'
 $db=Listener 3307;$backend=Listener 8080;$front=Listener 5173
 MysqlOwned $db;BackendOwned $backend -RequireReady;FrontendOwned $front -RequireReady
 $state=[ordered]@{runId=$runId;time=(Get-Date).ToString('o');status='READY';url='http://127.0.0.1:5173/';started=$started;mysqlPid=$db.ProcessId;backendPid=$backend.ProcessId;frontendPid=$front.ProcessId;health=(Health 8080);jarSha256=$script:jarHash;logDirectory=$run;browserOpened=(-not $NoBrowser)}
 $state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $run 'ready.json') -Encoding UTF8
 $state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $base 'current-runtime.json') -Encoding UTF8
 Note '全部真实就绪：http://127.0.0.1:5173/ （DB UP / week3-match-v2 / week2-v1）。'
 if(-not $NoBrowser) {Start-Process 'http://127.0.0.1:5173/'}
 exit 0
} catch {
 Note ('启动未完成：'+$_.Exception.Message)
 Write-Host ('日志目录：'+$run) -ForegroundColor Yellow
 $_ | Out-String | Add-Content -LiteralPath (Join-Path $run 'error.log') -Encoding UTF8
 exit 1
} finally {if($lock){$lock.Dispose()}}

