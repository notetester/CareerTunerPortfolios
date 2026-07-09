param(
    [string] $QtPrefix = "C:\Qt\6.11.1\mingw_64",
    [string] $MingwBin = "C:\Qt\Tools\mingw1310_64\bin",
    [string] $BuildDir = "build-release-qt6111",
    [string] $Version = "",
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

function Assert-LastExitCode {
    param([string] $Step)
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path $Windeployqt)) {
    throw "windeployqt not found: $Windeployqt"
}
if (-not (Test-Path $Compiler)) {
    throw "MinGW compiler not found: $Compiler"
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
    cmake -S $DesktopDir -B $BuildPath -G Ninja `
        "-DCMAKE_PREFIX_PATH=$QtPrefix" `
        "-DCMAKE_CXX_COMPILER=$Compiler" `
        -DCMAKE_BUILD_TYPE=Release
    Assert-LastExitCode "cmake configure"
    cmake --build $BuildPath --config Release
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
