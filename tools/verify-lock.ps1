$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$current = Join-Path $root '.tools\dependencies.current.lock'
& (Join-Path $PSScriptRoot 'mvn.ps1') -B dependency:list '-Dsort=true' "-DoutputFile=$current"
$expected = Get-Content -LiteralPath (Join-Path $root 'backend\dependencies.lock')
$actual = Get-Content -LiteralPath $current
$changes = Compare-Object $expected $actual
if ($changes) { $changes | Format-Table; throw 'Resolved Maven dependencies differ from backend/dependencies.lock' }
Write-Output 'Maven dependency lock verified'
