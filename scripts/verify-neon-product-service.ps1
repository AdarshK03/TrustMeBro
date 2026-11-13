$javaExe = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
$logFile = "product-service-neon-test.log"

if (Test-Path $logFile) { Remove-Item $logFile -Force }

Write-Host "Starting Product Service with live NeonDB configuration..."
$proc = Start-Process -FilePath $javaExe -ArgumentList "-jar", "product-service\target\product-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput $logFile -RedirectStandardError "product-service-error.log" -PassThru

try {
    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get -TimeoutSec 2
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

    Write-Host "Product Service is UP!"
    
    # Test Create Product
    $createBody = @{
        name = "Neon Cloud Wireless Mouse"
        description = "Ergonomic wireless mouse"
        price = 49.99
        stock = 120
    } | ConvertTo-Json

    $createResp = Invoke-RestMethod -Uri "http://localhost:8082/api/products" -Method Post -Body $createBody -ContentType "application/json"
    Write-Host "Created Product in Neon DB:" ($createResp | ConvertTo-Json -Compress)

    # Test Retrieve Product
    $productId = $createResp.id
    $getResp = Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get
    Write-Host "Retrieved Product from Neon DB:" ($getResp | ConvertTo-Json -Compress)

} finally {
    Write-Host "Stopping Product Service process..."
    Stop-Process -Id $proc.Id -Force
}

Write-Host "=== Startup Log Sample ==="
Get-Content $logFile | Select-String "Dotenv|Hikari|PostgreSQL|Hibernate|Started ProductServiceApplication"

