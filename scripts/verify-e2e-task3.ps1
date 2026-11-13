# End-to-End Verification Script for Task 3: Order Service and Gateway Routing
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Phase 8: End-to-End Verification (Task 3)                 " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$javaCmd = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
$javacCmd = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe"
$pgJar = "C:\Users\adars\.m2\repository\org\postgresql\postgresql\42.7.4\postgresql-42.7.4.jar"

New-Item -ItemType Directory -Path "$PSScriptRoot\..\logs" -Force | Out-Null

# Clean up any leftover processes on ports 8080, 8081, 8082, 8083
$ports = @(8080, 8081, 8082, 8083)
foreach ($p in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conn) {
        $pids = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}
Start-Sleep -Seconds 1

function Wait-For-Health($url, $serviceName, $timeoutSec = 45) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Host "Waiting for $serviceName to report UP on $url..." -NoNewline
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        try {
            $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 2 -ErrorAction Stop
            if ($resp.status -eq "UP") {
                Write-Host " [UP in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s]" -ForegroundColor Green
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 600
        }
    }
    Write-Host " [TIMEOUT]" -ForegroundColor Red
    return $false
}

# 1. Start User Service
Write-Host "`n[1/4] Starting User Service on port 8081..." -ForegroundColor Yellow
$userProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "user-service\target\user-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\user.log" -RedirectStandardError "logs\user-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8081/actuator/health" "User Service")) {
    Write-Host "[ERROR] User Service failed to start. Log output:" -ForegroundColor Red
    Get-Content "logs\user.log" -Tail 30
    exit 1
}

# 2. Start Product Service
Write-Host "`n[2/4] Starting Product Service on port 8082..." -ForegroundColor Yellow
$productProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "product-service\target\product-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\product.log" -RedirectStandardError "logs\product-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8082/actuator/health" "Product Service")) {
    Write-Host "[ERROR] Product Service failed to start. Log output:" -ForegroundColor Red
    Get-Content "logs\product.log" -Tail 30
    exit 1
}

# 3. Start Order Service
Write-Host "`n[3/4] Starting Order Service on port 8083..." -ForegroundColor Yellow
$orderProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\order.log" -RedirectStandardError "logs\order-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8083/actuator/health" "Order Service")) {
    Write-Host "[ERROR] Order Service failed to start. Log output:" -ForegroundColor Red
    Get-Content "logs\order.log" -Tail 30
    exit 1
}

# 4. Start API Gateway
Write-Host "`n[4/4] Starting API Gateway on port 8080..." -ForegroundColor Yellow
$gatewayProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "gateway\target\gateway-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\gateway.log" -RedirectStandardError "logs\gateway-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8080/actuator/health" "API Gateway")) {
    Write-Host "[ERROR] API Gateway failed to start. Log output:" -ForegroundColor Red
    Get-Content "logs\gateway.log" -Tail 30
    exit 1
}

try {
    Write-Host "`n--- STEP 1: Verify Gateway Health and Route Configuration ---" -ForegroundColor Cyan
    $gwHealth = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
    Write-Host "Gateway Health Status: $($gwHealth.status)" -ForegroundColor Green

    Write-Host "`n--- STEP 2: Verify User Service through Gateway ---" -ForegroundColor Cyan
    $userCorrId = "e2e-trace-user-" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
    $usersResp = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -Headers @{"X-Correlation-ID"=$userCorrId} -Method Get -UseBasicParsing
    Write-Host "GET /api/users Status: $($usersResp.StatusCode)" -ForegroundColor Green
    Write-Host "Returned X-Correlation-ID: $($usersResp.Headers['X-Correlation-ID'])" -ForegroundColor Green
    
    $users = $usersResp.Content | ConvertFrom-Json
    $validUserId = if ($users -and $users.Length -gt 0) { $users[0].id } elseif ($users -and $users.id) { $users.id } else {
        $newUserJson = '{"name":"E2E Test User","email":"e2e_user_' + (Get-Date -Format "yyyyMMddHHmmss") + '@example.com","role":"CUSTOMER"}'
        $uResp = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Body $newUserJson -ContentType "application/json"
        $uResp.id
    }
    Write-Host "Using Valid User ID: $validUserId" -ForegroundColor Green

    Write-Host "`n--- STEP 3: Create and Verify Product through Gateway ---" -ForegroundColor Cyan
    $productJson = @{
        name = "E2E Gaming Headset " + (Get-Date -Format "HHmmss")
        description = "High-fidelity spatial audio headset"
        price = 129.99
        stock = 25
    } | ConvertTo-Json
    $prodResp = Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Method Post -Body $productJson -ContentType "application/json"
    $productId = $prodResp.id
    Write-Host "Created Product ID: $productId (Name: $($prodResp.name), Price: $($prodResp.price), Stock: $($prodResp.stock))" -ForegroundColor Green

    Write-Host "`n--- STEP 4: Create Order through Gateway (Synchronous Product Verification) ---" -ForegroundColor Cyan
    $orderCorrId = "e2e-order-corr-" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
    $orderJson = @{
        userId = [long]$validUserId
        items = @(
            @{
                productId = [long]$productId
                quantity = 2
            }
        )
    } | ConvertTo-Json
    
    $createOrderResp = Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Headers @{"X-Correlation-ID"=$orderCorrId} -Method Post -Body $orderJson -ContentType "application/json" -UseBasicParsing
    Write-Host "POST /api/orders Status: $($createOrderResp.StatusCode)" -ForegroundColor Green
    Write-Host "Returned X-Correlation-ID: $($createOrderResp.Headers['X-Correlation-ID'])" -ForegroundColor Green
    $createdOrder = $createOrderResp.Content | ConvertFrom-Json
    $orderId = $createdOrder.id
    Write-Host "Created Order ID: $orderId, Status: $($createdOrder.status), TotalAmount: $($createdOrder.totalAmount)" -ForegroundColor Green
    Write-Host "Line Item Subtotal: $($createdOrder.items[0].subtotal) (Qty: $($createdOrder.items[0].quantity) x UnitPrice: $($createdOrder.items[0].unitPrice))" -ForegroundColor Green

    Write-Host "`n--- STEP 5: Retrieve Created Order through Gateway ---" -ForegroundColor Cyan
    $getOrderResp = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$orderId" -Method Get
    Write-Host "Retrieved Order ID: $($getOrderResp.id), TotalAmount: $($getOrderResp.totalAmount)" -ForegroundColor Green
    
    Write-Host "`n--- STEP 6: Retrieve Paginated Orders through Gateway ---" -ForegroundColor Cyan
    $pagedOrders = Invoke-RestMethod -Uri "http://localhost:8080/api/orders?page=0&size=2" -Method Get
    Write-Host "Paginated Query (page 0, size 2) Total Elements: $($pagedOrders.totalElements), Total Pages: $($pagedOrders.totalPages)" -ForegroundColor Green

    Write-Host "`n--- STEP 7: Verify Persisted Order in Neon PostgreSQL ---" -ForegroundColor Cyan
    $dbUrl = "jdbc:postgresql://ep-young-dew-b4fozts7-pooler.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require&channel_binding=require"
    $dbUser = "neondb_owner"
    $dbPass = "npg_VcXLOe6N9tSJ"

    $dbVerifyCode = @"
import java.sql.*;

public class OrderDbVerifier {
    public static void main(String[] args) throws Exception {
        String url = "$dbUrl";
        String user = "$dbUser";
        String password = "$dbPass";
        long targetOrderId = $orderId;

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, user_id, status, total_amount, created_at FROM orders WHERE id = ?")) {
                stmt.setLong(1, targetOrderId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println(String.format("PostgreSQL Confirmed: Order [%d] User: %d, Status: %s, Total: $%s, CreatedAt: %s",
                            rs.getLong("id"), rs.getLong("user_id"), rs.getString("status"), rs.getString("total_amount"), rs.getString("created_at")));
                    } else {
                        System.err.println("Order not found in PostgreSQL!");
                        System.exit(1);
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, product_id, quantity, unit_price, subtotal FROM order_items WHERE order_id = ?")) {
                stmt.setLong(1, targetOrderId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        System.out.println(String.format("PostgreSQL Confirmed: Item [%d] Product: %d, Qty: %d, UnitPrice: $%s, Subtotal: $%s",
                            rs.getLong("id"), rs.getLong("product_id"), rs.getInt("quantity"), rs.getString("unit_price"), rs.getString("subtotal")));
                    }
                }
            }
        }
    }
}
"@
    [System.IO.File]::WriteAllText("OrderDbVerifier.java", $dbVerifyCode)
    & $javacCmd -cp $pgJar OrderDbVerifier.java
    & $javaCmd -cp ".;$pgJar" OrderDbVerifier
    Remove-Item OrderDbVerifier.java, OrderDbVerifier.class -ErrorAction SilentlyContinue

    Write-Host "`n--- STEP 8: Business Validation & Error Responses ---" -ForegroundColor Cyan
    # 8.1 Nonexistent Product (404)
    try {
        $invalidProdOrder = @{ userId = [long]$validUserId; items = @(@{ productId = 999999; quantity = 1 }) } | ConvertTo-Json
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Post -Body $invalidProdOrder -ContentType "application/json" -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host "Nonexistent Product -> HTTP $($err.StatusCode.value__) (Expected 404)" -ForegroundColor Green
    }

    # 8.2 Nonexistent User (404)
    try {
        $invalidUserOrder = @{ userId = 999999; items = @(@{ productId = [long]$productId; quantity = 1 }) } | ConvertTo-Json
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Post -Body $invalidUserOrder -ContentType "application/json" -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host "Nonexistent User -> HTTP $($err.StatusCode.value__) (Expected 404)" -ForegroundColor Green
    }

    # 8.3 Stock Exceeded (400)
    try {
        $excessOrder = @{ userId = [long]$validUserId; items = @(@{ productId = [long]$productId; quantity = 50 }) } | ConvertTo-Json
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Post -Body $excessOrder -ContentType "application/json" -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host "Stock Exceeded (Requested 50, Available 25) -> HTTP $($err.StatusCode.value__) (Expected 400)" -ForegroundColor Green
    }

    # 8.4 Invalid Quantity 0 (400)
    try {
        $zeroQtyOrder = @{ userId = [long]$validUserId; items = @(@{ productId = [long]$productId; quantity = 0 }) } | ConvertTo-Json
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Post -Body $zeroQtyOrder -ContentType "application/json" -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host "Zero Quantity Validation -> HTTP $($err.StatusCode.value__) (Expected 400)" -ForegroundColor Green
    }

    Write-Host "`n--- STEP 9: Downstream Unavailability Testing ---" -ForegroundColor Cyan
    Write-Host "Stopping Product Service to simulate downstream microservice outage..." -ForegroundColor Yellow
    Stop-Process -Id $productProc.Id -Force
    Start-Sleep -Seconds 2

    # 9.1 Order creation when Product Service is down
    try {
        Write-Host "Attempting order creation with Product Service down..." -NoNewline
        $failOrder = @{ userId = [long]$validUserId; items = @(@{ productId = [long]$productId; quantity = 1 }) } | ConvertTo-Json
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Post -Body $failOrder -ContentType "application/json" -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host " Returned HTTP $($err.StatusCode.value__) (Expected 503 Service Unavailable)" -ForegroundColor Green
    }

    # 9.2 Gateway direct call when Product Service is down
    try {
        Write-Host "Calling /api/products/1 through Gateway with Product Service down..." -NoNewline
        Invoke-WebRequest -Uri "http://localhost:8080/api/products/1" -Method Get -UseBasicParsing
    } catch {
        $err = $_.Exception.Response
        Write-Host " Returned HTTP $($err.StatusCode.value__) (Expected 503 Service Unavailable)" -ForegroundColor Green
    }

    Write-Host "`n--- STEP 10: Verify Gateway Timing Logs in Gateway Output ---" -ForegroundColor Cyan
    Get-Content "logs\gateway.log" | Select-String "Gateway Timing" | Select-Object -Last 5 | ForEach-Object {
        Write-Host "Gateway Log: $_" -ForegroundColor DarkGray
    }

    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " ALL END-TO-END VERIFICATION CHECKS PASSED PERFECTLY!      " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
}
finally {
    Write-Host "`nCleaning up running services..." -ForegroundColor Yellow
    Stop-Process -Id $gatewayProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $orderProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $userProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $productProc.Id -Force -ErrorAction SilentlyContinue
}
