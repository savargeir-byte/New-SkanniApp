param(
  [int]$Port = 8090,
  [string]$Folder = "C:\\Users\\Computer\\Documents\\GitHub\\SkanniApp\\app\\build_android3\\outputs\\apk\\debug"
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Folder)) { $Folder = "C:\\Users\\Computer\\Documents\\GitHub\\SkanniApp\\app\\build\\outputs\\apk\\debug" }
if (-not (Test-Path $Folder)) { throw "APK folder not found: $Folder" }

Write-Host "Serving $Folder on http://localhost:$Port/ (Ctrl+C to stop)" -ForegroundColor Green

$listener = New-Object System.Net.HttpListener
$prefix = "http://+:$Port/"
$listener.Prefixes.Add($prefix)
$listener.Start()

try {
  while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $path = $ctx.Request.Url.AbsolutePath.TrimStart('/')
    if ([string]::IsNullOrWhiteSpace($path)) { $path = 'app-debug.apk' }
    $file = Join-Path $Folder $path
    if (Test-Path $file) {
      $bytes = [System.IO.File]::ReadAllBytes($file)
      $ctx.Response.ContentType = if ($file.ToLower().EndsWith('.apk')) { 'application/vnd.android.package-archive' } else { 'application/octet-stream' }
      $ctx.Response.ContentLength64 = $bytes.Length
      $ctx.Response.AddHeader('Content-Disposition', "attachment; filename='" + [IO.Path]::GetFileName($file) + "'")
      $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
      $ctx.Response.OutputStream.Close()
    } else {
      $ctx.Response.StatusCode = 404
      $writer = New-Object System.IO.StreamWriter($ctx.Response.OutputStream)
      $writer.Write("Not Found")
      $writer.Flush(); $ctx.Response.OutputStream.Close()
    }
  }
}
finally {
  $listener.Stop()
  $listener.Close()
}