<#
  Build SkanniApp debug APK with JDK 21 on Windows
  - Detect a local JDK 21 (Temurin/Microsoft/Oracle) or install Temurin 21 via winget
  - Set JAVA_HOME for this session
  - Bootstrap Android SDK cmdline-tools if missing
  - Accept licenses and install platform-tools, platforms;android-34, build-tools;35.0.0
  - Stop Gradle daemons and clean build dirs to avoid Windows file locks
  - Run Gradle clean + :app:assembleDebug (forces org.gradle.java.home=JAVA_HOME)
  - Save verbose output to ./gradle-build.log and print APK info on success (supports custom build dir build_android3)
#>

param(
  [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'

function Write-Header($text) {
  Write-Host "`n=== $text ===" -ForegroundColor Cyan
}

function Get-RepoRoot {
  $scriptDir = Split-Path -Parent $PSCommandPath
  $repoRoot = Split-Path -Parent $scriptDir
  if (-not (Test-Path (Join-Path $repoRoot 'gradlew.bat'))) {
    $cwd = (Get-Location).Path
    if (Test-Path (Join-Path $cwd 'gradlew.bat')) { $repoRoot = $cwd }
    else { throw "Could not find gradlew.bat. Run this script from the repo root or scripts folder." }
  }
  return $repoRoot
}

function Get-SdkDir($repoRoot) {
  $lp = Join-Path $repoRoot 'local.properties'
  if (Test-Path $lp) {
    $content = Get-Content $lp -Raw
    $m = [regex]::Match($content, 'sdk.dir\s*=\s*(.+)')
    if ($m.Success) {
      $val = $m.Groups[1].Value.Trim()
      # Unescape Java properties: C\:\\Users -> C:\Users
      $val = $val -replace '\\:', ':'
      $val = $val -replace '\\\\', '\\'
      return $val
    }
  }
  return (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
}

function Ensure-AndroidSdk($repoRoot) {
  Write-Header "Check Android SDK"
  $sdk = Get-SdkDir $repoRoot
  Write-Host "SDK dir: $sdk"
  if (-not (Test-Path $sdk)) { New-Item -ItemType Directory -Path $sdk -Force | Out-Null }

  $cmdlineRoot = Join-Path $sdk 'cmdline-tools'
  $cmdlineLatest = Join-Path $cmdlineRoot 'latest'
  $sdkmgr = Join-Path $cmdlineLatest 'bin\sdkmanager.bat'
  $buildToolsRoot = Join-Path $sdk 'build-tools'

  if (-not (Test-Path $sdkmgr)) {
    # If required components already exist, proceed without cmdline-tools
  $hasPlatform34 = Test-Path (Join-Path $sdk 'platforms\android-34')
  $hasAnyBuildTools = (Test-Path $buildToolsRoot) -and ((Get-ChildItem -Path $buildToolsRoot -Directory -ErrorAction SilentlyContinue).Count -gt 0)
    if ($hasPlatform34 -and $hasAnyBuildTools) {
      Write-Warning "cmdline-tools/sdkmanager not found, but platform 34 and build-tools are present. Proceeding without installing cmdline-tools."
      $env:ANDROID_SDK_ROOT = $sdk
      $env:ANDROID_HOME = $sdk
      return $sdk
    }
    Write-Header "Install Android cmdline-tools"
    New-Item -ItemType Directory -Path $cmdlineLatest -Force | Out-Null
    $tmpDir = Join-Path $env:TEMP ("android-cmdline-tools-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

    $urls = @(
      'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip',
      'https://dl.google.com/android/repository/commandlinetools-win-10406996_latest.zip',
      'https://dl.google.com/android/repository/commandlinetools-win-9123335_latest.zip'
    )
    $zipPath = Join-Path $tmpDir 'cmdline-tools.zip'
    $downloaded = $false
    try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
    foreach ($u in $urls) {
      try {
        Write-Host "Downloading cmdline-tools from $u ..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $u -OutFile $zipPath -UseBasicParsing -TimeoutSec 300 -ErrorAction Stop
        $downloaded = $true; break
      } catch { Write-Warning "Download failed from $u. Trying next..." }
    }
    if (-not $downloaded) { Write-Warning "cmdline-tools download failed"; }

    try {
      Write-Host "Extracting cmdline-tools ..."
      Expand-Archive -Path $zipPath -DestinationPath $tmpDir -Force -ErrorAction Stop
      $unpacked = Join-Path $tmpDir 'cmdline-tools'
      if (-not (Test-Path $unpacked)) { Write-Warning "Unexpected archive layout: 'cmdline-tools' folder not found" }
      if (Test-Path $cmdlineLatest) { try { Remove-Item -Path $cmdlineLatest -Recurse -Force -ErrorAction SilentlyContinue } catch {} }
      New-Item -ItemType Directory -Path $cmdlineLatest -Force | Out-Null
      if (Test-Path $unpacked) { Copy-Item -Path (Join-Path $unpacked '*') -Destination $cmdlineLatest -Recurse -Force }
    } catch {
      throw "Failed to extract/move cmdline-tools: $_"
    } finally {
      try { Remove-Item -Path $tmpDir -Recurse -Force -ErrorAction SilentlyContinue } catch {}
    }
    $sdkmgr = Join-Path $cmdlineLatest 'bin\sdkmanager.bat'
    if (-not (Test-Path $sdkmgr)) {
      Write-Warning "sdkmanager not found after attempted installation. Will proceed if required components are present."
  $hasPlatform34 = Test-Path (Join-Path $sdk 'platforms\android-34')
  $hasAnyBuildTools = (Test-Path $buildToolsRoot) -and ((Get-ChildItem -Path $buildToolsRoot -Directory -ErrorAction SilentlyContinue).Count -gt 0)
      if ($hasPlatform34 -and $hasAnyBuildTools) {
        $env:ANDROID_SDK_ROOT = $sdk
        $env:ANDROID_HOME = $sdk
        return $sdk
      } else {
        throw "cmdline-tools (sdkmanager) missing and required SDK components not found. Please install via Android Studio SDK Manager."
      }
    }
  }

  # Accept licenses and ensure required packages
  $platform34 = Join-Path $sdk 'platforms\android-34'
  # Match module buildToolsVersion in app/build.gradle(.kts)
  $buildTools = Join-Path $sdk 'build-tools\35.0.0'
  if ((-not (Test-Path $platform34)) -or (-not (Test-Path $buildTools))) {
    if (-not (Test-Path $sdkmgr)) {
      Write-Warning "sdkmanager not available to install missing components. Attempting to proceed with existing SDK; build may still succeed if another build-tools version is present."
      $env:ANDROID_SDK_ROOT = $sdk
      $env:ANDROID_HOME = $sdk
      return $sdk
    }
    Write-Host "Accepting Android SDK licenses ..." -ForegroundColor Yellow
    try {
      $yes = ("y`n" * 200)
      $p = Start-Process -FilePath $sdkmgr -ArgumentList '--licenses' -NoNewWindow -PassThru -RedirectStandardInput 'cmd'
      $p.StandardInput.Write($yes); $p.StandardInput.Close(); $p.WaitForExit()
    } catch { Write-Warning "License acceptance issue: $_" }

    Write-Host "Installing required SDK components ..." -ForegroundColor Yellow
    try { & $sdkmgr --install 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' } catch {
      # Retry with 35.0.0 if 34.0.0 fails or vice-versa
      try { & $sdkmgr --install 'build-tools;35.0.0' } catch {}
      Write-Warning "sdkmanager failed to install some packages. Use Android Studio > SDK Manager to install Android 34 + Build-Tools 35.0.0."
      throw "Required SDK components missing"
    }
  }

  $env:ANDROID_SDK_ROOT = $sdk
  $env:ANDROID_HOME = $sdk
  return $sdk
}

function Find-Jdk21 {
  $candidates = @(
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21*',
    'C:\\Program Files\\Microsoft\\jdk-21*',
    'C:\\Program Files\\Java\\jdk-21*',
    "$env:USERPROFILE\\AppData\\Local\\Programs\\Eclipse Adoptium\\jdk-21*",
    "$env:LOCALAPPDATA\\Programs\\Microsoft\\jdk-21*"
  )
  foreach ($p in $candidates) {
    $dirs = Get-ChildItem -Path $p -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    foreach ($d in $dirs) { if (Test-Path (Join-Path $d.FullName 'bin\\javac.exe')) { return $d.FullName } }
  }
  return $null
}

# Main
Write-Header 'Locate repository root'
$repoRoot = Get-RepoRoot
Set-Location $repoRoot
Write-Host "Repo: $repoRoot"

Write-Header 'Check Android Studio JBR'
$jbr = 'C:\\Program Files\\Android\\Android Studio\\jbr\\bin\\java.exe'
if (Test-Path $jbr) { try { & $jbr -version 2>&1 | Write-Host } catch { Write-Host '(Unable to run JBR java -version)' } }
else { Write-Host "Android Studio JBR not found (that's fine)." }

Write-Header 'Find JDK 21'
$jdk = Find-Jdk21
if (-not $jdk) {
  if ($NoInstall) { throw 'JDK 21 not found and --NoInstall specified. Install JDK 21 and re-run.' }
  Write-Host 'No local JDK 21 found. Installing Temurin 21 via winget...' -ForegroundColor Yellow
  try { winget install --id EclipseAdoptium.Temurin.21.JDK --silent --accept-source-agreements --accept-package-agreements | Out-Null } catch { Write-Warning 'winget install failed. Install JDK 21 manually and re-run.' }
  $jdk = Find-Jdk21
}
if (-not $jdk) { throw 'JDK 21 not found. Please install JDK 21 and re-run.' }

$env:JAVA_HOME = $jdk
Write-Host "Using JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Green

Ensure-AndroidSdk $repoRoot | Out-Null

Write-Header 'Run Gradle build (clean + assembleDebug)'
$gradleLog = Join-Path $repoRoot 'gradle-build.log'
try {
  # Proactively stop any running daemons and clean previous outputs to avoid locked R.jar on Windows
  try { & .\gradlew.bat --stop | Out-Null } catch {}
  # Clean both default and custom build directories
  $appDir = Join-Path $repoRoot 'app'
  @('build', 'build_android3') | ForEach-Object {
    $bd = Join-Path $appDir $_
    if (Test-Path $bd) {
      Write-Host "Removing $bd ..." -ForegroundColor Yellow
  try { Remove-Item -Path $bd -Recurse -Force -ErrorAction Stop } catch { Write-Warning "Failed to delete ${bd}: $_" }
    }
  }

  & .\gradlew.bat -Dorg.gradle.java.home="$env:JAVA_HOME" --no-daemon --stacktrace --info clean :app:assembleDebug --rerun-tasks *>&1 | Tee-Object -FilePath $gradleLog
} catch {
  Write-Error "Gradle invocation failed: $_"; exit 1
}

Write-Header 'APK output'

# Support custom build directory (build_android3) first, then fallback to default
$apkDirs = @(
  (Join-Path $repoRoot 'app\build_android3\outputs\apk\debug'),
  (Join-Path $repoRoot 'app\build\outputs\apk\debug')
)
$found = $false
foreach ($dir in $apkDirs) {
  if (Test-Path $dir) {
    $found = $true
    Write-Host "Scanning APKs in $dir" -ForegroundColor Cyan
    Get-ChildItem -Path $dir -File | Sort-Object LastWriteTime -Descending | Select-Object Name,LastWriteTime,Length | Format-Table -AutoSize
    try { Start-Process $dir } catch {}
    break
  }
}
if (-not $found) { throw "APK output folder not found in: $($apkDirs -join ', ')" }

Write-Host "`nDone. If the build failed, share the tail of $gradleLog for help." -ForegroundColor Green
