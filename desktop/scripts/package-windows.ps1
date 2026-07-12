param(
    [string] $QtPrefix = "C:\Qt\6.11.1\mingw_64",
    [string] $MingwBin = "C:\Qt\Tools\mingw1310_64\bin",
    [string] $BuildDir = "build-release-qt6111",
    [string] $Version = "",
    [string] $CMakePath = "",
    [string] $Generator = "",
    [switch] $NoBuild,
    [switch] $SkipInstaller,
    [switch] $SkipPortable
)

$ErrorActionPreference = "Stop"

$DesktopDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildPath = Join-Path $DesktopDir $BuildDir
$DistRoot = Join-Path $BuildPath "dist"
$StageDir = Join-Path $DistRoot "CareerTunerDesktop"
$PackageDir = Join-Path $DistRoot "packages"
$ExePath = Join-Path $BuildPath "CareerTunerDesktop.exe"
$StagedExe = Join-Path $StageDir "CareerTunerDesktop.exe"
$Windeployqt = Join-Path $QtPrefix "bin\windeployqt.exe"
$Compiler = Join-Path $MingwBin "g++.exe"
$MingwMake = Join-Path $MingwBin "mingw32-make.exe"

function Assert-LastExitCode {
    param([string] $Step)
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

function Get-CMakeExecutable {
    param([string] $ExplicitPath)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (-not (Test-Path -LiteralPath $ExplicitPath)) {
            throw "CMake executable not found: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }
    $command = Get-Command cmake -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $candidates = @(
        (Join-Path $env:ProgramFiles "CMake\bin\cmake.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\CMake\bin\cmake.exe")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    throw "CMake was not found in PATH or standard install locations. Pass -CMakePath explicitly."
}

function Get-BuildGenerator {
    param([string] $ExplicitGenerator)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitGenerator)) {
        return $ExplicitGenerator
    }
    $ninja = Get-Command ninja -ErrorAction SilentlyContinue
    if ($ninja -or (Test-Path -LiteralPath (Join-Path $MingwBin "ninja.exe"))) {
        return "Ninja"
    }
    if (Test-Path -LiteralPath $MingwMake) {
        return "MinGW Makefiles"
    }
    throw "Neither Ninja nor mingw32-make was found. Install Ninja or provide MinGW make in -MingwBin."
}

if (-not (Test-Path $Windeployqt)) {
    throw "windeployqt not found: $Windeployqt"
}
if (-not (Test-Path $Compiler)) {
    throw "MinGW compiler not found: $Compiler"
}

$CMakeExecutable = Get-CMakeExecutable $CMakePath
$ResolvedGenerator = Get-BuildGenerator $Generator

# 같은 build 폴더는 CMake generator를 바꿀 수 없다. 기존 캐시와 현재 탐지 결과가 다르면
# 사용자의 기존 산출물을 지우지 않고 generator별 형제 폴더로 전환한다.
$CachePath = Join-Path $BuildPath "CMakeCache.txt"
if (Test-Path -LiteralPath $CachePath) {
    $cache = Get-Content -LiteralPath $CachePath -Raw
    if ($cache -match "(?m)^CMAKE_GENERATOR:INTERNAL=(.+)$") {
        $CachedGenerator = $Matches[1].Trim()
        if ($CachedGenerator -ne $ResolvedGenerator) {
            $GeneratorSlug = ($ResolvedGenerator -replace '[^A-Za-z0-9]+', '-').Trim('-').ToLowerInvariant()
            $BuildPath = Join-Path $DesktopDir "$BuildDir-$GeneratorSlug"
            $DistRoot = Join-Path $BuildPath "dist"
            $StageDir = Join-Path $DistRoot "CareerTunerDesktop"
            $PackageDir = Join-Path $DistRoot "packages"
            $ExePath = Join-Path $BuildPath "CareerTunerDesktop.exe"
            $StagedExe = Join-Path $StageDir "CareerTunerDesktop.exe"
            Write-Warning "CMake generator changed ($CachedGenerator -> $ResolvedGenerator). Preserving the existing build and using: $BuildPath"

            $AlternateCache = Join-Path $BuildPath "CMakeCache.txt"
            if (Test-Path -LiteralPath $AlternateCache) {
                $alternate = Get-Content -LiteralPath $AlternateCache -Raw
                if ($alternate -match "(?m)^CMAKE_GENERATOR:INTERNAL=(.+)$") {
                    $AlternateGenerator = $Matches[1].Trim()
                    if ($AlternateGenerator -ne $ResolvedGenerator) {
                        throw "Generator-specific build directory has an incompatible CMake cache: $AlternateCache"
                    }
                }
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($Version)) {
    $cmakeLists = Get-Content (Join-Path $DesktopDir "CMakeLists.txt") -Raw
    if ($cmakeLists -match "project\(\s*CareerTunerDesktop\s+VERSION\s+([0-9A-Za-z\.\-\+]+)") {
        $Version = $Matches[1]
    } else {
        $Version = "0.0.0"
    }
}

$env:PATH = "$MingwBin;$QtPrefix\bin;$env:PATH"

if (-not $NoBuild) {
    & $CMakeExecutable -S $DesktopDir -B $BuildPath -G $ResolvedGenerator `
        "-DCMAKE_PREFIX_PATH=$QtPrefix" `
        "-DCMAKE_CXX_COMPILER=$Compiler" `
        -DCMAKE_DISABLE_FIND_PACKAGE_WrapVulkanHeaders=ON `
        -DCMAKE_BUILD_TYPE=Release
    Assert-LastExitCode "cmake configure"
    & $CMakeExecutable --build $BuildPath --config Release
    Assert-LastExitCode "cmake build"
}

if (-not (Test-Path $ExePath)) {
    throw "Built executable not found: $ExePath"
}

if (Test-Path $StageDir) {
    Remove-Item -LiteralPath $StageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $StageDir | Out-Null
Copy-Item -LiteralPath $ExePath -Destination $StageDir

& $Windeployqt --release `
    --qmldir (Join-Path $DesktopDir "qml") `
    --compiler-runtime `
    --no-system-dxc-compiler `
    --dir $StageDir `
    $StagedExe
Assert-LastExitCode "windeployqt"

if (Test-Path $PackageDir) {
    Remove-Item -LiteralPath $PackageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $PackageDir | Out-Null

$zipPath = Join-Path $PackageDir "CareerTunerDesktop-$Version-windows-x64.zip"
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path $StageDir -DestinationPath $zipPath -CompressionLevel Optimal

function Get-Makensis {
    $cmd = Get-Command makensis -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    $candidates = @(
        "C:\Program Files\NSIS\makensis.exe",
        "C:\Program Files (x86)\NSIS\makensis.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    return $null
}

function Expand-Template {
    param(
        [string] $TemplatePath,
        [string] $OutputPath,
        [hashtable] $Values
    )
    $content = Get-Content $TemplatePath -Raw
    foreach ($key in $Values.Keys) {
        $content = $content.Replace("@$key@", $Values[$key])
    }
    Set-Content -Path $OutputPath -Value $content -Encoding UTF8
}

$makensis = Get-Makensis
$installerPath = Join-Path $PackageDir "CareerTunerDesktop-$Version-windows-x64-setup.exe"
$portablePath = Join-Path $PackageDir "CareerTunerDesktop-$Version-windows-x64-portable.exe"

if ($makensis) {
    $templateDir = Join-Path $DesktopDir "packaging\windows"
    $generatedDir = Join-Path $BuildPath "generated-nsis"
    New-Item -ItemType Directory -Path $generatedDir -Force | Out-Null

    $values = @{
        "APP_VERSION" = $Version
        "DIST_DIR" = $StageDir.Replace("\", "\\")
        "INSTALLER_OUT" = $installerPath.Replace("\", "\\")
        "PORTABLE_OUT" = $portablePath.Replace("\", "\\")
    }

    if (-not $SkipInstaller) {
        $installerNsi = Join-Path $generatedDir "installer.nsi"
        Expand-Template `
            -TemplatePath (Join-Path $templateDir "installer.nsi.in") `
            -OutputPath $installerNsi `
            -Values $values
        & $makensis $installerNsi
        Assert-LastExitCode "makensis installer"
    }

    if (-not $SkipPortable) {
        $portableNsi = Join-Path $generatedDir "portable.nsi"
        Expand-Template `
            -TemplatePath (Join-Path $templateDir "portable.nsi.in") `
            -OutputPath $portableNsi `
            -Values $values
        & $makensis $portableNsi
        Assert-LastExitCode "makensis portable"
    }
} else {
    Write-Warning "NSIS makensis was not found. Zip package was created, but installer and portable exe were skipped."
    Write-Warning "Install NSIS, then rerun this script. Example: winget install NSIS.NSIS"
}

$files = Get-ChildItem -LiteralPath $StageDir -Recurse -File
$stageBytes = ($files | Measure-Object -Sum Length).Sum
$manifest = [ordered]@{
    app = "CareerTunerDesktop"
    version = $Version
    gitCommit = (git -C $DesktopDir rev-parse HEAD)
    builtAt = (Get-Date).ToString("o")
    qtPrefix = $QtPrefix
    cmake = $CMakeExecutable
    generator = $ResolvedGenerator
    stageDir = $StageDir
    stagedFileCount = $files.Count
    stagedSizeBytes = [int64] $stageBytes
    zip = $zipPath
    installer = if (Test-Path $installerPath) { $installerPath } else { $null }
    portable = if (Test-Path $portablePath) { $portablePath } else { $null }
}
$manifestPath = Join-Path $PackageDir "release-manifest.json"
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host "Created packages:"
Get-ChildItem -LiteralPath $PackageDir -File |
    Sort-Object Name |
    Select-Object Name, @{Name = "SizeMB"; Expression = { "{0:N1}" -f ($_.Length / 1MB) } } |
    Format-Table -AutoSize
