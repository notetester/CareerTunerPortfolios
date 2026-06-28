<#
.SYNOPSIS
  4090 절전/전원 정책 점검·설정 템플릿 — 4090 이 절전/슬립으로 Tailscale offline 되는 것을 막는다.

.DESCRIPTION
  배경: SSH self-heal(Layer 0)은 PC 가 **켜져 있어야** 동작한다. 2026-06-26 사고처럼 4090 이 절전/종료되면
  Tailscale 이 offline 되고 SSH·GPU 작업이 불가하다. 이 스크립트는 **현장 관리자 PowerShell**에서 실행해
  현재 전원 정책을 보고, (선택) 슬립/최대절전/디스플레이 타임아웃을 비활성화한다.

  ★ 기본은 **점검만(-Apply 없이 현재 상태 출력)**. 실제 변경은 `-Apply` 를 명시할 때만(현장 관리자 승인 전제).
    공용 PC 이므로 소유자 동의 없이 전원 정책을 바꾸지 말 것. 키/secret 없음.

.PARAMETER Apply
  지정 시 sleep/hibernate/monitor-timeout 을 0(안 함)으로 변경. 미지정 시 현재 상태만 출력.
.PARAMETER AcOnly
  지정 시 AC(전원 연결) 상태만 변경(배터리 모드는 건드리지 않음). 데스크톱 4090 은 보통 AC.

.EXAMPLE
  .\Set-4090PowerPolicy.ps1               # 점검만(현재 정책 출력)
  .\Set-4090PowerPolicy.ps1 -Apply        # 슬립/최대절전/모니터 타임아웃 비활성(관리자 승인 후)
#>
[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$AcOnly
)
$ErrorActionPreference = "Stop"

# 관리자 권한 확인
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host "관리자 권한: $isAdmin"
if ($Apply -and -not $isAdmin) { throw "-Apply 는 관리자 권한 PowerShell 이 필요합니다." }

function Show-PowerState([string]$when) {
    Write-Host "===== powercfg 상태 ($when) ====="
    Write-Host "[활성 전원 구성]"; powercfg /getactivescheme
    Write-Host "[슬립 타임아웃(AC/DC, 초)]";       powercfg /query SCHEME_CURRENT SUB_SLEEP STANDBYIDLE 2>$null | Select-String 'Power Setting Index|전원 설정 인덱스'
    Write-Host "[최대절전 타임아웃]";               powercfg /query SCHEME_CURRENT SUB_SLEEP HIBERNATEIDLE 2>$null | Select-String 'Power Setting Index|전원 설정 인덱스'
    Write-Host "[디스플레이 타임아웃]";              powercfg /query SCHEME_CURRENT SUB_VIDEO VIDEOIDLE 2>$null | Select-String 'Power Setting Index|전원 설정 인덱스'
    Write-Host "[wake armed devices(절전 깨우는 장치)]"; powercfg /devicequery wake_armed
}

Show-PowerState "변경 전"

if (-not $Apply) {
    Write-Host ""
    Write-Host "점검만 수행(변경 없음). 절전 방지를 적용하려면 -Apply 로 다시 실행하세요(관리자 승인 전제)."
    Write-Host "권장 변경(=-Apply): 슬립/최대절전 안 함, 모니터 타임아웃 0(헤드리스라 무관). AC 한정 권장."
    return
}

Write-Host "===== 절전 방지 적용 ====="
# AC(전원 연결): 슬립/최대절전/모니터 타임아웃 = 0(안 함)
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0
powercfg /change monitor-timeout-ac 0
if (-not $AcOnly) {
    powercfg /change standby-timeout-dc 0
    powercfg /change hibernate-timeout-dc 0
    powercfg /change monitor-timeout-dc 0
}
# (선택) 최대절전 자체 비활성 — 디스크 절약·예기치 않은 hibernate 방지. 필요 시 주석 해제.
# powercfg /hibernate off

Write-Host ""
Show-PowerState "변경 후"
Write-Host ""
Write-Host "완료. 검증: 노트북에서 Tailscale 의 chanssick 이 Connected 유지되는지 + ssh ... echo ok."
Write-Host "주의: 공용 PC 전원 정책 변경은 소유자 동의가 필요합니다. Wake-on-LAN 은 BIOS/NIC 설정이라 별도(runbook 참조)."
