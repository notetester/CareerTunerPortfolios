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
  echo   VITE_API_BASE_URL=https://^<도달가능한-백엔드호스트^>/api
  pause & exit /b 1
)

REM --- 1) 실데이터 번들 빌드 (.env.local 의 VITE_API_BASE_URL 박힘) ---
echo [1/4] npm run build...
call npm run build || (echo [에러] vite build 실패 & pause & exit /b 1)

REM --- 2) capacitor 동기화 (번들 앱은 HTTPS-only release-safe 프로필) ---
echo [2/4] cap sync android...
call npm run native:sync -- android || (echo [에러] cap sync 실패 & pause & exit /b 1)

REM --- 3) APK 빌드 (cap run 안 씀) ---
REM release 서명 환경변수 4개가 모두 있으면 verified App Link까지 가능한 release를 만든다.
REM 없으면 일반 API 기능 확인용 debug를 만들되, debug 인증서 지문은 운영 assetlinks에 없으므로 소셜 OAuth는 제외한다.
set "GRADLE_TASK=assembleRelease"
set "APK=%HERE%android\app\build\outputs\apk\release\app-release.apk"
set "APP_LINK_NOTE=verified App Link 검증 가능(release 서명 지문 일치 필요)"
if "%CAREERTUNER_ANDROID_STOREFILE%"=="" goto use_debug_apk
if "%CAREERTUNER_ANDROID_STOREPASSWORD%"=="" goto use_debug_apk
if "%CAREERTUNER_ANDROID_KEYALIAS%"=="" goto use_debug_apk
if "%CAREERTUNER_ANDROID_KEYPASSWORD%"=="" goto use_debug_apk
goto build_apk

:use_debug_apk
set "GRADLE_TASK=assembleDebug"
set "APK=%HERE%android\app\build\outputs\apk\debug\app-debug.apk"
set "APP_LINK_NOTE=debug 서명: 비밀번호/API 테스트 전용, 소셜 OAuth App Link 미검증"
echo [경고] release 서명 환경변수가 없어 debug APK를 만듭니다.
echo        네이티브 소셜 로그인/연결까지 테스트하려면 Actions live 빌드 또는 release 서명을 사용하세요.

:build_apk
echo [3/4] gradlew %GRADLE_TASK%...
cd /d "%HERE%android"
call .\gradlew.bat %GRADLE_TASK% || (echo [에러] gradle 빌드 실패 & pause & exit /b 1)

REM --- 4) 폰 설치 (연결돼 있으면) ---
echo [4/4] 폰 설치 시도...
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
echo   - %APP_LINK_NOTE%
echo   - 폰은 백엔드가 사설망이면 Tailscale 연결 필요
echo ============================================================
pause
endlocal
