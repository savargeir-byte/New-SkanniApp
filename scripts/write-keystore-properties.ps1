<#
Creates keystore.properties from .secrets/android-signing.env for local builds.
This file is gitignored. Do NOT commit it.
#>

[CmdletBinding()]
param(
  [string]$EnvFile = ".secrets/android-signing.env",
  [string]$KeystoreProps = "keystore.properties",
  [string]$StoreFile = "upload-keystore.jks"
)

if (!(Test-Path $EnvFile)) { Write-Error "Env file not found: $EnvFile"; exit 1 }

# Parse simple KEY=VALUE lines
$map = @{}
Get-Content $EnvFile | ForEach-Object {
  if ($_ -match '^(?<k>[A-Z0-9_]+)=(?<v>.*)$') {
    $map[$Matches.k] = $Matches.v
  }
}

$required = @('ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')
foreach ($k in $required) {
  if (-not $map.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($map[$k])) {
    Write-Error "Missing $k in $EnvFile"; exit 1
  }
}

@(
  "storeFile=$StoreFile",
  "storePassword=$($map['ANDROID_KEYSTORE_PASSWORD'])",
  "keyAlias=$($map['ANDROID_KEY_ALIAS'])",
  "keyPassword=$($map['ANDROID_KEY_PASSWORD'])"
) | Set-Content -Path $KeystoreProps -Encoding UTF8

Write-Host "Wrote $KeystoreProps for local release signing."