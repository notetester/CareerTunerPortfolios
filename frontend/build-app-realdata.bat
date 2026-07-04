@echo off
chcp 65001 > nul
setlocal

REM ============================================================
REM  build-app-realdata.bat
REM  실데이터 APK 빌드 한 방.
REM  android/ 는 repo에 포함되는 네이티브 프로젝트이므로 Manifest/권한은 직접 관리한다.
REM  (live reload 는 run-app-livereload.bat, 이건 번들 실데이터 APK 빌드용)
REM ============================================================

set "HERE=%~dp0"
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
cd /d "%HERE%"

REM --- 0) .env.local 확인 (백엔드 주소) ---
if not exist ".env.local" (
  echo [에러] frontend\.env.local 이 없음. 아래 2줄로 만들어라:
  echo   VITE_USE_MOCK=false
  echo   VITE_API_BASE_URL=http://^<백엔드주소^>:8080/api
  pause & exit /b 1
)

REM --- 1) 실데이터 번들 빌드 (.env.local 의 VITE_API_BASE_URL 박힘) ---
echo [1/4] npm run build...
call npm run build || (echo [에러] vite build 실패 & pause & exit /b 1)

REM --- 2) capacitor 동기화 ---
echo [2/4] cap sync android...
call npx cap sync android || (echo [에러] cap sync 실패 & pause & exit /b 1)

REM --- 3) APK 빌드 (cap run 안 씀) ---
echo [3/4] gradlew assembleDebug...
cd /d "%HERE%android"
call .\gradlew.bat assembleDebug || (echo [에러] gradle 빌드 실패 & pause & exit /b 1)

REM --- 4) 폰 설치 (연결돼 있으면) ---
echo [4/4] 폰 설치 시도...
set "APK=%HERE%android\app\build\outputs\apk\debug\app-debug.apk"
"%ADB%" get-state 1>nul 2>nul
if errorlevel 1 (
  echo   기기 없음 - APK 만 생성됨
) else (
  "%ADB%" install -r "%APK%"
  if errorlevel 1 ( "%ADB%" uninstall com.careertuner.app & "%ADB%" install "%APK%" )
  "%ADB%" shell monkey -p com.careertuner.app -c android.intent.category.LAUNCHER 1 >nul
  echo   설치+실행 완료
)

echo.
echo ============================================================
echo  완료! APK: %APK%
echo   - 실데이터(.env.local) + repo 관리 AndroidManifest 권한 포함
echo   - 폰은 백엔드가 사설망이면 Tailscale 연결 필요
echo ============================================================
pause
endlocal
