<#
.SYNOPSIS
  Exécute la collection HireLink API via Newman contre l'instance ISO.

.NOTES
  - Cible par défaut : http://127.0.0.1:18081/api/v1 (ISO)
  - Interdit : port 8081 / base hirelink (démo)
  - Mots de passe : HL_CANDIDATE_PASSWORD, HL_RECRUITER_PASSWORD, HL_ADMIN_PASSWORD
  - Aucun contournement CAPTCHA / OTP / MFA
#>
$ErrorActionPreference = "Stop"
$PostmanDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$Collection = Join-Path $PostmanDir "HireLink.postman_collection.json"
$EnvExample = Join-Path $PostmanDir "environments\HireLink.iso.environment.example.json"
$Reports = Join-Path $PostmanDir "reports"
New-Item -ItemType Directory -Force -Path $Reports | Out-Null

$BaseUrl = if ($env:HL_API) { $env:HL_API.TrimEnd("/") } else { "http://127.0.0.1:18081/api/v1" }
if ($BaseUrl -match ':8081\b') {
  throw "REFUS : baseUrl pointe vers la démo 8081. Utilisez l'API ISO (ex. 18081)."
}

$Builder = Join-Path $PSScriptRoot "build-collection.mjs"
if (Test-Path $Builder) { node $Builder | Out-Host }

if (-not $env:HL_CANDIDATE_PASSWORD -or -not $env:HL_RECRUITER_PASSWORD -or -not $env:HL_ADMIN_PASSWORD) {
  Write-Host "ERREUR : définissez HL_CANDIDATE_PASSWORD, HL_RECRUITER_PASSWORD, HL_ADMIN_PASSWORD (seed ISO)." -ForegroundColor Red
  exit 2
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$jsonReport = Join-Path $Reports "newman-$stamp.json"

Write-Host "Newman → $BaseUrl"
Write-Host "Collection → $Collection"

npx --yes newman run $Collection `
  -e $EnvExample `
  --env-var "baseUrl=$BaseUrl" `
  --env-var "candidateEmail=$(if ($env:HL_CANDIDATE_EMAIL) { $env:HL_CANDIDATE_EMAIL } else { 'candidat@hirelink.ma' })" `
  --env-var "recruiterEmail=$(if ($env:HL_RECRUITER_EMAIL) { $env:HL_RECRUITER_EMAIL } else { 'n.bennani@atlasdigital.ma' })" `
  --env-var "adminEmail=$(if ($env:HL_ADMIN_EMAIL) { $env:HL_ADMIN_EMAIL } else { 'admin@hirelink.ma' })" `
  --env-var "candidatePassword=$($env:HL_CANDIDATE_PASSWORD)" `
  --env-var "recruiterPassword=$($env:HL_RECRUITER_PASSWORD)" `
  --env-var "adminPassword=$($env:HL_ADMIN_PASSWORD)" `
  --delay-request 250 `
  --timeout-request 30000 `
  --reporters "cli,json" `
  --reporter-json-export $jsonReport

$exit = $LASTEXITCODE
Write-Host "Exit=$exit — rapport JSON : $jsonReport"
exit $exit
