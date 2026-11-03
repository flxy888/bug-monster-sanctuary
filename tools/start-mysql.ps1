# Local disposable/demo DB only. Files remain under the project directory.
param([int]$Port = 3307, [ValidatePattern('^[A-Z]$')] [string]$DriveLetter = 'R')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$drivePath = $DriveLetter + ':\'
$aliasName = $DriveLetter + ':'
if (-not ('SanctuaryDriveAlias' -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class SanctuaryDriveAlias {
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    public static extern uint QueryDosDevice(string name, StringBuilder target, int length);
}
"@
}
$mappingBuffer = [Text.StringBuilder]::new(4096)
$mappingLength = [SanctuaryDriveAlias]::QueryDosDevice($aliasName, $mappingBuffer, $mappingBuffer.Capacity)
$existingAlias = if ($mappingLength -gt 0) { $mappingBuffer.ToString() } else { '' }
if (Test-Path -LiteralPath $drivePath) {
    if (-not [string]::Equals($existingAlias, ('\??\' + $root), [StringComparison]::OrdinalIgnoreCase)) {
        throw "$aliasName already points elsewhere; choose another -DriveLetter. No existing drive was changed."
    }
} else {
    & subst $aliasName $root
    if ($LASTEXITCODE -ne 0) { throw 'Could not create an ASCII project path alias' }
}
$mysqlHome = $drivePath + '.tools\mysql\mysql-8.4.7-winx64'
$dataPath = $drivePath + '.tools\mysql-data'
$server = Join-Path $mysqlHome 'bin\mysqld.exe'
if (-not (Test-Path -LiteralPath $server)) { throw 'Run ./tools/bootstrap-tools.ps1 -Component mysql first' }
if (-not (Test-Path -LiteralPath $dataPath)) {
    & $server --no-defaults --initialize-insecure "--basedir=$mysqlHome" "--datadir=$dataPath" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; inspect diagnostics before retrying' }
}
& $server --no-defaults "--basedir=$mysqlHome" "--datadir=$dataPath" "--port=$Port" --bind-address=127.0.0.1 --mysqlx=0 --console
if ($LASTEXITCODE -ne 0) { throw 'MySQL exited with an error' }
# After stopping MySQL, remove the temporary alias manually: subst R: /D
