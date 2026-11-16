# =============================================================================
# Package one Botkeep upload ZIP per service.
# =============================================================================
# Usage:
#   .\deploy\scripts\package-botkeep.ps1
#   .\deploy\scripts\package-botkeep.ps1 -SkipBuild
# =============================================================================

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$artifactsRoot = Join-Path $repoRoot "deploy\artifacts"
$zipRoot = Join-Path $artifactsRoot "botkeep"

$services = @(
    @{ Name = "gateway-service";    Memory = "384m" },
    @{ Name = "auth-service";       Memory = "320m" },
    @{ Name = "arena-service";      Memory = "384m" },
    @{ Name = "assessment-service"; Memory = "320m" },
    @{ Name = "analytics-service";  Memory = "256m" }
)

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "build-jars.ps1") -Services ($services | ForEach-Object { $_.Name })
}

New-Item -ItemType Directory -Force -Path $zipRoot | Out-Null

foreach ($service in $services) {
    $name = $service.Name
    $jarPath = Join-Path $artifactsRoot "$name\app.jar"

    if (-not (Test-Path $jarPath)) {
        throw "Missing $jarPath. Run build-jars.ps1 first."
    }

    $staging = Join-Path $env:TEMP "botkeep-$name"
    if (Test-Path $staging) {
        Remove-Item -Recurse -Force $staging
    }
    New-Item -ItemType Directory -Force -Path $staging | Out-Null

    Copy-Item $jarPath (Join-Path $staging "app.jar") -Force

    $startCommand = "java -Xms96m -Xmx$($service.Memory) -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar"
    if ($name -eq "analytics-service") {
        $startCommand = "java -Xms64m -Xmx$($service.Memory) -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar"
    }

    @"
Service: $name
Runtime: Java 21
Start command:
$startCommand

Set the environment variables from $name/.env.example in the Botkeep
Environment panel. Do not commit a filled .env file.
"@ | Set-Content -Path (Join-Path $staging "START.txt") -Encoding UTF8

    $envExample = Join-Path $repoRoot "$name\.env.example"
    if (Test-Path $envExample) {
        Copy-Item $envExample (Join-Path $staging "env.example") -Force
    }

    $zipPath = Join-Path $zipRoot "botkeep-$name.zip"
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -Force

    Write-Host "  -> deploy/artifacts/botkeep/botkeep-$name.zip" -ForegroundColor Green
}
