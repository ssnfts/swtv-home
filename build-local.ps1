param([string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jdkRoot = Join-Path $taskRoot 'tools\jdk17'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'JDK 17 not found. Set up the documented toolchain or use Android Studio.' }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $taskRoot 'tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $taskRoot 'tools\gradle-cache'
$gradle = Join-Path $taskRoot 'tools\gradle-8.9\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradle)) { throw 'Gradle 8.9 not found. See README build instructions.' }
Push-Location $PSScriptRoot
try { & $gradle --console=plain --no-daemon @Tasks; if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" } }
finally { Pop-Location }
