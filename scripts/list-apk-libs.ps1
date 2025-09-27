param(
  [string]$Apk
)

$ErrorActionPreference = 'Stop'

if (-not $Apk) {
  $dir = Join-Path $PSScriptRoot '..\app\build_android\outputs\apk\debug'
  if (-not (Test-Path $dir)) { throw "APK directory not found: $dir" }
  $item = Get-ChildItem -Path $dir -Filter *.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $item) { throw 'No APK found' }
  $Apk = $item.FullName
}

Write-Host "APK: $Apk" -ForegroundColor Cyan

Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
  $entries = $zip.Entries | Where-Object { $_.FullName -like 'lib/*/*.so' } | Select-Object -ExpandProperty FullName
  if (-not $entries) { Write-Host '(no native .so files found)' ; exit 0 }
  $entries | Sort-Object
} finally {
  $zip.Dispose()
}
