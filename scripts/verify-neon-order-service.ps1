$javaExe = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
$logFile = "order-service-neon-test.log"

if (Test-Path $logFile) { Remove-Item $logFile -Force }

Write-Host "Starting Order Service with live NeonDB configuration..."
$proc = Start-Process -FilePath $javaExe -ArgumentList "-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput $logFile -RedirectStandardError "order-service-error.log" -PassThru

try {
    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -Method Get -TimeoutSec 2
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

    Write-Host "Order Service is UP!"

    # Test Create Order
    $createBody = @{
        userId = 1
        items = @(
            @{
                productId = 1
                quantity = 2
            }
        )
    } | ConvertTo-Json

    $createResp = Invoke-RestMethod -Uri "http://localhost:8083/api/orders" -Method Post -Body $createBody -ContentType "application/json"
    Write-Host "Created Order in Neon DB:" ($createResp | ConvertTo-Json -Compress)

    # Test Retrieve Order
    $orderId = $createResp.id
    $getResp = Invoke-RestMethod -Uri "http://localhost:8083/api/orders/$orderId" -Method Get
    Write-Host "Retrieved Order from Neon DB:" ($getResp | ConvertTo-Json -Compress)

} finally {
    Write-Host "Stopping Order Service process..."
    Stop-Process -Id $proc.Id -Force
}

Write-Host "=== Startup Log Sample ==="
Get-Content $logFile | Select-String "Dotenv|Hikari|PostgreSQL|Hibernate|Started OrderServiceApplication"

