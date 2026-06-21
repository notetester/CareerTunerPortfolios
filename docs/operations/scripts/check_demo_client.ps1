<#
  시연/개발 PC 연결 점검 스크립트 (읽기 전용)
  - 공용 4090 Ollama에 붙을 PC에서 실행. 아무것도 변경하지 않는다(점검만).
  - ★ VPN(메시 VPN) 설치/로그인/접근 권한 수락은 사람이 직접 해야 한다. 이 스크립트는 대신하지 않는다.
  사용: powershell -ExecutionPolicy Bypass -File check_demo_client.ps1 [-OssHost localhost]
#>
param(
  [string]$OssHost = "localhost",
  [int]$Port = 11434,
  [string]$Model = "careertuner-c-career-strategy-3b"
)
$base = "http://${OssHost}:${Port}/v1"
$fail = @()
Write-Output "===== 시연 PC 점검 (읽기 전용) — target=$base ====="

# 1) VPN(Tailscale) 설치/상태
$ts = "C:\Program Files\Tailscale\tailscale.exe"
if (Test-Path $ts) {
  Write-Output "[OK] Tailscale 설치됨"
  & $ts status 2>&1 | Select-Object -First 6
} else {
  Write-Output "[!] Tailscale 미설치 — 사람이 직접 설치/로그인 필요"
  $fail += "VPN 클라이언트 미설치"
}

# 2) 공용 서버 /v1/models 도달
try {
  $m = Invoke-RestMethod -Uri "$base/models" -TimeoutSec 10
  $ids = ($m.data | ForEach-Object { $_.id }) -join ", "
  Write-Output "[OK] $base/models 응답: $ids"
  if ($ids -notmatch [regex]::Escape($Model)) { $fail += "모델 $Model 미표시" }
} catch {
  Write-Output ("[FAIL] $base/models 도달 실패: " + $_.Exception.Message)
  $fail += "공용 서버 도달 실패 (VPN 권한/연결 확인)"
}

# 3) 환경변수 안내 (변경하지 않음, 안내만)
Write-Output "--- 백엔드 표준 환경변수(설정 안내) ---"
Write-Output "  CAREERTUNER_ANALYSIS_AI_PROVIDER=oss"
Write-Output "  CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL=$base"
Write-Output "  CAREERTUNER_ANALYSIS_AI_OSS_MODEL=$Model"

# 4) 결과 / 체크리스트
Write-Output "===== 결과 ====="
if ($fail.Count -eq 0) {
  Write-Output "[PASS] 연결 점검 통과. 백엔드에 위 환경변수 적용 후 case 2 E2E 확인."
} else {
  Write-Output "[CHECK] 실패 항목:"; $fail | ForEach-Object { Write-Output "  - $_" }
  Write-Output "체크리스트: (1)VPN 로그인/접근권한 수락 했나 (2)tailscale status에 4090 보이나 (3)방화벽/네트워크"
}
