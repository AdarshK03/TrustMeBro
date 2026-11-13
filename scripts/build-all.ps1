# PowerShell script to build all microservices and frontend

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building Cloud-Based Microservices Platform (Day 1)      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Detect Java & Maven
$mvnCmd = "mvn"
if (-not (Get-Command "mvn" -ErrorAction SilentlyContinue)) {
    $ideaMvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMvn) {
        $mvnCmd = $ideaMvn
        $env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr"
        Write-Host "[INFO] Using IntelliJ bundled Maven and JBR: $ideaMvn" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Maven executable not found in PATH or standard IntelliJ location." -ForegroundColor Red
        exit 1
    }
}

# 1. Build Spring Boot Applications
Write-Host "`n[1/2] Compiling and Packaging Spring Boot Microservices..." -ForegroundColor Green
& $mvnCmd clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 2. Build React Frontend
Write-Host "`n[2/2] Installing Frontend Dependencies and Building..." -ForegroundColor Green
Push-Location frontend
npm install
npm run build
Pop-Location

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Build Completed Successfully!                            " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
