param(
  [string]$AvdName,
  [switch]$Rebuild,
  [switch]$ColdBoot
)

$ErrorActionPreference = 'Stop'

function Write-Header($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Invoke-Gradle([string[]]$gradleArgs) {
  Push-Location $repoRoot
  & .\gradlew.bat --no-daemon @gradleArgs
  $code = $LASTEXITCODE
  Pop-Location
  if ($code -ne 0) { throw "Gradle failed with exit code $code" }
}

function Wait-ForDeviceReady {
  Write-Header 'Wait for device'
  & $adb wait-for-device

  Write-Header 'Wait for boot completed'
  for ($i=0; $i -lt 240; $i++) {
    try {
      $state = (& $adb get-state 2>$null).Trim()
      $boot = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
      if ($state -eq 'device' -and $boot -eq '1') { return }
    } catch {}
    Start-Sleep -Seconds 2
  }
  throw 'Device did not become ready in time.'
}

# Resolve SDK path
$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkDir = Join-Path $repoRoot 'local.properties' | ForEach-Object { (Get-Content $_ -Raw) } | ForEach-Object { ($_ -split "\r?\n") } | Where-Object { $_ -match '^sdk.dir\s*=\s*(.+)$' } | ForEach-Object { $Matches[1].Trim() } | Select-Object -First 1
# Unescape Java properties path (e.g., C\:\\Users\\... -> C:\Users\...)
if ($sdkDir) {
  $sdkDir = $sdkDir.Replace('\:', ':').Replace('\\', '\')
}
if (-not $sdkDir) { $sdkDir = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdkDir 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdkDir 'emulator\emulator.exe'
$avdManager = Join-Path $sdkDir 'cmdline-tools\latest\bin\avdmanager.bat'

if (-not (Test-Path $adb)) { throw "adb not found at $adb" }
if (-not (Test-Path $emulatorExe)) { throw "emulator not found at $emulatorExe" }

# Pick an AVD if not provided
if (-not $AvdName) {
  $iniDir = Join-Path $env:USERPROFILE '.android\avd'
  # Strip the trailing ".avd" from directory names to get the AVD name
  $avds = Get-ChildItem -Path $iniDir -Filter *.avd -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name -replace '\.avd$','' }
  if (-not $avds) { throw "No Android Virtual Devices found. Create one via Android Studio > Device Manager." }
  $AvdName = $avds | Select-Object -First 1
} else {
  # In case caller passed a folder-like name, normalize by stripping trailing .avd
  $AvdName = ($AvdName -replace '\.avd$','')
}

Write-Header "Start emulator: $AvdName"
$emuArgs = @('-avd', $AvdName)
if ($ColdBoot) { $emuArgs += ('-no-snapshot-load','-wipe-data') }
Start-Process -FilePath $emulatorExe -ArgumentList $emuArgs -NoNewWindow | Out-Null

# Wait for device fully ready
Wait-ForDeviceReady
Start-Sleep -Seconds 3

# Optional rebuild
if ($Rebuild) {
  Write-Header 'Stop Gradle daemons'
  try { Invoke-Gradle @('--stop') } catch {}

  Write-Header 'Clean project (avoid Windows file locks)'
  try { Invoke-Gradle @('clean') } catch { Write-Warning $_ }

  Write-Header 'Assemble debug APK'
  try {
    Invoke-Gradle @(':app:assembleDebug','-x','test')
  } catch {
    Write-Warning "Initial build failed: $($_.Exception.Message)"
    Write-Header 'Retry clean and assemble'
    try { Invoke-Gradle @('clean') } catch { Write-Warning $_ }
    Invoke-Gradle @(':app:assembleDebug','-x','test')
  }
}

# Install APK (use new build_android output dir)
$apkDirCandidates = @(
  (Join-Path $repoRoot 'app\build_android\outputs\apk\debug'),
  (Join-Path $repoRoot 'app\build\outputs\apk\debug') # legacy fallback
)
$apk = $null
foreach ($dir in $apkDirCandidates) {
  if (Test-Path $dir) {
    $apk = Get-ChildItem -Path $dir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apk) { break }
  }
}
if (-not $apk) { throw "No debug APK found in build_android or build. Run a build first or pass -Rebuild." }
Write-Header "Install APK: $($apk.FullName)"

# Uninstall any existing install to avoid stale dex/class issues
Write-Header 'Uninstall old package (if present)'
& $adb uninstall 'io.github.saeargeir.skanniapp' 2>$null | Out-Null
# Try install with -g (grant all permissions). Fallback without if unsupported
Write-Header 'Install APK: grant permissions (-g)'
& $adb install -r -d -g $apk.FullName
if ($LASTEXITCODE -ne 0) {
  Write-Warning 'Install with -g failed; retrying without permission grant.'
  & $adb install -r -d $apk.FullName
  if ($LASTEXITCODE -ne 0) { throw 'APK install failed.' }
}

# Launch app
Write-Header 'Launch app'
$pkg = 'io.github.saeargeir.skanniapp'
$act = 'io.github.saeargeir.skanniapp.MainActivity'
# Verify package installed
$pkgInstalled = (& $adb shell pm list packages 2>$null | Select-String -SimpleMatch $pkg)
if (-not $pkgInstalled) { throw "Package $pkg not installed; install step likely failed." }
& $adb shell am start -n "$pkg/.$($act.Split('.')[-1])" | Out-Null

Write-Host "\nApp launched on emulator $AvdName" -ForegroundColor Green
