# PowerShell script to start all microservices and frontend in individual terminal windows

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting Microservices Platform Locally                  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Load .env file if present
$envFile = "$PSScriptRoot\..\.env"
if (Test-Path $envFile) {
    Write-Host "[INFO] Loading environment variables from .env..." -ForegroundColor Yellow
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $val, [System.EnvironmentVariableTarget]::Process)
        }
    }
}

# Detect Java & Maven
$mvnCmd = "mvn"
if (-not (Get-Command "mvn" -ErrorAction SilentlyContinue)) {
    $ideaMvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMvn) {
        $mvnCmd = $ideaMvn
        $env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr"
    } else {
        Write-Host "[ERROR] Maven executable not found in PATH or standard IntelliJ location." -ForegroundColor Red
        exit 1
    }
}

$javaEnvPrefix = if ($env:JAVA_HOME) { "`$env:JAVA_HOME = '$($env:JAVA_HOME)'; " } else { "" }

Write-Host "Launching User Service on Port 8081 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$javaEnvPrefix cd '$PSScriptRoot\..\user-service'; & '$mvnCmd' spring-boot:run"

Write-Host "Launching Product Service on Port 8082 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$javaEnvPrefix cd '$PSScriptRoot\..\product-service'; & '$mvnCmd' spring-boot:run"

Write-Host "Launching Order Service on Port 8083 (Neon PostgreSQL)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$javaEnvPrefix cd '$PSScriptRoot\..\order-service'; & '$mvnCmd' spring-boot:run"

Write-Host "Launching API Gateway on Port 8080..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$javaEnvPrefix cd '$PSScriptRoot\..\gateway'; & '$mvnCmd' spring-boot:run"

Write-Host "Launching React Frontend on Port 5173..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\..\frontend'; npm run dev"

Write-Host "`nAll services have been launched in separate console windows." -ForegroundColor Cyan
Write-Host "Gateway:  http://localhost:8080" -ForegroundColor White
Write-Host "Frontend: http://localhost:5173" -ForegroundColor White
