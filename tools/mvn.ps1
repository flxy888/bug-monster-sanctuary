# Project-local Maven runner. Arguments are passed as an array, never evaluated as shell code.
param([Parameter(ValueFromRemainingArguments=$true)] [string[]]$MavenArguments)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$localJdk = Join-Path $root '.tools\jdk\jdk21.0.13_0'
if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21, or run tools/bootstrap-tools.ps1 -Component jdk' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$maven = Join-Path $root '.tools\maven\apache-maven-3.9.9\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
}
Push-Location (Join-Path $root 'backend')
try {
    & $maven "-Dmaven.repo.local=$root/.tools/m2" @MavenArguments
    $mavenExit = $LASTEXITCODE
} finally { Pop-Location }
if ($mavenExit -ne 0) { throw "Maven failed: exit $mavenExit" }
