<#
.SYNOPSIS
  Generate a new Android upload keystore and base64 encode it for CI secrets.

.DESCRIPTION
  - Backs up any existing upload-keystore.jks and .b64 files with a timestamp.
  - Prompts for or generates strong random passwords and alias.
  - Uses keytool to generate a JKS keystore suitable for Play Console upload signing.
  - Base64 encodes the JKS for storage in GitHub Secrets.
  - Writes a local .secrets/android-signing.env with the values to copy.

.NOTES
  Run in PowerShell (pwsh). Keytool must be available (included with JDK).
  This script does NOT commit anything. Keep .secrets out of source control.

.OUTPUTS
  - upload-keystore.jks
  - upload-keystore.jks.b64
  - .secrets/android-signing.env (gitignored)
#>

[CmdletBinding()]
param(
  [string]$KeystorePath = "upload-keystore.jks",
  [string]$B64Path = "upload-keystore.jks.b64",
  [string]$Alias,
  [string]$StorePass,
  [string]$KeyPass,
  [string]$DName = "CN=SkanniApp, OU=Engineering, O=Skanni, L=Reykjavik, S=Capital, C=IS",
  [int]$ValidityDays = 3650
)

function New-RandomSecret {
  param([int]$Length = 24)
  $bytes = New-Object byte[] $Length
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  # URL-safe base64 without padding
  $s = [Convert]::ToBase64String($bytes).TrimEnd('=')
  $s = $s -replace '\+', '-' -replace '/', '_'
  return $s
}

function Backup-IfExists {
  param([string]$Path)
  if (Test-Path $Path) {
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $bk = "$Path.$ts.bak"
    Write-Host "Backing up $Path -> $bk"
    Move-Item -Force -Path $Path -Destination $bk
  }
}

# Ensure keytool exists
$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) {
  Write-Error "keytool not found. Install a JDK and ensure keytool is on PATH."
  exit 1
}

# Generate defaults if not provided
if (-not $Alias) { $Alias = "upload-" + (New-RandomSecret 8) }
if (-not $StorePass) { $StorePass = New-RandomSecret 32 }
if (-not $KeyPass) { $KeyPass = New-RandomSecret 32 }

Write-Host "Using alias: $Alias"

# Backup existing files
Backup-IfExists -Path $KeystorePath
Backup-IfExists -Path $B64Path

# Generate keystore
Write-Host "Generating JKS keystore..."
& keytool -genkeypair `
  -v `
  -keystore $KeystorePath `
  -storetype JKS `
  -storepass $StorePass `
  -keypass $KeyPass `
  -alias $Alias `
  -keyalg RSA `
  -keysize 2048 `
  -validity $ValidityDays `
  -dname $DName

if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeystorePath)) {
  Write-Error "Failed to generate keystore."
  exit 1
}

# Base64 encode (no newline)
Write-Host "Encoding keystore to Base64..."
$bytes = [IO.File]::ReadAllBytes($KeystorePath)
$base64 = [Convert]::ToBase64String($bytes)
[IO.File]::WriteAllText($B64Path, $base64)

# Create local secrets file
$secretsDir = ".secrets"
if (-not (Test-Path $secretsDir)) { New-Item -ItemType Directory -Path $secretsDir | Out-Null }
$envPath = Join-Path $secretsDir "android-signing.env"
@(
  "# Copy these values into your GitHub repository Secrets",
  "ANDROID_KEYSTORE_BASE64=$base64",
  "ANDROID_KEYSTORE_PASSWORD=$StorePass",
  "ANDROID_KEY_ALIAS=$Alias",
  "ANDROID_KEY_PASSWORD=$KeyPass"
) | Set-Content -Path $envPath -Encoding UTF8

Write-Host "Done. Files created:"
Write-Host " - $KeystorePath"
Write-Host " - $B64Path"
Write-Host " - $envPath (gitignored)"

Write-Host "Next steps:" -ForegroundColor Green
Write-Host " 1) Open GitHub -> Settings -> Secrets and variables -> Actions -> New repository secret"
Write-Host " 2) Add the four secrets with the values from .secrets/android-signing.env"
Write-Host " 3) Re-run the 'Build and Release Signed APK' workflow for tag v1.0.14"
