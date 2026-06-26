<#
.SYNOPSIS
  Layer 0 — 4090 Windows OpenSSH self-healing 설치 템플릿(키 없음 · placeholder 만 받음).

.DESCRIPTION
  4090(Windows, 관리자 PowerShell)에서 **현장 1회 실행**. 재부팅으로 authorized_keys/ACL/서비스가 무효화돼
  SSH publickey 가 거부되던 사고(2026-06-26)를 막기 위해, 부팅마다 SSH 설정을 재적용하는 self-heal 작업을 건다.

  ★ 이 스크립트는 **repo 커밋용 템플릿**이라 키를 내장하지 않는다. 공개키는 **파라미터로** 받는다.
    (private key·token·auth key 는 절대 인자/커밋 금지. 공개키만.)

  하는 일(멱등):
    1) OpenSSH Server 설치 보장 + sshd StartupType Automatic + 기동
    2) administrators_authorized_keys 작성(FullSessionPubKey plain + (선택)TriggerPubKey forced-command) + ACL 교정
    3) sshd_config: PubkeyAuthentication yes / PasswordAuthentication no / PermitEmptyPasswords no / AuthorizedKeysFile 확인
    4) 방화벽 22 = Tailscale 대역(기본 100.64.0.0/10) 한정
    5) Tailscale 서비스 StartupType Automatic 확인
    6) 위 전부를 재적용하는 self-heal 스크립트를 OpsDir 에 생성 + **OnStart + 매시간 SYSTEM 스케줄 작업** 등록
    7) 즉시 1회 self-heal 실행
  로그: $LogDir\ssh-self-heal.log

.EXAMPLE
  .\Install-4090OpenSshSelfHeal.ps1 `
     -FullSessionPubKey 'ssh-ed25519 <FULL_SESSION_PUBKEY> careertuner-fullsession-ops' `
     -TriggerPubKey     'ssh-ed25519 <TRIGGER_PUBKEY> careertuner-notebook-to-4090'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$FullSessionPubKey,                 # placeholder: 'ssh-ed25519 <FULL_SESSION_PUBKEY>'
    [string]$TriggerPubKey = "",                # placeholder: 'ssh-ed25519 <TRIGGER_PUBKEY>' (선택)
    [string]$WrapperPath = "C:\Users\careertuner\CareerTunerOps\run_careertuner_job.ps1",
    [string]$TailscaleRange = "100.64.0.0/10",
    [string]$OpsDir = "C:\Users\careertuner\CareerTunerOps",
    [string]$LogDir = "C:\ProgramData\CareerTuner4090\logs",
    [string]$TaskName = "CareerTuner-SSH-Persist"
)
$ErrorActionPreference = "Stop"

# placeholder 그대로면 거부(자체 검증: 키 값은 호출자가 placeholder 로만 전달)
foreach ($p in @($FullSessionPubKey, $TriggerPubKey)) {
    if ($p -and $p -match '<.*PUBKEY.*>') { throw "공개키 placeholder 가 채워지지 않았습니다: '$p'" }
}
if ($FullSessionPubKey -notmatch '^ssh-(ed25519|rsa|ecdsa)') { throw "FullSessionPubKey 형식이 공개키가 아닙니다." }
# 키를 코드가 아닌 데이터 파일로 분리하지만, 방어심화로 authorized_keys 줄 무결성을 깨는 문자는 거부.
foreach ($p in @($FullSessionPubKey, $TriggerPubKey)) {
    if ($p) {
        if ($p -match 'PRIVATE KEY') { throw "private key 로 보입니다 — 공개키만 전달하세요." }
        if ($p -match '["`\r\n]') { throw "공개키에 허용되지 않는 문자(따옴표/백틱/개행)가 있습니다: '$p'" }
    }
}

New-Item -ItemType Directory -Force -Path $OpsDir, $LogDir | Out-Null

# 부팅마다 실행될 self-heal 스크립트 본문.
# ★ 코드/데이터 분리(보안): 공개키 값을 스크립트 소스에 보간하지 않는다. 키는 OpsDir\authorized_keys.txt
#   에 데이터로 1회 기록(아래)하고, self-heal 은 그 파일을 Copy-Item 으로 복사만 한다. 따라서 키 값이
#   매 부팅 SYSTEM 권한으로 재파싱·실행되는 인젝션 경로가 없다.
$selfHeal = @'
param([string]$LogDir = "__LOGDIR__")
$ErrorActionPreference = "Continue"
$AK    = "C:\ProgramData\ssh\administrators_authorized_keys"
$AKSRC = "__AKSRC__"
$CFG   = "C:\ProgramData\ssh\sshd_config"
function Log($m){ $l="$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $m"; try{Add-Content (Join-Path $LogDir 'ssh-self-heal.log') $l}catch{}; Write-Host $l }
Log "self-heal start"
Set-Service sshd -StartupType Automatic -ErrorAction SilentlyContinue
if((Get-Service sshd -ErrorAction SilentlyContinue).Status -ne 'Running'){ Start-Service sshd; Log "sshd started" }
try{ Set-Service Tailscale -StartupType Automatic -ErrorAction SilentlyContinue }catch{}
if(Test-Path $AKSRC){ Copy-Item -Path $AKSRC -Destination $AK -Force; Log "authorized_keys copied from data file" }
else { Log "WARN authorized_keys data file missing: $AKSRC" }
icacls $AK /inheritance:r | Out-Null
icacls $AK /grant "Administrators:F" /grant "SYSTEM:F" | Out-Null
Log "authorized_keys + ACL reapplied"
if(Test-Path $CFG){
  $c = Get-Content $CFG -Raw
  foreach($kv in @(@('PubkeyAuthentication','yes'),@('PasswordAuthentication','no'),@('PermitEmptyPasswords','no'))){
    if($c -match "(?im)^\s*#?\s*$($kv[0])\s+.*$"){ $c=[regex]::Replace($c,"(?im)^\s*#?\s*$($kv[0])\s+.*$","$($kv[0]) $($kv[1])") }
    else { $c = $c.TrimEnd()+"`r`n$($kv[0]) $($kv[1])`r`n" }
  }
  Set-Content $CFG $c -Encoding ascii; Restart-Service sshd; Log "sshd_config reapplied"
}
try{
  Disable-NetFirewallRule -Name "OpenSSH-Server-In-TCP" -ErrorAction SilentlyContinue
  Get-NetFirewallRule -DisplayName "CareerTuner OpenSSH (Tailscale)" -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  New-NetFirewallRule -DisplayName "CareerTuner OpenSSH (Tailscale)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 22 -RemoteAddress "__TSRANGE__" | Out-Null
}catch{ Log "WARN firewall $_" }
Log "self-heal done (sshd=$((Get-Service sshd).Status))"
'@

# 키 파일을 authorized_keys 형식 '데이터'로 생성(풀세션 plain + 선택 트리거 forced-command).
# self-heal 은 이 파일을 복사만 하므로 키 값이 코드로 재파싱되지 않는다(코드/데이터 분리).
$akLines = @($FullSessionPubKey)
if ($TriggerPubKey) {
    # authorized_keys 옵션 형식: command="..." 은 평문 큰따옴표(PowerShell 이스케이프 아님 — 파일에 그대로 기록).
    $fc = 'command="powershell -NoProfile -ExecutionPolicy Bypass -File ' + $WrapperPath + '",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ' + $TriggerPubKey
    $akLines += $fc
}
$akSrcPath = Join-Path $OpsDir "authorized_keys.txt"
Set-Content -Path $akSrcPath -Value $akLines -Encoding ascii
Write-Host "[1/3] authorized_keys 데이터 파일 생성(키 포함, repo 밖): $akSrcPath"

$selfHeal = $selfHeal.Replace("__LOGDIR__", $LogDir).Replace("__TSRANGE__", $TailscaleRange).Replace("__AKSRC__", $akSrcPath)
$selfHealPath = Join-Path $OpsDir "ssh-self-heal.ps1"
Set-Content -Path $selfHealPath -Value $selfHeal -Encoding utf8
Write-Host "      self-heal 스크립트 생성(키 미포함, 데이터 파일만 참조): $selfHealPath"

# OnStart + 매시간 SYSTEM 작업
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$selfHealPath`""
$t1 = New-ScheduledTaskTrigger -AtStartup
# ★ 반복 트리거는 생성 시 -RepetitionInterval 로 만든다. -Once 만으로 만든 뒤 $t2.Repetition.Interval 에
#   대입하면 .Repetition 이 $null 이라 'property Interval cannot be found' 예외가 나고($ErrorActionPreference=Stop)
#   Register-ScheduledTask 전에 install 이 중단돼 self-heal 작업이 아예 등록되지 않는다(과거 회귀). RepetitionInterval
#   만 주고 Duration 은 생략하면 무기한 반복(범위초과 오류 없음).
$t2 = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Hours 1)
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger @($t1, $t2) -Principal $principal -Settings $settings -Force | Out-Null
# 등록 검증: 트리거 2개(OnStart + 매시간 반복)가 실제로 들어갔는지 확인.
$rt = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
$ntrig = @($rt.Triggers).Count
if ($ntrig -lt 2) { Write-Warning "트리거가 $ntrig 개 — OnStart+매시간 반복 등록이 불완전할 수 있습니다." }
Write-Host "[2/3] 스케줄 작업 '$TaskName' 등록(OnStart + 매시간, SYSTEM) — 트리거 $ntrig 개"

Write-Host "[3/3] 즉시 1회 self-heal 실행..."
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $selfHealPath
Write-Host ""
Write-Host "완료. 검증: Test-4090OpenSshHealth.ps1 실행 + 노트북에서 ssh -i <full_key> hsy82@<4090-Tailscale-IP> echo ok"
