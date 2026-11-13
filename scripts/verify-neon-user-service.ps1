$javaExe = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
$logFile = "user-service-neon-test.log"

if (Test-Path $logFile) { Remove-Item $logFile -Force }

Write-Host "Starting User Service with live NeonDB configuration..."
$proc = Start-Process -FilePath $javaExe -ArgumentList "-jar", "user-service\target\user-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput $logFile -RedirectStandardError "user-service-error.log" -PassThru

try {
    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Method Get -TimeoutSec 2
            if ($resp.status -eq "UP") {
                $healthy = $true
                break
            }
        } catch {
            # Still booting
        }
    }

    if (-not $healthy) {
        Write-Error "Service failed to become healthy within 30 seconds."
        Get-Content $logFile -Tail 50
        exit 1
    }

    Write-Host "User Service is UP!"
    
    # Test Create User
    $createBody = @{
        name = "Neon Test User"
        email = "neontest_$(Get-Random)@example.com"
        role = "ADMIN"
    } | ConvertTo-Json

    $createResp = Invoke-RestMethod -Uri "http://localhost:8081/api/users" -Method Post -Body $createBody -ContentType "application/json"
    Write-Host "Created User in Neon DB:" ($createResp | ConvertTo-Json -Compress)

    # Test Retrieve User
    $userId = $createResp.id
    $getResp = Invoke-RestMethod -Uri "http://localhost:8081/api/users/$userId" -Method Get
    Write-Host "Retrieved User from Neon DB:" ($getResp | ConvertTo-Json -Compress)

} finally {
    Write-Host "Stopping User Service process..."
    Stop-Process -Id $proc.Id -Force
}

Write-Host "=== Startup Log Sample ==="
Get-Content $logFile | Select-String "Dotenv|Hikari|PostgreSQL|Hibernate|Started UserServiceApplication"

