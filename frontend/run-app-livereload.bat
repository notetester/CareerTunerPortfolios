@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

REM ============================================================
REM  run-app-livereload.bat
REM  폰 실기기에 CareerTuner 앱(WebView)을 PC dev 서버에 물려 띄운다.
REM  LAN IP 자동감지 -> vite -> APK 빌드 -> 폰 설치/실행.
REM  (CLAUDE.local.md "앱 실기기 live reload" 절차의 자동화)
REM  ※ cap run 은 Windows 에서 gradlew 못 부르고 깨지므로 직접 빌드한다.
REM ============================================================

set "HERE=%~dp0"
set "ANDROID_DIR=%HERE%android"
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"

REM --- 0) 기기 연결 확인 ---
"%ADB%" get-state 1>nul 2>nul
if errorlevel 1 (
  echo [에러] 연결된 안드로이드 기기가 없음. USB 디버깅 ON + 케이블 확인.
  "%ADB%" devices
  pause & exit /b 1
)

REM --- 1) LAN IP 자동감지 (192.168.x.x. Tailscale 100.x / Docker 172.x 제외) ---
set "LANIP="
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  set "ip=%%a"
  set "ip=!ip: =!"
  echo !ip! | findstr /r "^192\.168\." >nul && if "!LANIP!"=="" set "LANIP=!ip!"
)
if "%LANIP%"=="" (
  echo [에러] 192.168.x.x LAN IP를 못 찾음. 와이파이 연결 확인.
  pause & exit /b 1
)
set "CAP_SERVER_URL=http://%LANIP%:5173"
set "CAP_SYNC_MODE=debug"
set "CAP_ALLOW_CLEARTEXT=true"
echo [1/5] LAN IP 감지: %LANIP%   server=%CAP_SERVER_URL%

REM --- 2) vite dev 서버 (5173 미사용일 때만 새 창) ---
netstat -ano | findstr ":5173" | findstr LISTENING >nul
if errorlevel 1 (
  echo [2/5] vite dev 서버 새 창 실행...
  start "vite-dev (live reload)" cmd /k "cd /d "%HERE%" && npm run dev -- --host 0.0.0.0"
) else (
  echo [2/5] 5173 이미 사용 중 - 기존 vite 재사용
)

REM --- 3) cap sync (CAP_SERVER_URL 주입된 채로 server.url 반영) ---
echo [3/5] cap sync android...
cd /d "%HERE%"
call node scripts\cap-sync-with-env.mjs android || (echo [에러] cap sync 실패 & pause & exit /b 1)

REM --- 4) APK 직접 빌드 (cap run 안 씀) ---
echo [4/5] gradlew assembleDebug (첫 빌드만 몇 분, 이후 증분)...
cd /d "%ANDROID_DIR%"
call .\gradlew.bat assembleDebug || (echo [에러] gradle 빌드 실패 & pause & exit /b 1)

REM --- 5) 폰 설치 + 실행 ---
echo [5/5] 폰 설치 + 실행...
set "APK=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo     [재시도] 서명충돌 추정 - 기존 앱 제거 후 재설치
  "%ADB%" uninstall com.careertuner.app
  "%ADB%" install "%APK%"
)
"%ADB%" shell monkey -p com.careertuner.app -c android.intent.category.LAUNCHER 1 >nul

echo.
echo ============================================================
echo  완료! 폰에서 CareerTuner 앱 확인.
echo   - 코드 수정 -^> 폰 자동 반영 (vite HMR, 재빌드 불필요)
echo   - 디버깅: PC 크롬 chrome://inspect
echo   - vite 창을 닫으면 live reload 끊김(앱 흰화면)
echo   - LAN IP 가 바뀌면 이 .bat 다시 실행
echo ============================================================
pause
endlocal
