# 실데이터 APK 빌드용 AndroidManifest 패치 (idempotent).
# android/ 가 gitignore 라 cap add/sync 시 manifest 가 기본값(INTERNET만)으로 재생성된다.
# 이 스크립트가 빌드 전에 cleartext + 마이크/카메라 권한을 보장한다. 이미 있으면 안 바꾼다.
# (메시지는 cmd 콘솔 호환 위해 영어로 둔다)
param(
  [string]$ManifestPath = "android\app\src\main\AndroidManifest.xml"
)

if (-not (Test-Path $ManifestPath)) {
  Write-Host "[patch] manifest not found: $ManifestPath  (run 'cap add android' first)"
  exit 1
}

$full = (Resolve-Path $ManifestPath).Path
$c = [System.IO.File]::ReadAllText($full)
$changed = $false

# 1) cleartext — 외부 평문 http 백엔드 호출(예: Tailscale 100.x:8080)
if ($c -notmatch 'usesCleartextTraffic') {
  $c = $c -replace '(<application\s+android:allowBackup="true")', "`$1`r`n        android:usesCleartextTraffic=`"true`""
  $changed = $true
  Write-Host "[patch] added usesCleartextTraffic=true"
}

# 2) 음성/아바타 면접 getUserMedia 권한
foreach ($p in 'RECORD_AUDIO','CAMERA','MODIFY_AUDIO_SETTINGS') {
  if ($c -notmatch "permission\.$p") {
    $c = $c -replace '(<uses-permission android:name="android\.permission\.INTERNET" />)', "`$1`r`n    <uses-permission android:name=`"android.permission.$p`" />"
    $changed = $true
    Write-Host "[patch] added permission: $p"
  }
}

if ($changed) {
  [System.IO.File]::WriteAllText($full, $c, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "[patch] manifest updated"
} else {
  Write-Host "[patch] already applied (no change)"
}
