# PowerShell script to run unit tests across all microservices

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Automated Tests for Microservices Platform       " -ForegroundColor Cyan
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

& $mvnCmd test

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[SUCCESS] All unit tests passed cleanly!" -ForegroundColor Green
} else {
    Write-Host "`n[FAILURE] One or more tests failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
