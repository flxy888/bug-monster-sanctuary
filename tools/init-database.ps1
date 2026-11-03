param(
    [string]$DatabaseHost = '127.0.0.1',
    [ValidatePattern('^bug_monster(?:_[a-zA-Z0-9_]+)?$')] [string]$DatabaseName = 'bug_monster',
    [int]$Port = 3307,
    [string]$User = 'root',
    [string]$MysqlExecutable = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $MysqlExecutable) {
    $MysqlExecutable = Join-Path $root '.tools\mysql\mysql-8.4.7-winx64\bin\mysql.exe'
}
foreach ($file in @('01-schema.sql','04-week2-upgrade.sql','02-seed.sql','05-week3-upgrade.sql','06-week4-upgrade.sql')) {
    (Get-Content -LiteralPath (Join-Path $root "database\$file") -Raw).Replace('bug_monster',$DatabaseName) |
        & $MysqlExecutable --no-defaults "--host=$DatabaseHost" "--port=$Port" "--user=$User" --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "Database initialization failed at $file" }
}
& $MysqlExecutable --no-defaults "--host=$DatabaseHost" "--port=$Port" "--user=$User" --default-character-set=utf8mb4 "--database=$DatabaseName" '--execute=SELECT VERSION(); SHOW TABLES; SELECT rule_code,monster_name,enabled FROM monster_types;'
if ($LASTEXITCODE -ne 0) { throw 'Database verification failed' }
