param(
  [Parameter(Mandatory = $true)][string]$Database,
  [string]$Mysql = "C:\xampp\mysql\bin\mysql.exe",
  [switch]$Fresh
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
# Scripts live in database/migrations/ ; this runner may sit in database/
$root = Join-Path $scriptDir "migrations"
if (-not (Test-Path (Join-Path $root "V000__schema_baseline_mariadb.sql"))) { $root = $scriptDir }
if (-not (Test-Path $Mysql)) { throw "mysql introuvable: $Mysql" }

if ($Database -eq "hirelink") {
  Write-Host "ATTENTION: cible 'hirelink' (base demo habituelle). Preferer une base isolee pour les essais." -ForegroundColor Yellow
}

Write-Host "Migrations dir: $root"
Write-Host "=== HireLink migrations -> $Database ==="

function Apply-SqlFile([string]$path, [string]$db) {
  # Strip UTF-8 BOM if present (mysqldump / editors) ? BOM breaks MariaDB client on some installs.
  $bytes = [IO.File]::ReadAllBytes($path)
  if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    $bytes = $bytes[3..($bytes.Length - 1)]
  }
  $tmp = [IO.Path]::GetTempFileName()
  try {
    [IO.File]::WriteAllBytes($tmp, $bytes)
    & $Mysql -uroot --default-character-set=utf8mb4 $db -e "source $tmp"
    if ($LASTEXITCODE -ne 0) { throw "Echec SQL: $path" }
  } finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
  }
}

& $Mysql -uroot -e "CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ($LASTEXITCODE -ne 0) { throw "Impossible de creer/verifier la base $Database" }

if ($Fresh) {
  Write-Host "Fresh: DROP + recreate"
  & $Mysql -uroot -e "DROP DATABASE IF EXISTS ``$Database``; CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  if ($LASTEXITCODE -ne 0) { throw "Echec Fresh DROP/CREATE" }
  $baseline = Join-Path $root "V000__schema_baseline_mariadb.sql"
  if (-not (Test-Path $baseline)) { throw "Baseline manquante: $baseline" }
  Write-Host "Apply V000 baseline"
  Apply-SqlFile $baseline $Database
}

$incremental = @(
  "V001__legal_acceptances.sql",
  "V002__external_offers.sql",
  "V003__billing_invoice_org_nullable.sql",
  "V004__external_offer_url_quality.sql",
  "V005__external_job_boards_ats.sql",
  "V006__catalog_listings.sql"
)

foreach ($name in $incremental) {
  $path = Join-Path $root $name
  if (-not (Test-Path $path)) { throw "Migration manquante: $path" }
  Write-Host "Apply $name"
  Apply-SqlFile $path $Database
}

Write-Host "OK. Tables:"
& $Mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_type='BASE TABLE';"
if ($LASTEXITCODE -ne 0) { throw "Verification tables echouee" }

# Preuves minimales Phase 3
& $Mysql -uroot -N -e @"
SELECT CONCAT('catalog_listings=', COUNT(*)) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='catalog_listings'
UNION ALL SELECT CONCAT('external_job_boards=', COUNT(*)) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='external_job_boards'
UNION ALL SELECT CONCAT('url_quality_col=', COUNT(*)) FROM information_schema.columns WHERE table_schema='$Database' AND table_name='external_job_offers' AND column_name='url_quality';
"@

Write-Host "Migrations V001-V006 appliquees avec succes sur $Database"
exit 0
