<#
.SYNOPSIS
    Builds invoice-ocr-setup-<version>.exe.

.DESCRIPTION
    Collects everything the NSIS script needs, then compiles it:

      1. the application jar (built with Maven if it is missing),
      2. the Tesseract setup executable to bundle,
      3. the English and Romanian language files,
      4. the generated HTML handbook, if it is present,

    then runs makensis. Every step reports what it found, so a failed build
    says which ingredient was missing rather than failing inside NSIS.

.PARAMETER TesseractSetup
    Path to tesseract-ocr-w64-setup-*.exe. Searched for in the usual download
    folders when not given.

.PARAMETER MakeNsis
    Path to makensis.exe. Searched for in the usual install folders when not
    given.

.PARAMETER SkipLanguageDownload
    Do not fetch language files from the internet. The installer then relies
    entirely on the Tesseract payload for language data.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File build-installer.ps1
#>

[CmdletBinding()]
param(
    [string] $TesseractSetup,
    [string] $MakeNsis,
    [switch] $SkipLanguageDownload
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$here      = Split-Path -Parent $MyInvocation.MyCommand.Path
$root      = Split-Path -Parent $here
$payload   = Join-Path $here 'payload'
$tessdata  = Join-Path $payload 'tessdata'
$jar       = Join-Path $root 'target\invoice-ocr.jar'
$docs      = Join-Path $root 'user-docu\html\index.html'

function Write-Step { param([string] $Text) Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Text) Write-Host "    $Text" -ForegroundColor DarkGray }
function Fail       { param([string] $Text) throw $Text }

# ---------------------------------------------------------------- 1. the jar

Write-Step 'Application jar'
if (-not (Test-Path $jar)) {
    Write-Ok 'target\invoice-ocr.jar not found, running Maven'
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -eq $mvn) {
        Fail "target\invoice-ocr.jar is missing and Maven is not on PATH. Run 'mvn clean package' in $root first."
    }
    Push-Location $root
    try { & $mvn.Source -B clean package | Out-Null } finally { Pop-Location }
}
if (-not (Test-Path $jar)) { Fail "Maven did not produce $jar." }
Write-Ok ("{0} ({1:N1} MB)" -f $jar, ((Get-Item $jar).Length / 1MB))

# --------------------------------------------------- 2. the Tesseract payload

Write-Step 'Tesseract setup'
if (-not $TesseractSetup) {
    $candidates = @(
        (Join-Path $payload 'tesseract-ocr-w64-setup-*.exe'),
        (Join-Path $env:USERPROFILE 'Downloads\tesseract-ocr-w64-setup-*.exe'),
        (Join-Path $here 'tesseract-ocr-w64-setup-*.exe')
    )
    foreach ($pattern in $candidates) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
        if ($found) { $TesseractSetup = $found.FullName; break }
    }
}
if (-not $TesseractSetup -or -not (Test-Path $TesseractSetup)) {
    Fail @"
Could not find the Tesseract setup executable.
Download tesseract-ocr-w64-setup-*.exe from https://github.com/UB-Mannheim/tesseract/wiki
and either place it in $payload or pass -TesseractSetup <path>.
"@
}
$TesseractSetup = (Resolve-Path $TesseractSetup).Path
Write-Ok ("{0} ({1:N1} MB)" -f $TesseractSetup, ((Get-Item $TesseractSetup).Length / 1MB))

# -------------------------------------------------------- 3. the language data

Write-Step 'Language data'
New-Item -ItemType Directory -Force -Path $tessdata | Out-Null
$languages = @{
    'eng.traineddata' = 'https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata'
    'ron.traineddata' = 'https://github.com/tesseract-ocr/tessdata_fast/raw/main/ron.traineddata'
}
foreach ($name in $languages.Keys) {
    $target = Join-Path $tessdata $name
    if (Test-Path $target) {
        Write-Ok ("{0} already present ({1:N1} MB)" -f $name, ((Get-Item $target).Length / 1MB))
        continue
    }
    if ($SkipLanguageDownload) {
        Write-Ok "$name missing, skipped on request"
        continue
    }
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Write-Ok "downloading $name"
        Invoke-WebRequest -Uri $languages[$name] -OutFile $target -UseBasicParsing
        Write-Ok ("{0} ({1:N1} MB)" -f $name, ((Get-Item $target).Length / 1MB))
    } catch {
        Write-Warning "Could not download $name : $($_.Exception.Message)"
        Write-Warning 'The installer will fall back to whatever the Tesseract payload provides.'
        if (Test-Path $target) { Remove-Item $target -Force }
    }
}

# ------------------------------------------------------------- 4. the handbook

Write-Step 'User handbook'
if (Test-Path $docs) {
    Write-Ok 'user-docu\html found, it will be installed alongside the application'
} else {
    Write-Ok 'user-docu\html not found, skipping (run: cd user-docu; java build-html.java)'
}

# ----------------------------------------------------------------- 5. makensis

Write-Step 'NSIS'
if (-not $MakeNsis) {
    # Note the ${...} form: "$env:ProgramFiles(x86)" would expand $env:ProgramFiles
    # and leave a literal "(x86)" behind.
    $candidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'NSIS\makensis.exe'),
        (Join-Path $env:ProgramFiles 'NSIS\makensis.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\NSIS\makensis.exe')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { $MakeNsis = $candidate; break }
    }
    if (-not $MakeNsis) {
        $onPath = Get-Command makensis.exe -ErrorAction SilentlyContinue
        if ($onPath) { $MakeNsis = $onPath.Source }
    }
}
if (-not $MakeNsis -or -not (Test-Path $MakeNsis)) {
    Fail @"
makensis.exe was not found. Install NSIS 3.x, for example:

    winget install NSIS.NSIS

then run this script again, or pass -MakeNsis <path to makensis.exe>.
"@
}
Write-Ok $MakeNsis

# -------------------------------------------------------------------- 6. build

Write-Step 'Compiling the installer'
Push-Location $here
try {
    $arguments = @(
        '/V3',
        "/DTESSERACT_SETUP=$TesseractSetup",
        "/DAPP_JAR=$jar",
        "/DLANG_DATA_DIR=$tessdata",
        'invoice-ocr.nsi'
    )
    & $MakeNsis @arguments
    if ($LASTEXITCODE -ne 0) { Fail "makensis failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}

$output = Get-ChildItem (Join-Path $here 'invoice-ocr-setup-*.exe') |
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ''
Write-Host ("Built {0} ({1:N1} MB)" -f $output.FullName, ($output.Length / 1MB)) -ForegroundColor Green
Write-Host 'Run it as administrator, or silently with:  invoice-ocr-setup-1.2.0.exe /S' -ForegroundColor DarkGray
