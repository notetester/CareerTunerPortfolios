# iOS Info.plist 패치 (idempotent).
# ios/ 가 gitignore 라 'npx cap add ios' 시 Info.plist 가 기본값으로 생성된다.
# 이 스크립트가 빌드 전에 스토어 심사 필수 usage description 키를 보장한다. 이미 있으면 안 바꾼다.
# 실행 시점: 'npx cap add ios' 직후 1회 + 'npx cap sync ios' 로 plist 가 재생성됐을 수 있는 빌드 전마다.
# (메시지는 cmd 콘솔 호환 위해 영어로 둔다)
param(
  [string]$PlistPath = "ios\App\App\Info.plist"
)

if (-not (Test-Path $PlistPath)) {
  Write-Host "[patch] Info.plist not found: $PlistPath  (run 'npx cap add ios' first)"
  exit 1
}

$full = (Resolve-Path $PlistPath).Path
$c = [System.IO.File]::ReadAllText($full)
$changed = $false

# 스토어 심사 필수 usage description — 키가 이미 있으면 건너뛴다(멱등).
$keys = [ordered]@{
  'NSCameraUsageDescription'          = '화상 면접에서 표정·자세 분석과 공고·이력서 촬영 등록에 카메라를 사용합니다. 원본 영상은 채점 후 즉시 폐기됩니다.'
  'NSMicrophoneUsageDescription'      = '음성 면접 답변 녹음과 전달력 채점에 마이크를 사용합니다. 원본 음성은 채점 후 즉시 폐기됩니다.'
  'NSPhotoLibraryUsageDescription'    = '공고·이력서 사진을 선택해 지원 건으로 등록할 때 사진 보관함에 접근합니다.'
  'NSPhotoLibraryAddUsageDescription' = '촬영한 공고 사진을 저장할 때 사용합니다.'
}

foreach ($k in $keys.Keys) {
  if ($c.Contains("<key>$k</key>")) {
    continue
  }
  # 최상위 <dict> 의 마지막 닫힘 직전에 삽입 (plist 는 단일 루트 dict)
  $idx = $c.LastIndexOf('</dict>')
  if ($idx -lt 0) {
    Write-Host "[patch] malformed plist (no </dict>): $full"
    exit 1
  }
  $entry = "`t<key>$k</key>`r`n`t<string>$($keys[$k])</string>`r`n"
  $c = $c.Insert($idx, $entry)
  $changed = $true
  Write-Host "[patch] added key: $k"
}

if ($changed) {
  [System.IO.File]::WriteAllText($full, $c, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "[patch] Info.plist updated"
} else {
  Write-Host "[patch] already applied (no change)"
}
