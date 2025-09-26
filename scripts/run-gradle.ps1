param(
  [string]$JavaHome,
  [string]$Log,
  [Parameter(ValueFromRemainingArguments=$true)]
  [string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'

# Resolve repo root (one level up from scripts)
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# Prefer installed JDK 17 used by our build script, fall back to common path
function Get-JavaVersion($javaExe) {
  try {
    $out = & $javaExe -version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { return $null }
    if ($out -match 'version "(\d+)(?:\.(\d+))?') { return [int]$Matches[1] } else { return $null }
  } catch { return $null }
}

function Find-Jdk17 {
  $candidates = @(
    # Android Studio bundled JBR (Electric Eel+ typical path)
    'C:\\Program Files\\Android\\Android Studio\\jbr',
    'C:\\Program Files\\Android\\Android Studio\\jre',
    'C:\\Users\\Computer\\AppData\\Local\\Programs\\Android Studio\\jbr',
    # Common vendor installs
    'C:\\Program Files\\Eclipse Adoptium\\jdk-17*',
    'C:\\Program Files\\Microsoft\\jdk-17*',
    'C:\\Program Files\\Java\\jdk-17*'
  )
  foreach ($p in $candidates) {
    Get-ChildItem -Path $p -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object {
      $javaExe = Join-Path $_.FullName 'bin\\java.exe'
      if (Test-Path $javaExe) {
        $ver = Get-JavaVersion $javaExe
        if ($ver -eq 17) { return $_.FullName }
      }
    }
    # Direct path (if exact folder, not wildcard)
    if ((Test-Path $p) -and (Test-Path (Join-Path $p 'bin\\java.exe'))) {
      $ver = Get-JavaVersion (Join-Path $p 'bin\\java.exe')
      if ($ver -eq 17) { return $p }
    }
  }
  # Try JAVA_HOME if set
  if ($env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\\java.exe'
    if (Test-Path $javaExe) {
      $ver = Get-JavaVersion $javaExe
      if ($ver -eq 17) { return $env:JAVA_HOME }
    }
  }
  # Try PATH java
  $javaCmdObj = Get-Command java -ErrorAction SilentlyContinue
  if ($javaCmdObj) {
    $javaPath = $javaCmdObj.Path
    if (-not $javaPath) { $javaPath = $javaCmdObj.Source }
    if (-not $javaPath) { $javaPath = $javaCmdObj.Definition }
    # If it's a shim or symlink, still attempt version detection
    $ver = Get-JavaVersion $javaPath
    if ($ver -eq 17) {
      # If it's under \bin, strip to JAVA_HOME; otherwise fallback to env var
      $binDir = Split-Path $javaPath -Parent
      $maybeHome = Split-Path $binDir -Parent
      if (Test-Path (Join-Path $maybeHome 'bin')) { return $maybeHome }
      # As a last resort, return the bin parent if it looks like a JDK
      return $maybeHome
    }
  }
  # Fallback to where.exe (in case Get-Command doesn't resolve applications)
  try {
    $whereOut = & where.exe java 2>$null
    if ($whereOut) {
      $first = ($whereOut | Select-Object -First 1).Trim()
      if ($first) {
        $ver = Get-JavaVersion $first
        if ($ver -eq 17) {
          $binDir = Split-Path $first -Parent
          $maybeHome = Split-Path $binDir -Parent
          if (Test-Path (Join-Path $maybeHome 'bin')) { return $maybeHome }
          return $maybeHome
        }
      }
    }
  } catch { }
  return $null
}

$jdk = $null
if ($JavaHome) {
  $javaExe = Join-Path $JavaHome 'bin\\java.exe'
  if (Test-Path $javaExe) {
    $ver = Get-JavaVersion $javaExe
    if ($ver -eq 17) {
      $jdk = $JavaHome
    } else {
      Write-Host "Provided -JavaHome java -version did not report 17 (continuing anyway): $JavaHome" -ForegroundColor Yellow
      $jdk = $JavaHome
    }
  } else {
    Write-Host "Provided -JavaHome has no bin\\java.exe (continuing anyway): $JavaHome" -ForegroundColor Yellow
    $jdk = $JavaHome
  }
}
if ($jdk) {
  $env:JAVA_HOME = $jdk
  if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;" + $env:Path }
  Write-Host "Using JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
  if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin'))) {
    Write-Host "Warning: JAVA_HOME/bin not found. Build may still work if Gradle locates Java via PATH." -ForegroundColor Yellow
  }
} else {
  $jdk = Find-Jdk17
  if ($jdk) {
    $env:JAVA_HOME = $jdk
    Write-Host "Using JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin'))) {
      Write-Host "Warning: JAVA_HOME/bin not found. Build may still work if Gradle locates Java via PATH." -ForegroundColor Yellow
    }
  } else {
  # Try PATH Java directly
  Write-Host "PATH = $env:PATH" -ForegroundColor DarkGray
  try {
    $pv = & java -version 2>&1 | Out-String
  } catch { $pv = '' }
  if ($pv -match 'version \"(17)') {
    Write-Host "Using PATH Java 17 (java -version detected)" -ForegroundColor Cyan
  } else {
    # As a secondary attempt, resolve the java path with Get-Command/where and check again
    $javaCmdObj = Get-Command java -ErrorAction SilentlyContinue
    $javaPath = $null
    if ($javaCmdObj) { $javaPath = $javaCmdObj.Path; if (-not $javaPath) { $javaPath = $javaCmdObj.Source }; if (-not $javaPath) { $javaPath = $javaCmdObj.Definition } }
    if (-not $javaPath) {
      try { $javaPath = (& where.exe java 2>$null | Select-Object -First 1).Trim() } catch { $javaPath = $null }
    }
    if ($javaPath) {
      $ver = Get-JavaVersion $javaPath
      if ($ver -eq 17) {
        Write-Host "Using PATH Java 17 at $javaPath" -ForegroundColor Cyan
      } else {
        Write-Host 'Could not find a Java 17 installation automatically.' -ForegroundColor Yellow
        Write-Host 'Please install JDK 17 (Temurin or Microsoft) or set JAVA_HOME to a valid JDK 17 and re-run.' -ForegroundColor Yellow
        exit 1
      }
    } else {
      Write-Host 'Could not find a Java installation on PATH.' -ForegroundColor Yellow
      Write-Host 'Please install JDK 17 (Temurin or Microsoft) or set JAVA_HOME to a valid JDK 17 and re-run.' -ForegroundColor Yellow
      exit 1
    }
  }
}
}

$gradle = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path $gradle)) { throw "gradlew.bat not found at $gradle" }

$argsList = @('--no-daemon','--stacktrace','--info') + $GradleArgs
if ($Log) {
  Write-Host "Logging Gradle output to $Log" -ForegroundColor DarkGray
  $prevEap = $ErrorActionPreference
  $ErrorActionPreference = 'SilentlyContinue'
  & $gradle @argsList 2>&1 | Tee-Object -FilePath $Log
  $code = $LASTEXITCODE
  $ErrorActionPreference = $prevEap
} else {
  $prevEap = $ErrorActionPreference
  $ErrorActionPreference = 'SilentlyContinue'
  & $gradle @argsList
  $code = $LASTEXITCODE
  $ErrorActionPreference = $prevEap
}

if ($code -ne 0) {
  Write-Host "Gradle exited with code $code" -ForegroundColor Red
  exit $code
}

# On success, try to locate and print APK path(s)
$apkDir = Join-Path $repoRoot 'app\build\outputs\apk\debug'
if (Test-Path $apkDir) {
  $apks = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue
  if ($apks) {
    Write-Host "APK built:" -ForegroundColor Green
    $apks | ForEach-Object { Write-Host " - $($_.FullName)" -ForegroundColor Green }
  } else {
    Write-Host "Build succeeded but no APK found in $apkDir yet. Tasks may still be running or outputs differ." -ForegroundColor Yellow
  }
} else {
  Write-Host "Build succeeded but APK directory not found: $apkDir" -ForegroundColor Yellow
}