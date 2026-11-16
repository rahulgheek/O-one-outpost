# =============================================================================
# Build every service JAR and copy it to deploy/artifacts/<service>/app.jar
# =============================================================================
# Usage:
#   .\deploy\scripts\build-jars.ps1
#   .\deploy\scripts\build-jars.ps1 -Services gateway-service,auth-service
# =============================================================================

param(
    [string[]]$Services = @(
        "gateway-service",
        "auth-service",
        "arena-service",
        "assessment-service",
        "analytics-service",
        "code-sandbox"
    )
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$artifactsRoot = Join-Path $repoRoot "deploy\artifacts"
$isWindows = ($env:OS -eq "Windows_NT")

foreach ($service in $Services) {
    $serviceDir = Join-Path $repoRoot $service
    if (-not (Test-Path $serviceDir)) {
        throw "Unknown service directory: $serviceDir"
    }

    Write-Host ""
    Write-Host "=== Building $service ===" -ForegroundColor Cyan
    Push-Location $serviceDir
    try {
        if ($isWindows) {
            & .\mvnw.cmd -q -DskipTests clean package
        } else {
            & ./mvnw -q -DskipTests clean package
        }

        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed for $service (exit $LASTEXITCODE)"
        }

        $jar = Get-ChildItem -Path (Join-Path $serviceDir "target") -Filter "*.jar" |
            Where-Object { $_.Name -notlike "*.original" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if (-not $jar) {
            throw "No JAR produced for $service"
        }

        $outDir = Join-Path $artifactsRoot $service
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        Copy-Item -Path $jar.FullName -Destination (Join-Path $outDir "app.jar") -Force

        $sizeMb = [math]::Round($jar.Length / 1MB, 1)
        Write-Host "  -> deploy/artifacts/$service/app.jar ($sizeMb MB)" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "All requested services built." -ForegroundColor Green
Write-Host "Next: upload each app.jar to its Botkeep Java workload." -ForegroundColor Yellow
