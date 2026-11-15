# Construit un fat JAR candidat sous un NOM DISTINCT — ne jamais ecraser un JAR
# utilise par une JVM active (cause NoClassDefFoundError / paiement 500).
# Usage : powershell -File scripts/build-b0-fat-jar.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Backend = Join-Path $Root 'backend'
$OutDir = Join-Path $Root 'docs\deploy-prep\b0-candidate-runtime'
$BuildDir = Join-Path $Root 'build-iso\b0-fatjar\maven-target'
$DemoJar = Join-Path $Root 'docs\deploy-prep\demo-runtime\hirelink-demo-8081.jar'
$LegacyName = Join-Path $OutDir 'hirelink-b0-backend.jar'

New-Item -ItemType Directory -Force -Path $OutDir, $BuildDir | Out-Null

if (-not (Test-Path $DemoJar)) { throw "JAR demo introuvable : $DemoJar" }
$demoHashBefore = (Get-FileHash $DemoJar -Algorithm SHA256).Hash

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw "JDK 23 requis : $env:JAVA_HOME" }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Push-Location $Backend
try {
    & mvn -DskipTests package "-DbuildDirectory=$BuildDir" '-Dspring-boot.repackage.skip=false'
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

$built = Join-Path $BuildDir 'hirelink-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path $built)) { throw "Fat JAR non produit : $built" }
$size = (Get-Item $built).Length
if ($size -lt 5MB) { throw "JAR trop petit ($size) — probablement thin, pas fat" }

& jar tf $built | Select-String -Pattern 'BOOT-INF/lib/|org/springframework/boot/loader/launch/JarLauncher|ExternalOfferListability|AiCreditService' |
    ForEach-Object { $_.Line } | Out-File (Join-Path $OutDir 'jar-contents-check.txt') -Encoding utf8
$check = Get-Content (Join-Path $OutDir 'jar-contents-check.txt') -Raw
if ($check -notmatch 'JarLauncher') { throw 'JarLauncher absent — pas un fat JAR Spring Boot' }
if ($check -notmatch 'BOOT-INF/lib/') { throw 'BOOT-INF/lib absent' }
if ($check -notmatch 'ExternalOfferListability') { throw 'Classe B0 ExternalOfferListability absente du JAR' }

$preHash = (Get-FileHash $built -Algorithm SHA256).Hash
$short = $preHash.Substring(0, 12)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$CandidateJar = Join-Path $OutDir ("hirelink-candidate-{0}-{1}.jar" -f $stamp, $short)
Copy-Item $built $CandidateJar -Force

# Pointeur latest = copie seulement si le nom legacy n'est PAS verrouille par une JVM.
$latest = Join-Path $OutDir 'hirelink-b0-backend-latest.jar'
Copy-Item $CandidateJar $latest -Force
$legacyUpdated = $false
try {
    $fs = [System.IO.File]::Open($LegacyName, 'OpenOrCreate', 'ReadWrite', 'None')
    $fs.Close()
    Copy-Item $CandidateJar $LegacyName -Force
    $legacyUpdated = $true
} catch {
    Write-Warning "JAR legacy verrouille (JVM active) — non ecrase. Utiliser : $CandidateJar"
}

$candHash = (Get-FileHash $CandidateJar -Algorithm SHA256).Hash
$demoHashAfter = (Get-FileHash $DemoJar -Algorithm SHA256).Hash
if ($demoHashBefore -ne $demoHashAfter) { throw 'JAR DEMO MODIFIE — abort' }

@"
built_at=$(Get-Date -Format o)
candidate=$CandidateJar
candidate_sha256=$candHash
candidate_bytes=$size
latest_pointer=$latest
legacy_path=$LegacyName
legacy_updated=$legacyUpdated
demo_jar=$DemoJar
demo_sha256=$demoHashAfter
demo_unchanged=$($demoHashBefore -eq $demoHashAfter)
maven_buildDirectory=$BuildDir
"@ | Set-Content (Join-Path $OutDir 'BUILD_META.txt') -Encoding utf8

Write-Output "OK candidate=$CandidateJar sha=$candHash bytes=$size legacy_updated=$legacyUpdated demo_unchanged=True"
