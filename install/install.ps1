# den-tv installer for Windows: the same steps as install.sh. Puts den-tv and the Fan Hub on an Android TV or Google TV
# on the same Wi-Fi and makes den-tv the home screen. Needs nothing installed: it fetches Google's adb tool and the
# newest den-tv release.
#
#   powershell -ExecutionPolicy Bypass -File install.ps1                 asks for the TV's IP address
#   powershell -ExecutionPolicy Bypass -File install.ps1 192.168.1.50    installs (or updates) on that TV
#   ... install.ps1 192.168.1.50 -Undo        puts the TV's own home screen back and removes den-tv
#   -NoHelper, -NoScreensaver, -KeepDebugging answer those questions up front
#   -Ethernet   the TV is on a network cable: skips the check of who owns its saved Wi-Fi network
#
# The TV needs USB debugging on first (INSTALL.md, step 1). Google's launcher is switched off only once den-tv has
# started, and put back if Home doesn't end up on den-tv, so the TV is never left without a home screen.
param(
    [string]$Ip = '',
    [switch]$Undo,
    [switch]$NoHelper,
    [switch]$NoScreensaver,
    [switch]$KeepDebugging,
    [switch]$Ethernet
)
# Native tools write to stderr in normal use; with 'Stop' Windows PowerShell 5.1 would treat that as a failure.
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest is many times slower with its progress bar
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Repo = if ($env:DEN_REPO) { $env:DEN_REPO } else { 'knightfox1510/den-tv' }
$Work = if ($env:DEN_HOME) { $env:DEN_HOME } else { Join-Path $env:USERPROFILE '.den-tv' }
$Launcher = 'tv.den.launcher'
$Component = "$Launcher/com.gathin.tvhome.MainActivity"
$FanHub = 'tv.den.fanhub'
$Helper = "$FanHub/tv.den.fanhub.Helper"
$Dream = "$Launcher/com.gathin.tvhome.DenDream"
# Home screens that come with the TV: Google TV, Android TV, older Android TV.
$Stock = @('com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher', 'com.google.android.leanbacklauncher')
# Google TV's setup wizard also claims Home and takes it back unless it is switched off too.
$Wizard = 'com.google.android.tungsten.setupwraith'
$HomeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

function Say($text) { Write-Host ''; Write-Host $text -ForegroundColor Cyan }
function Stop-Install($text) { Write-Host ''; Write-Host "Stopped: $text" -ForegroundColor Red; exit 1 }
function Ask($question, $default) {
    $hint = if ($default -eq 'y') { '[Y/n]' } else { '[y/N]' }
    $a = ''
    try { $a = Read-Host "$question $hint" } catch { $a = '' }
    if (-not $a) { $a = $default }
    return $a -match '^[Yy]'
}

if (-not $Ip) { $Ip = Read-Host "The TV's IP address (Settings, Network & Internet, your Wi-Fi), e.g. 192.168.1.50" }
$Ip = $Ip.Trim()
# A port can follow, for TVs whose Wireless debugging shows one (192.168.1.50:41234); otherwise ADB's own 5555.
if ($Ip -notmatch '^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$') { Stop-Install "'$Ip' isn't an IP address like 192.168.1.50." }
if ($Ip -like '*:*') { $T = $Ip; $Ip = $Ip.Split(':')[0] } else { $T = "${Ip}:5555" }
New-Item -ItemType Directory -Force -Path $Work | Out-Null

# ---- adb ------------------------------------------------------------------------------------------------------------

$found = Get-Command adb -ErrorAction SilentlyContinue
if ($found) { $Adb = $found.Source }
elseif (Test-Path (Join-Path $Work 'platform-tools\adb.exe')) { $Adb = Join-Path $Work 'platform-tools\adb.exe' }
else {
    Say "Getting adb, Android's own tool for talking to the TV, from Google (about 10 MB)..."
    $zip = Join-Path $Work 'platform-tools.zip'
    try { Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip }
    catch { Stop-Install "couldn't download adb. Check the internet connection and run this again." }
    Expand-Archive -Force -Path $zip -DestinationPath $Work
    Remove-Item $zip
    $Adb = Join-Path $Work 'platform-tools\adb.exe'
}

# adb against the TV, its output as one string without Windows line ends.
function Tv { $out = & $Adb -s $T @args 2>&1 | ForEach-Object { "$_" }; (($out -join "`n") -replace "`r", '') }
# A command run in the TV's shell. One string: adb passes it to the TV's shell as it is.
function Sh($command) { Tv shell $command }
function Installed($pkg) { (Sh "pm list packages $pkg") -split "`n" -contains "package:$pkg" }
function Enabled($pkg) { (Sh "pm list packages -e $pkg") -split "`n" -contains "package:$pkg" }
function HomeNow { ((Sh $HomeQuery) -split "`n" | Where-Object { $_ } | Select-Object -Last 1) }
# Home back to a package's own Home activity, worked out on the TV (single quotes: $(...) runs there, not here).
function HomeTo($pkg) { Sh ('cmd package set-home-activity --user 0 $(' + $HomeQuery + ' ' + $pkg + ' | tail -n 1)') | Out-Null }

Say "Connecting to the TV at $Ip..."
& $Adb connect $T 2>&1 | Out-Null
Write-Host 'Look at the TV now: if it asks "Allow USB debugging?", tick "Always allow from this computer" and choose Allow.'
$state = ''
for ($i = 1; $i -le 45; $i++) {
    $state = (& $Adb -s $T get-state 2>$null | Out-String).Trim()
    if ($state -eq 'device') { break }
    if ($i % 5 -eq 0) { & $Adb connect $T 2>&1 | Out-Null }
    Start-Sleep -Seconds 2
}
if ($state -ne 'device') {
    Stop-Install "the TV at $Ip didn't answer. Check that it's on, that USB debugging is on (INSTALL.md, step 1), that the IP address is right and that this computer is on the same Wi-Fi. Then run this again."
}
Write-Host "Connected to $(Sh 'getprop ro.product.model') (Android $(Sh 'getprop ro.build.version.release'))."

# ---- undo -----------------------------------------------------------------------------------------------------------

if ($Undo) {
    Say "Putting the TV's own home screen back..."
    foreach ($p in $Stock) {
        if (Installed $p) { Sh "pm enable --user 0 $p" | Out-Null; HomeTo $p; break }
    }
    if (Installed $Wizard) { Sh "pm enable --user 0 $Wizard" | Out-Null }
    if ((Sh 'settings get secure screensaver_components') -eq $Dream) { Sh 'settings delete secure screensaver_components' | Out-Null }
    $list = Sh 'settings get secure enabled_accessibility_services'
    if ($list -like "*$Helper*") {
        $rest = (($list -split ':') | Where-Object { $_ -and $_ -ne $Helper }) -join ':'
        if ($rest) { Sh "settings put secure enabled_accessibility_services '$rest'" | Out-Null }
        else { Sh 'settings delete secure enabled_accessibility_services' | Out-Null }
    }
    Tv uninstall $FanHub | Out-Null
    Tv uninstall $Launcher | Out-Null
    Sh 'input keyevent KEYCODE_HOME' | Out-Null
    Say "Done: den-tv is removed and the TV's own home screen is back. Turn USB debugging off in Developer options."
    exit 0
}

# ---- install --------------------------------------------------------------------------------------------------------

$sdk = 0
[int]::TryParse((Sh 'getprop ro.build.version.sdk').Trim(), [ref]$sdk) | Out-Null
if ($sdk -lt 26) { Stop-Install "this TV runs Android SDK $sdk; den-tv needs Android 8 (SDK 26) or newer." }

# Google TV's setup wizard: where it answers Home it has to go with Google's home screen, or Home falls to its
# RecoveryActivity (a black screen system_server can crash-loop on). But it owns the Wi-Fi network saved during the
# TV's first setup, and Android deletes that network when the wizard is switched off; it can also hold the casting
# credentials. So it goes only where it answers Home, and never while it owns the Wi-Fi. Checked before anything is
# installed. (cleanyourtv's tools/disable.sh learned this on many TVs.)
$wizardOff = $false
if ((Enabled $Wizard) -and ((Sh 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -like "*$Wizard/*")) {
    $owners = [regex]::Matches((Sh 'dumpsys wifi'), 'cname=[^ \n]*') | ForEach-Object { $_.Value }
    if ($owners | Where-Object { $_ -like "cname=$Wizard*" }) {
        Stop-Install "the Wi-Fi network this TV is on was saved by Google's setup, and switching that off (which den-tv needs on this TV) would delete it and take the TV off Wi-Fi. On the TV, in Settings > Network & Internet, choose your network, Forget it, join it again with its password, and run this again. Nothing was changed."
    }
    if (-not $owners -and -not $Ethernet) {
        Stop-Install "the TV didn't say which app owns its saved Wi-Fi network, so it isn't safe to switch off Google's setup, which den-tv needs on this TV. Run this again in a minute; if the TV is on a network cable, add -Ethernet. Nothing was changed."
    }
    $wizardOff = $true
}

function Get-Apk($name) {
    $to = Join-Path $Work "$name.apk"
    if ($env:DEN_APKS) { Copy-Item (Join-Path $env:DEN_APKS "$name.apk") $to; return }
    try { Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/$Repo/releases/latest/download/$name.apk" -OutFile $to }
    catch { Stop-Install "couldn't download $name.apk from https://github.com/$Repo/releases (the repo has to be public, with a release)." }
}
Say 'Downloading the newest den-tv...'
Get-Apk 'den-tv-launcher'
Get-Apk 'den-tv-fanhub'

function Install-Apk($name, $label) {
    $out = Tv install -r (Join-Path $Work "$name.apk")
    if ($out -match 'Success') { Write-Host "$label installed."; return }
    if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Stop-Install "this TV has a $label signed with a different key (an older test build). Run this again with -Undo, then install again."
    }
    Stop-Install "$label didn't install: $((($out -split "`n") | Select-Object -Last 1))"
}
# The launcher first: it defines the permission the Fan Hub needs to change the look.
Say 'Installing on the TV (this takes a minute)...'
Install-Apk 'den-tv-launcher' 'den-tv'
Install-Apk 'den-tv-fanhub' 'Fan Hub'

Say 'Making den-tv the home screen...'
$homePkg = ((HomeNow) -split '/')[0]
# An update replaces the running Home; start a fresh one, as Android's restarted copy can draw wrongly.
Sh "am force-stop $Launcher" | Out-Null
$out = Sh "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $Component"
if ($out -notmatch 'Status: ok' -or $out -match '(?m)^Error') {
    Stop-Install "den-tv didn't start, so the TV's own home screen was left as it is. What the TV said: $out"
}

$disabled = @()
$candidates = if ($wizardOff) { $Stock + $Wizard } else { $Stock }
foreach ($p in $candidates) {
    if (Enabled $p) { Sh "pm disable-user --user 0 $p" | Out-Null; $disabled += $p }
}
Sh "cmd package set-home-activity --user 0 $Component" | Out-Null
$now = HomeNow
if ($now -like "$Launcher/*") { Write-Host 'den-tv is the home screen.' }
else {
    foreach ($p in $disabled) { Sh "pm enable --user 0 $p" | Out-Null }
    if ($homePkg -and $homePkg -ne 'android' -and $homePkg -ne $Launcher) { HomeTo $homePkg }
    Stop-Install "Home went to '$now' instead of den-tv, so the TV's own home screen was put back. This TV's maker may lock its home screen; see Troubleshooting in INSTALL.md."
}

# Lets Home show the Wi-Fi name; lets the Fan Hub install den-tv's updates, and switch its helper back on after one.
Sh "pm grant $Launcher android.permission.ACCESS_FINE_LOCATION" | Out-Null
Sh "appops set $FanHub REQUEST_INSTALL_PACKAGES allow" | Out-Null
Sh "pm grant $FanHub android.permission.WRITE_SECURE_SETTINGS" | Out-Null
# TCL firmware won't bind the helper, or start the Fan Hub after a reboot, without its auto-start app-op; other TVs
# don't know the op and ignore it.
Sh "cmd appops set $FanHub AUTO_START allow; cmd appops set $FanHub APP_AUTO_START allow; appops write-settings" | Out-Null

Write-Host ''
Write-Host 'The den-tv helper lets the phone app press Back and Home, type, open apps and count screen time.'
Write-Host "It's an accessibility service that only notes which app is in front; it reads nothing else on screen."
if (-not $NoHelper -and (Ask 'Turn the den-tv helper on?' 'y')) {
    $list = Sh 'settings get secure enabled_accessibility_services'
    if ($list -like "*$Helper*") { $new = $list }
    elseif (-not $list -or $list -eq 'null') { $new = $Helper }
    else { $new = "${list}:$Helper" }
    # Cleared first: after an update Android binds the helper again only when the list changes.
    Sh 'settings delete secure enabled_accessibility_services' | Out-Null
    Sh "settings put secure enabled_accessibility_services '$new'" | Out-Null
    Sh 'settings put secure accessibility_enabled 1' | Out-Null
    Write-Host 'Helper on.'
}

Write-Host ''
if (-not $NoScreensaver -and (Ask "Use den-tv's screensaver (the look with a clock and what's next) instead of the TV's own?" 'y')) {
    Sh "settings put secure screensaver_components $Dream" | Out-Null
    Write-Host 'Screensaver set.'
}

Sh 'input keyevent KEYCODE_HOME' | Out-Null
Say 'den-tv is on the TV. On the TV, follow the welcome screen to pair your phone.'

Write-Host ''
Write-Host "USB debugging lets any computer on your Wi-Fi that you allow control the TV, so it's best switched off."
Write-Host 'You only need it again to update den-tv or to undo it.'
if (-not $KeepDebugging -and (Ask 'Turn USB debugging off now?' 'y')) {
    Sh 'settings put global adb_enabled 0' | Out-Null
    & $Adb disconnect $T 2>&1 | Out-Null
    Write-Host 'USB debugging is off.'
} else {
    Write-Host 'Remember to turn it off later: Settings, System, Developer options, USB debugging.'
}
