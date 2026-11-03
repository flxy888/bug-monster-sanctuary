param([ValidateSet('jdk','maven','mysql','all')] [string]$Component = 'all')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolDir = Join-Path $root '.tools'
New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
$downloads = @(
    @{name='jdk'; url='https://corretto.aws/downloads/resources/21.0.13.0.10/amazon-corretto-21.0.13.0.10-windows-x64-jdk.zip'; archive='jdk21.zip'; sha256='ABD264A79E07C64E19AAA705E5B8EA8DB01D5213877B8AC0B855A94541A4B0FD'},
    @{name='maven'; url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'; archive='maven.zip'; sha256='4EC3F26FB1A692473AEA0235C300BD20F0F9FE741947C82C1234CEFD76AC3A3C'},
    @{name='mysql'; url='https://cdn.mysql.com/archives/mysql-8.4/mysql-8.4.7-winx64.zip'; archive='mysql.zip'; sha256='FD9BDBD4B5A878D31C8E4067078BD60665B1B3C4677FA1F099416D194B458AFF'}
)
foreach ($item in $downloads) {
    if ($Component -ne 'all' -and $Component -ne $item.name) { continue }
    $destination = Join-Path $toolDir $item.archive
    if (-not (Test-Path -LiteralPath $destination)) {
        Write-Output ('Downloading ' + $item.name + ' from ' + $item.url)
        Invoke-WebRequest -Uri $item.url -OutFile ($destination + '.part') -TimeoutSec 600
        Move-Item -LiteralPath ($destination + '.part') -Destination $destination
    }
    $actualHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    if ($actualHash -ne $item.sha256) { throw "SHA256 mismatch: $destination" }
    $expanded = Join-Path $toolDir $item.name
    if (-not (Test-Path -LiteralPath $expanded)) {
        Expand-Archive -LiteralPath $destination -DestinationPath $expanded
    }
    Write-Output ($item.name + ' SHA256 verified: ' + $actualHash)
}
