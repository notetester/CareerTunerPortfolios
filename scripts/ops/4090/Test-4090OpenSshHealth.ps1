<#
.SYNOPSIS
  4090 OpenSSH 접속 건강검진 — publickey 거부 원인을 A~I 로 분류.

.DESCRIPTION
  4090(Windows)에서 실행. SSH 가 죽었을 때(또는 정기점검) 원인을 좁힌다. 키/토큰을 출력하지 않는다(메타만).
  분류:
    A sshd 서비스 죽음 · B Tailscale 단절 · C authorized_keys 유실 · D administrators_authorized_keys ACL 오류
    E sshd_config AuthorizedKeysFile/Pubkey 경로·정책 · F 접속 계정/관리자 그룹 · G forced-command wrapper 경로
    H OpenSSH/Windows 업데이트 후 정책 변화 · I 키 미등록/형식 오류
  로그: $LogDir\ssh-health.log
#>
[CmdletBinding()]
param(
    [string]$WrapperPath = "C:\Users\careertuner\CareerTunerOps\run_careertuner_job.ps1",
    [string]$LogDir = "C:\ProgramData\CareerTuner4090\logs"
)
$ErrorActionPreference = "Continue"
$AK = "C:\ProgramData\ssh\administrators_authorized_keys"
$CFG = "C:\ProgramData\ssh\sshd_config"
$fail = @()
function Note($m) { Write-Host $m; try { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null; Add-Content (Join-Path $LogDir 'ssh-health.log') "$(Get-Date -Format 'u')  $m" } catch {} }

Note "=== 4090 OpenSSH health ==="

# A sshd
$svc = Get-Service sshd -ErrorAction SilentlyContinue
if (-not $svc -or $svc.Status -ne 'Running') { $fail += "A: sshd 미기동(status=$($svc.Status))" }
else { Note "A ok: sshd Running (StartType=$((Get-Service sshd).StartType))" }

# B Tailscale
$ts = Get-Service Tailscale -ErrorAction SilentlyContinue
if (-not $ts -or $ts.Status -ne 'Running') { $fail += "B: Tailscale 서비스 미기동" } else { Note "B ok: Tailscale Running" }

# C authorized_keys 존재/비어있지 않음
if (-not (Test-Path $AK)) { $fail += "C: authorized_keys 파일 없음 ($AK)" }
elseif (-not ((Get-Content $AK -ErrorAction SilentlyContinue) -match 'ssh-')) { $fail += "I: authorized_keys 에 유효 키 없음" }
else { Note "C ok: authorized_keys 존재 + 키 $(@(Get-Content $AK | Where-Object {$_ -match 'ssh-'}).Count)개" }

# D ACL — SYSTEM/Administrators 외 권한이 있으면 sshd 가 파일 무시.
# ★ icacls 문자열 매칭(과거)은 'Everyone:(F)'/'Users:(RX)' 처럼 백슬래시 없는 위험 principal 을 놓쳤다.
#   Get-Acl 로 ACE 를 구조적으로 열거하고 허용 SID(SYSTEM/Administrators)만 화이트리스트한다.
if (Test-Path $AK) {
    try {
        $acl = Get-Acl $AK
        $allowSids = @('S-1-5-18', 'S-1-5-32-544')   # NT AUTHORITY\SYSTEM, BUILTIN\Administrators
        $badAces = @()
        foreach ($ace in $acl.Access) {
            $sid = $null
            try { $sid = $ace.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value }
            catch { $sid = $ace.IdentityReference.Value }
            if ($allowSids -notcontains $sid) {
                $badAces += "$($ace.IdentityReference.Value)[$sid]:$($ace.FileSystemRights)"
            }
        }
        # 소유자도 SYSTEM/Administrators 여야 sshd 가 신뢰(아니면 키 무시).
        $ownerSid = $null
        try { $ownerSid = $acl.GetOwner([System.Security.Principal.SecurityIdentifier]).Value } catch {}
        if ($ownerSid -and ($allowSids -notcontains $ownerSid)) {
            $badAces += "OWNER=$($acl.Owner)[$ownerSid]"
        }
        if ($badAces) { $fail += "D: administrators_authorized_keys ACL/소유자에 SYSTEM/Administrators 외 항목 — sshd 가 키 무시: $($badAces -join ', ')" }
        else { Note "D ok: ACL/소유자 SYSTEM/Administrators 한정(구조적 검사)" }
    } catch { $fail += "D: ACL 조회 실패 — $($_.Exception.Message)" }
}

# E sshd_config
if (Test-Path $CFG) {
    $c = Get-Content $CFG
    $pub = $c | Select-String -Pattern '^\s*PubkeyAuthentication\s+yes' -Quiet
    if (-not $pub) { $fail += "E: sshd_config PubkeyAuthentication yes 아님(또는 미설정)" } else { Note "E ok: PubkeyAuthentication yes" }
    $akf = $c | Select-String -Pattern '^\s*AuthorizedKeysFile'
    if ($akf) { Note "E info: AuthorizedKeysFile = $($akf.Line.Trim())" }
} else { $fail += "E: sshd_config 없음" }

# F 계정/관리자 그룹
$inAdmins = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Note "F info: 현재 세션 관리자=$inAdmins (admin 계정은 administrators_authorized_keys 를 사용)"

# G forced-command wrapper
if (-not (Test-Path $WrapperPath)) { $fail += "G: trigger forced-command wrapper 경로 없음 ($WrapperPath) — 트리거 키 무력(풀세션 키는 영향 없음)" }
else { Note "G ok: wrapper 존재" }

# H OpenSSH 버전(업데이트 추적용)
try { $v = (Get-Item C:\Windows\System32\OpenSSH\sshd.exe -ErrorAction SilentlyContinue).VersionInfo.ProductVersion; Note "H info: OpenSSH sshd $v" } catch {}

Note "=== 결과 ==="
if ($fail.Count -eq 0) { Note "PASS — 분류상 결함 없음. 그래도 거부되면 sshd.log(C:\ProgramData\ssh\logs\sshd.log) 확인." ; exit 0 }
foreach ($f in $fail) { Note "FAIL  $f" }
Note "→ 권장: Install-4090OpenSshSelfHeal.ps1 로 재적용(특히 C/D/E 계열)."
exit 1
