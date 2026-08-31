<#
.SYNOPSIS
    Installs or removes Invoice OCR, including the Tesseract OCR engine.

.DESCRIPTION
    Does the same work as the NSIS setup, without needing a build toolchain:

      * checks for Java 17 or newer,
      * installs the bundled Tesseract setup silently at its default location,
      * finds where that landed and points the application at it,
      * copies the application, the icon and the handbook into place,
      * writes invoice-ocr.properties,
      * creates Start Menu and desktop shortcuts,
      * registers the application under Programs and Features.

    The Tesseract install location is discovered, never assumed: the payload
    decides where it goes, and this script then looks it up.

    Must run elevated. It relaunches itself as administrator when it is not.

.PARAMETER InstallDir
    Where to install. Default: C:\Program Files\Invoice OCR

.PARAMETER TesseractSetup
    Path to tesseract-ocr-w64-setup-*.exe. Searched for next to this script and
    in Downloads when not given.

.PARAMETER Jar
    Path to invoice-ocr.jar. Searched for next to this script and in ..\target
    when not given.

.PARAMETER SkipTesseract
    Do not install Tesseract. Any existing installation is still detected.

.PARAMETER NoDesktopShortcut
    Do not create a desktop shortcut.

.PARAMETER Uninstall
    Remove a previous installation instead of installing.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File Install-InvoiceOCR.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File Install-InvoiceOCR.ps1 -Uninstall
#>

[CmdletBinding()]
param(
    [string] $InstallDir = (Join-Path $env:ProgramFiles 'Invoice OCR'),
    [string] $TesseractSetup,
    [string] $Jar,
    [switch] $SkipTesseract,
    [switch] $NoDesktopShortcut,
    [switch] $Uninstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$AppName    = 'Invoice OCR'
$AppVersion = '1.2.0'
$AppRegKey  = 'HKLM:\SOFTWARE\InvoiceOCR'
$UninstKey  = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\InvoiceOCR'
$MinJava    = 17
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step { param([string] $Text) Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Text) Write-Host "    $Text" -ForegroundColor DarkGray }
function Write-Warn { param([string] $Text) Write-Host "    $Text" -ForegroundColor Yellow }

#------------------------------------------------------------------ elevation

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrator)) {
    Write-Host 'Administrator rights are required; relaunching elevated...' -ForegroundColor Yellow
    $argumentList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
    foreach ($entry in $PSBoundParameters.GetEnumerator()) {
        if ($entry.Value -is [switch]) {
            if ($entry.Value.IsPresent) { $argumentList += "-$($entry.Key)" }
        } else {
            $argumentList += @("-$($entry.Key)", "`"$($entry.Value)`"")
        }
    }
    Start-Process -FilePath 'powershell.exe' -ArgumentList $argumentList -Verb RunAs
    return
}

#-------------------------------------------------------------------- helpers

function Get-JavaMajorVersion {
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $java) { return 0 }
    $output = cmd /c 'java -version 2>&1'
    $text = ($output | Out-String)
    if ($text -match '"(\d+)(?:\.(\d+))?') {
        if ($matches[1] -eq '1' -and $matches.Count -gt 2) { return [int] $matches[2] }
        return [int] $matches[1]
    }
    return 0
}

function Get-JavaWPath {
    $javaw = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($null -ne $javaw) { return $javaw.Source }
    return $null
}

<#
    Returns the path of a tessdata folder that actually contains language data,
    or $null. Fixed locations are checked first, then the uninstall registry in
    both views and in the per-user hive, because the Tesseract payload may
    install per machine or per user.
#>
function Find-Tessdata {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Tesseract-OCR'),
        (Join-Path ${env:ProgramFiles(x86)} 'Tesseract-OCR'),
        'C:\Tesseract-OCR',
        (Join-Path $env:LOCALAPPDATA 'Programs\Tesseract-OCR'),
        (Join-Path $env:LOCALAPPDATA 'Tesseract-OCR'),
        (Join-Path $env:APPDATA 'Tesseract-OCR')
    )

    $uninstallRoots = @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall'
    )
    foreach ($root in $uninstallRoots) {
        if (-not (Test-Path $root)) { continue }
        foreach ($key in Get-ChildItem $root -ErrorAction SilentlyContinue) {
            $properties = Get-ItemProperty $key.PSPath -ErrorAction SilentlyContinue
            if ($null -eq $properties) { continue }
            $name = $properties.PSObject.Properties['DisplayName']
            if ($null -eq $name -or $name.Value -notlike 'Tesseract*') { continue }

            $location = $properties.PSObject.Properties['InstallLocation']
            if ($null -ne $location -and $location.Value) {
                $candidates += $location.Value
            } else {
                $uninstallString = $properties.PSObject.Properties['UninstallString']
                if ($null -ne $uninstallString -and $uninstallString.Value) {
                    $candidates += Split-Path -Parent ($uninstallString.Value.Trim('"'))
                }
            }
        }
    }

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $folder = Join-Path $candidate 'tessdata'
        if (Test-Path $folder) {
            $data = Get-ChildItem (Join-Path $folder '*.traineddata') -ErrorAction SilentlyContinue
            if ($data) { return $folder }
        }
    }
    return $null
}

function New-Shortcut {
    param(
        [string] $Path,
        [string] $Target,
        [string] $Arguments = '',
        [string] $WorkingDirectory = '',
        [string] $IconPath = '',
        [string] $Description = ''
    )
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($Path)
    $shortcut.TargetPath = $Target
    if ($Arguments)        { $shortcut.Arguments = $Arguments }
    if ($WorkingDirectory) { $shortcut.WorkingDirectory = $WorkingDirectory }
    if ($IconPath)         { $shortcut.IconLocation = $IconPath }
    if ($Description)      { $shortcut.Description = $Description }
    $shortcut.Save()
}

function Resolve-Payload {
    param([string] $Given, [string[]] $Patterns, [string] $What)
    if ($Given) {
        if (-not (Test-Path $Given)) { throw "$What not found: $Given" }
        return (Resolve-Path $Given).Path
    }
    foreach ($pattern in $Patterns) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

#------------------------------------------------------------------ uninstall

if ($Uninstall) {
    Write-Step "Removing $AppName"

    $installed = $InstallDir
    $ownTessdata = $false
    if (Test-Path $AppRegKey) {
        $stored = Get-ItemProperty $AppRegKey
        if ($stored.PSObject.Properties['InstallDir']) { $installed = $stored.InstallDir }
        if ($stored.PSObject.Properties['OwnTessdata']) { $ownTessdata = ($stored.OwnTessdata -eq '1') }
    }

    foreach ($file in 'invoice-ocr.jar', 'invoice-ocr.ico', 'invoice-ocr.properties', 'invoice-ocr.properties.bak', 'Install-InvoiceOCR.ps1', 'LICENSE.txt', 'NOTICE.txt', 'THIRD-PARTY-NOTICES.md') {
        $path = Join-Path $installed $file
        if (Test-Path $path) { Remove-Item $path -Force }
    }
    $docs = Join-Path $installed 'docs'
    if (Test-Path $docs) { Remove-Item $docs -Recurse -Force }
    if ($ownTessdata) {
        $own = Join-Path $installed 'tessdata'
        if (Test-Path $own) { Remove-Item $own -Recurse -Force }
    }
    if ((Test-Path $installed) -and -not (Get-ChildItem $installed -Force)) {
        Remove-Item $installed -Force
    }
    Write-Ok "removed files from $installed"

    $startMenu = Join-Path $env:ProgramData "Microsoft\Windows\Start Menu\Programs\$AppName"
    if (Test-Path $startMenu) { Remove-Item $startMenu -Recurse -Force }
    $desktop = Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) "$AppName.lnk"
    if (Test-Path $desktop) { Remove-Item $desktop -Force }
    Write-Ok 'removed shortcuts'

    foreach ($key in $UninstKey, $AppRegKey) {
        if (Test-Path $key) { Remove-Item $key -Recurse -Force }
    }
    Write-Ok 'removed registry entries'

    Write-Host ''
    Write-Host "$AppName has been removed." -ForegroundColor Green
    Write-Host 'Tesseract OCR was left installed; remove it from Programs and Features if you no longer need it.' -ForegroundColor DarkGray
    return
}

#-------------------------------------------------------------------- install

Write-Host ''
Write-Host "$AppName $AppVersion setup" -ForegroundColor White
Write-Host ''

# --- 1. Java ---------------------------------------------------------------
Write-Step 'Java'
$javaMajor = Get-JavaMajorVersion
$javaw = Get-JavaWPath
if ($javaMajor -ge $MinJava) {
    Write-Ok "Java $javaMajor found at $javaw"
} else {
    Write-Warn "Java $MinJava or newer was not found (detected: $javaMajor)."
    Write-Warn 'The application will not start until it is installed: https://adoptium.net'
    Write-Warn 'Continuing so the rest of the installation is in place.'
}

# --- 2. Payload ------------------------------------------------------------
Write-Step 'Payload'
$Jar = Resolve-Payload -Given $Jar -What 'invoice-ocr.jar' -Patterns @(
    (Join-Path $ScriptDir 'invoice-ocr.jar'),
    (Join-Path $ScriptDir '..\target\invoice-ocr.jar')
)
if (-not $Jar) {
    throw "invoice-ocr.jar was not found. Build it with 'mvn clean package', or pass -Jar <path>."
}
Write-Ok ("{0} ({1:N1} MB)" -f $Jar, ((Get-Item $Jar).Length / 1MB))

$TesseractSetup = Resolve-Payload -Given $TesseractSetup -What 'Tesseract setup' -Patterns @(
    (Join-Path $ScriptDir 'payload\tesseract-ocr-w64-setup-*.exe'),
    (Join-Path $ScriptDir 'tesseract-ocr-w64-setup-*.exe'),
    (Join-Path $env:USERPROFILE 'Downloads\tesseract-ocr-w64-setup-*.exe')
)
if ($TesseractSetup) {
    Write-Ok ("{0} ({1:N1} MB)" -f $TesseractSetup, ((Get-Item $TesseractSetup).Length / 1MB))
} else {
    Write-Ok 'no Tesseract setup found; an existing installation will be used if there is one'
}

# --- 3. Tesseract ----------------------------------------------------------
Write-Step 'OCR engine'
$tessdata = Find-Tessdata
if ($tessdata) {
    Write-Ok "Tesseract language data already present: $tessdata"
} elseif ($SkipTesseract) {
    Write-Warn 'Tesseract installation skipped on request.'
} elseif ($TesseractSetup) {
    Write-Ok 'installing Tesseract silently at its default location, this takes a moment'
    $process = Start-Process -FilePath $TesseractSetup -ArgumentList '/S' -Wait -PassThru
    Write-Ok "installer exit code $($process.ExitCode)"
    $tessdata = Find-Tessdata
    if ($tessdata) {
        Write-Ok "installed, language data at: $tessdata"
    } else {
        Write-Warn 'Tesseract ran but no language data could be found afterwards.'
    }
} else {
    Write-Warn 'No Tesseract setup available and none installed.'
}

# --- 4. Files --------------------------------------------------------------
Write-Step 'Installing files'
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item $Jar (Join-Path $InstallDir 'invoice-ocr.jar') -Force

$icon = Join-Path $ScriptDir 'assets\invoice-ocr.ico'
$iconTarget = ''
if (Test-Path $icon) {
    $iconTarget = Join-Path $InstallDir 'invoice-ocr.ico'
    Copy-Item $icon $iconTarget -Force
}

# Licence and attributions travel with the build. The Apache-2.0 components
# bundled in the jar require their NOTICE to accompany any redistribution.
foreach ($legal in @(
        @{ Source = '..\LICENSE';                 Target = 'LICENSE.txt' },
        @{ Source = '..\NOTICE';                  Target = 'NOTICE.txt' },
        @{ Source = '..\THIRD-PARTY-NOTICES.md';  Target = 'THIRD-PARTY-NOTICES.md' })) {
    $from = Join-Path $ScriptDir $legal.Source
    if (Test-Path $from) { Copy-Item $from (Join-Path $InstallDir $legal.Target) -Force }
}

$handbook = Join-Path $ScriptDir '..\user-docu\html'
if (Test-Path $handbook) {
    $docsTarget = Join-Path $InstallDir 'docs'
    New-Item -ItemType Directory -Force -Path $docsTarget | Out-Null
    Copy-Item (Join-Path $handbook '*') $docsTarget -Recurse -Force
    Write-Ok 'user handbook installed to docs\'
}
Write-Ok $InstallDir

# --- 5. Language data ------------------------------------------------------
Write-Step 'Language data'
$ownTessdata = '0'
$bundled = Join-Path $ScriptDir 'payload\tessdata'
if (-not $tessdata) {
    $tessdata = Join-Path $InstallDir 'tessdata'
    New-Item -ItemType Directory -Force -Path $tessdata | Out-Null
    $ownTessdata = '1'
    Write-Ok "using a local folder: $tessdata"
}
if (Test-Path $bundled) {
    foreach ($file in Get-ChildItem (Join-Path $bundled '*.traineddata') -ErrorAction SilentlyContinue) {
        $target = Join-Path $tessdata $file.Name
        if (-not (Test-Path $target)) {
            Copy-Item $file.FullName $target -Force
            Write-Ok "added $($file.Name)"
        }
    }
}
$available = @(Get-ChildItem (Join-Path $tessdata '*.traineddata') -ErrorAction SilentlyContinue)
if ($available.Count -eq 0) {
    Write-Warn "No language data in $tessdata. The application cannot read invoices until some is added."
} else {
    Write-Ok ("{0}: {1}" -f $tessdata, (($available | ForEach-Object { $_.BaseName }) -join ', '))
}

# --- 6. Settings -----------------------------------------------------------
Write-Step 'Settings'
$properties = Join-Path $InstallDir 'invoice-ocr.properties'
if (Test-Path $properties) {
    Copy-Item $properties "$properties.bak" -Force
    Write-Ok 'previous settings kept as invoice-ocr.properties.bak'
}
$language = 'eng'
if (Test-Path (Join-Path $tessdata 'ron.traineddata')) { $language = 'ron+eng' }

# A .properties file reads a backslash as an escape, so paths use forward slashes.
$tessdataForProperties = $tessdata -replace '\\', '/'
# Only the settings this machine actually needs are written live. The rest are
# listed commented-out: it makes them discoverable in the file the user is told
# to edit, while leaving the defaults where they belong - in the jar - so a later
# release can change one without every installed machine overriding it.
@(
    "# $AppName $AppVersion settings, written by the installer.",
    '# Use forward slashes in paths. Restart the application after editing.',
    '',
    "ocr.tessdata.path=$tessdataForProperties",
    "ocr.language=$language",
    'ui.locale=ro',
    '',
    '# --- reading (1.2.0) ------------------------------------------------',
    '# A page is read up to this many times, each with the picture prepared',
    '# differently, and the answers compared. Reading stops as soon as one is',
    '# good enough, so a clean scan still costs a single pass.',
    '#   1 = one reading, exactly as version 1.1 behaved',
    '#ocr.passes.maximum=4',
    '#ocr.passes.targetConfidence=0.80',
    '',
    '# Read the rows of the goods table, and mark values that were worked',
    '# out rather than read from their own label.',
    '#extraction.lineItems.enabled=true',
    '#report.showConfidence=true',
    '#report.lineItems=true',
    '',
    '# Every setting is documented in docs\index.html -> Settings.'
) | Set-Content -Path $properties -Encoding UTF8
Write-Ok "ocr.tessdata.path=$tessdataForProperties, ocr.language=$language"

# --- 7. Shortcuts ----------------------------------------------------------
Write-Step 'Shortcuts'
$startMenu = Join-Path $env:ProgramData "Microsoft\Windows\Start Menu\Programs\$AppName"
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null

$jarPath = Join-Path $InstallDir 'invoice-ocr.jar'
if ($javaw) {
    $target = $javaw
    $arguments = "-jar `"$jarPath`""
} else {
    $target = $jarPath
    $arguments = ''
}
New-Shortcut -Path (Join-Path $startMenu "$AppName.lnk") -Target $target -Arguments $arguments `
             -WorkingDirectory $InstallDir -IconPath $iconTarget -Description $AppName
if (Test-Path (Join-Path $InstallDir 'docs\index.html')) {
    New-Shortcut -Path (Join-Path $startMenu 'User handbook.lnk') `
                 -Target (Join-Path $InstallDir 'docs\index.html')
}
if (-not $NoDesktopShortcut) {
    $desktop = Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) "$AppName.lnk"
    New-Shortcut -Path $desktop -Target $target -Arguments $arguments `
                 -WorkingDirectory $InstallDir -IconPath $iconTarget -Description $AppName
}
Write-Ok 'Start Menu and desktop'

# --- 8. Registry -----------------------------------------------------------
Write-Step 'Registering'
Copy-Item $PSCommandPath (Join-Path $InstallDir 'Install-InvoiceOCR.ps1') -Force
$uninstallCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$InstallDir\Install-InvoiceOCR.ps1`" -Uninstall"

New-Item -Path $AppRegKey -Force | Out-Null
Set-ItemProperty $AppRegKey 'InstallDir'   $InstallDir
Set-ItemProperty $AppRegKey 'Version'      $AppVersion
Set-ItemProperty $AppRegKey 'TessdataPath' $tessdata
Set-ItemProperty $AppRegKey 'OwnTessdata'  $ownTessdata

New-Item -Path $UninstKey -Force | Out-Null
Set-ItemProperty $UninstKey 'DisplayName'     $AppName
Set-ItemProperty $UninstKey 'DisplayVersion'  $AppVersion
Set-ItemProperty $UninstKey 'Publisher'       $AppName
Set-ItemProperty $UninstKey 'InstallLocation' $InstallDir
Set-ItemProperty $UninstKey 'UninstallString' $uninstallCommand
Set-ItemProperty $UninstKey 'QuietUninstallString' $uninstallCommand
Set-ItemProperty $UninstKey 'NoModify' 1 -Type DWord
Set-ItemProperty $UninstKey 'NoRepair' 1 -Type DWord
if ($iconTarget) { Set-ItemProperty $UninstKey 'DisplayIcon' $iconTarget }
Write-Ok 'listed under Programs and Features'

Write-Host ''
Write-Host "$AppName $AppVersion is installed in $InstallDir" -ForegroundColor Green
if ($javaMajor -lt $MinJava) {
    Write-Host "Install Java $MinJava or newer before starting it: https://adoptium.net" -ForegroundColor Yellow
} else {
    Write-Host 'Start it from the Start Menu, or from the desktop shortcut.' -ForegroundColor DarkGray
}
