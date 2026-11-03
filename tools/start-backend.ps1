param([int]$Port = 8080, [switch]$BundledDatabase)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = if (Test-Path -LiteralPath (Join-Path $root '.tools\jdk\jdk21.0.13_0\bin\java.exe')) {
    Join-Path $root '.tools\jdk\jdk21.0.13_0\bin\java.exe'
} elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$jar = Join-Path $root 'backend\target\sanctuary-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first: ./tools/mvn.ps1 -B verify' }
$env:SERVER_PORT = "$Port"
if ($BundledDatabase) {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/bug_monster?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = ''
}
& $java -jar $jar
if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error' }
