# PowerShell script to start all microservices and frontend from pre-packaged JARs
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting Microservices Platform (Packaged JARs Mode)     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Detect Java
$javaCmd = "java"
if (-not (Get-Command "java" -ErrorAction SilentlyContinue)) {
    $ideaJava = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
    if (Test-Path $ideaJava) {
        $javaCmd = $ideaJava
    } else {
        Write-Host "[ERROR] Java executable not found in PATH or standard IntelliJ location." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Launching User Service on Port 8081 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..'; & '$javaCmd' -jar user-service\target\user-service-1.0.0-SNAPSHOT.jar"

Write-Host "Launching Product Service on Port 8082 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..'; & '$javaCmd' -jar product-service\target\product-service-1.0.0-SNAPSHOT.jar"

Write-Host "Launching Order Service on Port 8083 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..'; & '$javaCmd' -jar order-service\target\order-service-1.0.0-SNAPSHOT.jar"

Write-Host "Launching API Gateway on Port 8080..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..'; & '$javaCmd' -jar gateway\target\gateway-1.0.0-SNAPSHOT.jar"

Write-Host "Launching React Frontend on Port 5173..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..\frontend'; npm run dev"

Write-Host "`nAll services have been launched in separate console windows." -ForegroundColor Cyan
Write-Host "Gateway:  http://localhost:8080" -ForegroundColor White
Write-Host "Frontend: http://localhost:5173" -ForegroundColor White

