# Requires Windows PowerShell 5.1 or PowerShell 7, and adb on PATH.
# Installs the THRONE][OS debug APKs and tries to select the launcher as Home.
# Does not wipe the device, unlock the bootloader, root the phone, flash partitions, or remove packages.

[CmdletBinding()]
param(
    [ValidateSet("keter", "archangel")]
    [string]$Edition = "keter",
    [string]$Serial,
    [string]$LauncherApk,
    [string]$GuardApk,
    [string]$VaultApk,
    [string]$SuiteApk,
    [string]$NetherApk
)

$ErrorActionPreference = "Stop"
$LauncherPackage = "com.infinitum.throne.launcher"
$GuardPackage = "com.infinitum.throne.guard"
$VaultPackage = "com.infinitum.throne.vault"
$SuitePackage = "com.infinitum.throne.suite"
$NetherPackage = "app.infinitum.nether"

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    throw "adb was not found. Install Android platform-tools and add them to PATH."
}

function Resolve-DebugApk {
    param(
        [Parameter(Mandatory = $true)][string]$Module,
        [Parameter(Mandatory = $true)][string]$Flavor
    )
    $dir = Join-Path $Root "apps\$Module\build\outputs\apk\$Flavor\debug"
    $found = Get-ChildItem -Path $dir -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { return $null }
    return $found.FullName
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$ArgumentList)
    $output = & $script:AdbExe @script:AdbPrefix @ArgumentList 2>&1 | ForEach-Object { "$_" }
    $text = ($output -join "`n").Trim()
    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Text     = $text
    }
}

$Root = Split-Path -Parent $PSScriptRoot
$AdbExe = Resolve-Adb
$AdbPrefix = @()
if ($Serial) { $AdbPrefix = @("-s", $Serial) }

Write-Host "THRONE][OS KM4 apply"
Write-Host "Installs debug APKs and tries to set the Home app."
Write-Host "Does not wipe, unlock, root, flash, or remove packages."
Write-Host ""

$devices = Invoke-Adb -ArgumentList @("devices")
$ready = @($devices.Text -split "`n" | Where-Object { $_ -match "`tdevice$" })
if ($ready.Count -eq 0) {
    throw "No adb device in the 'device' state.`n$($devices.Text)"
}
if (-not $Serial -and $ready.Count -gt 1) {
    throw "More than one device is connected. Re-run with -Serial."
}

$reportedDevice = (Invoke-Adb -ArgumentList @("shell", "getprop", "ro.product.device")).Text
$reportedProduct = (Invoke-Adb -ArgumentList @("shell", "getprop", "ro.product.name")).Text
$reportedRelease = (Invoke-Adb -ArgumentList @("shell", "getprop", "ro.build.version.release")).Text
Write-Host "Phone reports device=$reportedDevice product=$reportedProduct release=$reportedRelease"
if ($reportedDevice -notmatch "KM4" -and $reportedProduct -notmatch "KM4") {
    Write-Host "This phone did not report KM4. Continuing. The script only installs apps."
}

if (-not $LauncherApk) {
    $LauncherApk = Join-Path $Root "apps\throne-launcher\build\outputs\apk\$Edition\debug"
    $found = Get-ChildItem -Path $LauncherApk -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { $LauncherApk = $null } else { $LauncherApk = $found.FullName }
}
if (-not $GuardApk) {
    $guardDir = Join-Path $Root "apps\throne-guard\build\outputs\apk\$Edition\debug"
    $found = Get-ChildItem -Path $guardDir -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { $GuardApk = $null } else { $GuardApk = $found.FullName }
}
if (-not $VaultApk) {
    $vaultDir = Join-Path $Root "apps\throne-vault-notes\build\outputs\apk\$Edition\debug"
    $found = Get-ChildItem -Path $vaultDir -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { $VaultApk = $null } else { $VaultApk = $found.FullName }
}
if (-not $SuiteApk) {
    $suiteDir = Join-Path $Root "apps\throne-suite\build\outputs\apk\$Edition\debug"
    $found = Get-ChildItem -Path $suiteDir -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { $SuiteApk = $null } else { $SuiteApk = $found.FullName }
}
if (-not $NetherApk) {
    $netherDir = Join-Path $Root "apps\nether\build\outputs\apk\$Edition\debug"
    $found = Get-ChildItem -Path $netherDir -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "androidTest" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $found) { $NetherApk = $null } else { $NetherApk = $found.FullName }
}

if (-not $LauncherApk -or -not $GuardApk -or -not $VaultApk -or -not $SuiteApk -or -not $NetherApk) {
    throw @"
A debug APK is missing for edition '$Edition'.
Build it first, from the repo root:
  .\gradlew test assembleDebug
Expected:
  apps\throne-launcher\build\outputs\apk\$Edition\debug\*.apk
  apps\throne-guard\build\outputs\apk\$Edition\debug\*.apk
  apps\throne-vault-notes\build\outputs\apk\$Edition\debug\*.apk
  apps\throne-suite\build\outputs\apk\$Edition\debug\*.apk
  apps\nether\build\outputs\apk\$Edition\debug\*.apk
"@
}

function Install-One {
    param([string]$Path, [string]$Label)
    Write-Host "Installing $Label"
    Write-Host "  $Path"
    $result = Invoke-Adb -ArgumentList @("install", "-r", $Path)
    if ($result.ExitCode -ne 0 -or $result.Text -match "Failure|INSTALL_FAILED") {
        throw @"
Install failed for $Label.
$($result.Text)
If this is a signature mismatch, this script will not remove the installed package.
Debug builds share keystore/throne-debug.keystore. An older CI build used a different debug key.
Android will not replace that copy. Vault is left installed. Do not uninstall it from this script.
"@
    }
    Write-Host $result.Text
}

$EideticApk = Resolve-DebugApk -Module "eidetic-cam" -Flavor $Edition
$HrvstApk = Resolve-DebugApk -Module "hrvst-cam" -Flavor $Edition
if (-not $EideticApk -or -not $HrvstApk) {
    throw @"
A camera debug APK is missing for edition '$Edition'.
Build it first, from the repo root:
  .\gradlew test assembleDebug
Expected:
  apps\eidetic-cam\build\outputs\apk\$Edition\debug\*.apk
  apps\hrvst-cam\build\outputs\apk\$Edition\debug\*.apk
"@
}

Install-One -Path $LauncherApk -Label $LauncherPackage
Install-One -Path $GuardApk -Label $GuardPackage
Install-One -Path $VaultApk -Label $VaultPackage
Install-One -Path $SuiteApk -Label $SuitePackage
Install-One -Path $NetherApk -Label $NetherPackage
Install-One -Path $EideticApk -Label "com.infinitum.eidetic.cam"
Install-One -Path $HrvstApk -Label "com.infinitum.hrvst.cam"

Write-Host ""
Write-Host "Trying to grant the Home role to $LauncherPackage"
$role = Invoke-Adb -ArgumentList @("shell", "cmd", "role", "add-role-holder", "android.app.role.HOME", $LauncherPackage)
$home = Invoke-Adb -ArgumentList @("shell", "cmd", "package", "set-home-activity", "$LauncherPackage/.MainActivity")
$roleOk = ($role.ExitCode -eq 0 -and $role.Text -notmatch "SecurityException|Error")
$homeOk = ($home.ExitCode -eq 0 -and $home.Text -notmatch "SecurityException|Error")
if ($role.Text) { Write-Host $role.Text }
if ($home.Text) { Write-Host $home.Text }
if (-not $roleOk -and -not $homeOk) {
    Write-Host "Could not set the default Home app from adb. Opening the Home picker."
    $picker = Invoke-Adb -ArgumentList @("shell", "am", "start", "-a", "android.settings.HOME_SETTINGS")
    if ($picker.ExitCode -ne 0) {
        Invoke-Adb -ArgumentList @("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME") | Out-Null
    }
} else {
    Write-Host "Home role command returned success. HiOS may still ask you to confirm."
}

Write-Host ""
Write-Host "Remaining manual steps. This script does not do these."
Write-Host "1. If the Home picker is open, choose THRONE][OS. HiOS may ask once."
Write-Host "2. Open THRONE][OS Guard from the OTHERS page. INFINITUM lists the product catalog."
Write-Host "3. Set a PIN, pattern, or password if Guard says the screen lock is open."
Write-Host "4. Set Private DNS yourself: Settings, Network and internet, Private DNS, Private DNS provider hostname, dns.adguard-dns.com"
Write-Host "   Guard cannot write this setting. That hostname is a public resolver, not an Infinitum server."
Write-Host "5. Install VEIL VPN (com.infinitum.veilvpn) if it is not installed."
Write-Host "   In VPN settings enable Always-on VPN and Block connections without VPN."
Write-Host "6. In Google Messages, turn on RCS Chat features yourself. Encryption applies to RCS chats with other RCS users."
Write-Host "   Plain SMS and MMS, including RCS fallback, are not encrypted. This script cannot change Messages."
Write-Host "7. Hide lock-screen message previews. Guard links to notification settings and cannot see each Messages channel."
Write-Host "8. Use THRONE][OS Vault for a message you want sealed before it is handed to Messages. Compare safety numbers in person."
Write-Host "9. Turn off Developer options and USB debugging when you are done installing."
Write-Host "10. Device-owner policy is not applied. It would require a factory reset, which this script does not perform."
Write-Host "11. Nothing here is NIAP, FIPS, or DISA certified. OS-level hardening needs the Pixel and GrapheneOS build."
Write-Host ""
Write-Host "Done. No wipe, unlock, root, flash, or package removal was performed."
