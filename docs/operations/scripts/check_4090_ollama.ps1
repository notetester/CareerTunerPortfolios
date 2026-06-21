<#
  공용 4090 Ollama 서버 상태 점검 스크립트 (읽기 전용)
  - 4090 PC에서 실행. 환경변수/방화벽/Ollama 상태를 "확인만" 한다. 아무것도 변경하지 않는다.
  - 변경(OLLAMA_HOST 설정·방화벽 규칙 추가 등)은 이 스크립트가 하지 않는다. 별도 실행 절차로 처리.
  - 일부 조회는 관리자 권한이 필요할 수 있다(권한 없으면 해당 항목만 건너뜀).
  사용: powershell -ExecutionPolicy Bypass -File check_4090_ollama.ps1 [-TailnetIp localhost]
#>
param(
  [string]$TailnetIp = "localhost",
  [int]$Port = 11434,
  [string]$Model = "careertuner-c-career-strategy-3b"
)
Write-Output "===== 4090 Ollama 점검 (읽기 전용) ====="

# 1) OLLAMA_HOST 환경변수 (현재 세션/Machine 표시만)
Write-Output ("[env] 세션 OLLAMA_HOST = " + ($env:OLLAMA_HOST))
try { Write-Output ("[env] Machine OLLAMA_HOST = " + [Environment]::GetEnvironmentVariable("OLLAMA_HOST","Machine")) } catch {}

# 2) 11434 LISTENING
Write-Output "--- $Port LISTENING ---"
try { Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object LocalAddress,LocalPort,State | Format-Table | Out-String } catch { Write-Output "  (리스너 없음 또는 조회 불가)" }

# 3) localhost /v1/models
try { $l=(Invoke-RestMethod -Uri "http://localhost:$Port/v1/models" -TimeoutSec 8).data.id -join ", "; Write-Output "[OK] localhost: $l" } catch { Write-Output ("[FAIL] localhost /v1/models: " + $_.Exception.Message) }

# 4) tailnet IP /v1/models
try { $t=(Invoke-RestMethod -Uri "http://${TailnetIp}:$Port/v1/models" -TimeoutSec 8).data.id -join ", "; Write-Output "[OK] ${TailnetIp}: $t" } catch { Write-Output ("[FAIL] ${TailnetIp} /v1/models: " + $_.Exception.Message) }

# 5) 모델 존재
try { if (((Invoke-RestMethod -Uri "http://localhost:$Port/v1/models" -TimeoutSec 8).data.id) -match [regex]::Escape($Model)) { Write-Output "[OK] 모델 $Model 존재" } else { Write-Output "[!] 모델 $Model 미표시" } } catch {}

# 6) 방화벽 11434 규칙 (읽기)
Write-Output "--- 방화벽 11434 규칙(읽기) ---"
try {
  Get-NetFirewallPortFilter -ErrorAction Stop | Where-Object { $_.LocalPort -eq "$Port" } |
    ForEach-Object { ($_ | Get-NetFirewallRule -ErrorAction SilentlyContinue) } |
    Select-Object DisplayName, Direction, Action, Enabled | Format-Table | Out-String
} catch { Write-Output "  (방화벽 규칙 조회 실패 — 관리자 권한 필요할 수 있음)" }

Write-Output "===== 끝 (변경 없음) ====="
