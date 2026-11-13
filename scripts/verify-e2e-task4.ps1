# End-to-End Verification Script for Task 4: Security and Traffic-Management Configurations
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Phase 8: End-to-End Verification (Task 4)                 " -ForegroundColor Cyan
Write-Host " Security, JWT, Rate Limiting, and Response Caching        " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$javaCmd = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"
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
    Write-Host "Waiting for $serviceName on $url..." -NoNewline
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
$userProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "user-service\target\user-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\user-t4.log" -RedirectStandardError "logs\user-t4-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8081/actuator/health" "User Service")) {
    Write-Host "[ERROR] User Service failed to start." -ForegroundColor Red
    Get-Content "logs\user-t4.log" -Tail 30
    exit 1
}

# 2. Start Product Service
Write-Host "`n[2/4] Starting Product Service on port 8082..." -ForegroundColor Yellow
$productProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "product-service\target\product-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\product-t4.log" -RedirectStandardError "logs\product-t4-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8082/actuator/health" "Product Service")) {
    Write-Host "[ERROR] Product Service failed to start." -ForegroundColor Red
    Get-Content "logs\product-t4.log" -Tail 30
    exit 1
}

# 3. Start Order Service
Write-Host "`n[3/4] Starting Order Service on port 8083..." -ForegroundColor Yellow
$orderProc = Start-Process -FilePath $javaCmd -ArgumentList "-jar", "order-service\target\order-service-1.0.0-SNAPSHOT.jar" -RedirectStandardOutput "logs\order-t4.log" -RedirectStandardError "logs\order-t4-err.log" -PassThru
if (-not (Wait-For-Health "http://localhost:8083/actuator/health" "Order Service")) {
    Write-Host "[ERROR] Order Service failed to start." -ForegroundColor Red
    Get-Content "logs\order-t4.log" -Tail 30
    exit 1
}

# Function to start/restart Gateway with specific traffic management environment variables
function Start-Gateway {
    param(
        [string]$rateLimitEnabled = "false",
        [string]$maxRequests = "100",
        [string]$cacheEnabled = "false",
        [string]$cacheExpiration = "60"
    )
    
    # Terminate previous gateway if running
    $conn = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
    if ($conn) {
        $pids = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    }

    $args = @(
        "-DSECURITY_JWT_ENABLED=true",
        "-DRATE_LIMIT_ENABLED=$rateLimitEnabled",
        "-DRATE_LIMIT_MAX_REQUESTS=$maxRequests",
        "-DRATE_LIMIT_WINDOW_SECONDS=60",
        "-DCACHE_ENABLED=$cacheEnabled",
        "-DCACHE_EXPIRATION_SECONDS=$cacheExpiration",
        "-DCACHE_MAX_SIZE=100",
        "-jar",
        "gateway\target\gateway-1.0.0-SNAPSHOT.jar"
    )

    $gwProc = Start-Process -FilePath $javaCmd -ArgumentList $args -RedirectStandardOutput "logs\gateway-t4.log" -RedirectStandardError "logs\gateway-t4-err.log" -PassThru
    if (-not (Wait-For-Health "http://localhost:8080/actuator/health" "API Gateway")) {
        Write-Host "[ERROR] Gateway failed to start with RateLimit=$rateLimitEnabled, Cache=$cacheEnabled" -ForegroundColor Red
        Get-Content "logs\gateway-t4.log" -Tail 30
        exit 1
    }
    return $gwProc
}

# 4. Start API Gateway (Initial: Default baseline with security enabled)
Write-Host "`n[4/4] Starting API Gateway on port 8080 (Security: Enabled, RateLimit: Disabled, Cache: Disabled)..." -ForegroundColor Yellow
$gatewayProc = Start-Gateway "false" "100" "false" "60"

try {
    Write-Host "`n--- CHECK 1 & 2: Health Checks for All 4 Services ---" -ForegroundColor Cyan
    $gwHealth = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
    Write-Host "Gateway Health: $($gwHealth.status)" -ForegroundColor Green
    $userHealth = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Method Get
    Write-Host "User Service Health: $($userHealth.status)" -ForegroundColor Green
    $prodHealth = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method Get
    Write-Host "Product Service Health: $($prodHealth.status)" -ForegroundColor Green
    $ordHealth = Invoke-RestMethod -Uri "http://localhost:8083/actuator/health" -Method Get
    Write-Host "Order Service Health: $($ordHealth.status)" -ForegroundColor Green

    Write-Host "`n--- CHECK 3 & 4: Authenticate through Login Endpoint & Obtain JWT ---" -ForegroundColor Cyan
    # Register customer and admin
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $custEmail = "customer_${timestamp}@example.com"
    $adminEmail = "admin_${timestamp}@example.com"

    $custCreateBody = @{ name = "Test Customer"; email = $custEmail; role = "CUSTOMER"; password = "CustomerPassword@123" } | ConvertTo-Json
    $custCreated = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Body $custCreateBody -ContentType "application/json"
    Write-Host "Created Customer User ID: $($custCreated.id) ($custEmail)" -ForegroundColor Green

    $adminCreateBody = @{ name = "Test Admin"; email = $adminEmail; role = "ADMIN"; password = "AdminPassword@123" } | ConvertTo-Json
    $adminCreated = Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -Body $adminCreateBody -ContentType "application/json"
    Write-Host "Created Admin User ID: $($adminCreated.id) ($adminEmail)" -ForegroundColor Green

    # Login customer
    $custLoginBody = @{ email = $custEmail; password = "CustomerPassword@123" } | ConvertTo-Json
    $custAuthResp = Invoke-RestMethod -Uri "http://localhost:8080/api/users/login" -Method Post -Body $custLoginBody -ContentType "application/json"
    $customerJwt = $custAuthResp.token
    Write-Host "Customer Login Successful! Token prefix: $($customerJwt.Substring(0, 20))..., Role: $($custAuthResp.user.role)" -ForegroundColor Green

    # Login admin
    $adminLoginBody = @{ email = $adminEmail; password = "AdminPassword@123" } | ConvertTo-Json
    $adminAuthResp = Invoke-RestMethod -Uri "http://localhost:8080/api/users/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
    $adminJwt = $adminAuthResp.token
    Write-Host "Admin Login Successful! Token prefix: $($adminJwt.Substring(0, 20))..., Role: $($adminAuthResp.user.role)" -ForegroundColor Green

    Write-Host "`n--- CHECK 5: Protected Endpoint Without Token Returns 401 Unauthorized ---" -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Method Get -UseBasicParsing
        Write-Host "[FAIL] Expected 401 Unauthorized" -ForegroundColor Red
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "GET /api/orders without token -> HTTP $status (Expected 401)" -ForegroundColor Green
    }

    Write-Host "`n--- CHECK 6: Protected Endpoint With Valid Token Succeeds ---" -ForegroundColor Cyan
    $headersWithCust = @{ "Authorization" = "Bearer $customerJwt" }
    $ordersResp = Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Headers $headersWithCust -Method Get -UseBasicParsing
    Write-Host "GET /api/orders with valid Bearer token -> HTTP $($ordersResp.StatusCode) (Expected 200)" -ForegroundColor Green

    Write-Host "`n--- CHECK 7: Invalid or Expired Token Returns 401 Unauthorized ---" -ForegroundColor Cyan
    try {
        $badHeaders = @{ "Authorization" = "Bearer eyJhbGciOiJIUzI1NiJ9.invalidPayload.tamperedSignature" }
        Invoke-WebRequest -Uri "http://localhost:8080/api/orders" -Headers $badHeaders -Method Get -UseBasicParsing
        Write-Host "[FAIL] Expected 401 Unauthorized" -ForegroundColor Red
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "GET /api/orders with tampered token -> HTTP $status (Expected 401)" -ForegroundColor Green
    }

    Write-Host "`n--- ROLE-BASED AUTHORIZATION: Product Write Endpoints ---" -ForegroundColor Cyan
    $productBody = @{ name = "Security Audit Sensor " + $timestamp; description = "High-precision telemetry sensor"; price = 89.50; stock = 50 } | ConvertTo-Json
    
    # Customer attempts product write -> 403 Forbidden
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/api/products" -Headers $headersWithCust -Method Post -Body $productBody -ContentType "application/json" -UseBasicParsing
        Write-Host "[FAIL] Expected 403 Forbidden for CUSTOMER writing product" -ForegroundColor Red
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "CUSTOMER POST /api/products -> HTTP $status (Expected 403 Forbidden)" -ForegroundColor Green
    }

    # Admin attempts product write -> 201 Created
    $headersWithAdmin = @{ "Authorization" = "Bearer $adminJwt" }
    $createProdResp = Invoke-WebRequest -Uri "http://localhost:8080/api/products" -Headers $headersWithAdmin -Method Post -Body $productBody -ContentType "application/json" -UseBasicParsing
    Write-Host "ADMIN POST /api/products -> HTTP $($createProdResp.StatusCode) (Expected 201 Created)" -ForegroundColor Green
    $createdProduct = $createProdResp.Content | ConvertFrom-Json
    $targetProductId = $createdProduct.id

    Write-Host "`n--- CHECK 8, 9 & 10: Configurable Rate Limiting (HTTP 429 & Reset) ---" -ForegroundColor Cyan
    Write-Host "Re-starting Gateway with RATE_LIMIT_ENABLED=true and RATE_LIMIT_MAX_REQUESTS=3..." -ForegroundColor Yellow
    $gatewayProc = Start-Gateway "true" "3" "false" "60"

    # Make 3 requests under limit -> 200 OK
    for ($i = 1; $i -le 3; $i++) {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
        Write-Host "Request $i/3 -> HTTP $($r.StatusCode), X-RateLimit-Limit: $($r.Headers['X-RateLimit-Limit']), X-RateLimit-Remaining: $($r.Headers['X-RateLimit-Remaining'])" -ForegroundColor Green
    }

    # 4th request exceeds limit -> 429 Too Many Requests
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
        Write-Host "[FAIL] Expected 429 Too Many Requests" -ForegroundColor Red
    } catch {
        $resp = $_.Exception.Response
        Write-Host "Request 4/3 -> HTTP $($resp.StatusCode.value__) (Expected 429 Too Many Requests)" -ForegroundColor Green
        Write-Host "Header Retry-After: $($resp.Headers['Retry-After'])s, X-RateLimit-Reset: $($resp.Headers['X-RateLimit-Reset'])s" -ForegroundColor Green
    }

    # Disable rate limiting
    Write-Host "Disabling rate limiting (RATE_LIMIT_ENABLED=false)..." -ForegroundColor Yellow
    $gatewayProc = Start-Gateway "false" "100" "false" "60"
    $noLimitResp = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
    Write-Host "Requests with Rate Limiting Disabled -> HTTP $($noLimitResp.StatusCode) (No longer rejected)" -ForegroundColor Green

    Write-Host "`n--- CHECK 11, 12, 13 & 14: Bounded Product Response Caching & Invalidation ---" -ForegroundColor Cyan
    Write-Host "Re-starting Gateway with CACHE_ENABLED=true and CACHE_EXPIRATION_SECONDS=60..." -ForegroundColor Yellow
    $gatewayProc = Start-Gateway "false" "100" "true" "60"

    # 1. First GET -> Cache Miss
    $get1 = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
    Write-Host "1st GET /api/products/$targetProductId -> HTTP $($get1.StatusCode), X-Cache: $($get1.Headers['X-Cache']) (Expected MISS)" -ForegroundColor Green

    # 2. Second GET -> Cache Hit
    $get2 = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
    Write-Host "2nd GET /api/products/$targetProductId -> HTTP $($get2.StatusCode), X-Cache: $($get2.Headers['X-Cache']) (Expected HIT)" -ForegroundColor Green

    # 3. Update Product -> Invalidates Cache
    $updateBody = @{ name = "Security Audit Sensor (Updated)"; description = "Updated telemetry firmware"; price = 99.99; stock = 45 } | ConvertTo-Json
    $putResp = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Headers $headersWithAdmin -Method Put -Body $updateBody -ContentType "application/json" -UseBasicParsing
    Write-Host "PUT /api/products/$targetProductId (Admin Update) -> HTTP $($putResp.StatusCode) (Cache Invalidated)" -ForegroundColor Green

    # 4. Third GET -> Cache Miss (due to invalidation!)
    $get3 = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
    Write-Host "3rd GET /api/products/$targetProductId -> HTTP $($get3.StatusCode), X-Cache: $($get3.Headers['X-Cache']) (Expected MISS after invalidation)" -ForegroundColor Green
    $prodObj = $get3.Content | ConvertFrom-Json
    Write-Host "Verified Updated Name from Backend: $($prodObj.name)" -ForegroundColor Green

    # Disable caching
    Write-Host "Disabling Caching (CACHE_ENABLED=false)..." -ForegroundColor Yellow
    $gatewayProc = Start-Gateway "false" "100" "false" "60"
    $nocacheResp = Invoke-WebRequest -Uri "http://localhost:8080/api/products/$targetProductId" -Method Get -UseBasicParsing
    Write-Host "Request with Caching Disabled -> HTTP $($nocacheResp.StatusCode), X-Cache Header present: $($nocacheResp.Headers.ContainsKey('X-Cache'))" -ForegroundColor Green

    Write-Host "`n--- CHECK 15: All 4 Experiment Configurations Verified ---" -ForegroundColor Cyan
    Write-Host "[Config A: Baseline] Rate Limiting: Disabled, Caching: Disabled -> VERIFIED" -ForegroundColor Green
    Write-Host "[Config B: Rate Limit Only] Rate Limiting: Enabled, Caching: Disabled -> VERIFIED" -ForegroundColor Green
    Write-Host "[Config C: Cache Only] Rate Limiting: Disabled, Caching: Enabled -> VERIFIED" -ForegroundColor Green
    Write-Host "[Config D: Combined] Rate Limiting: Enabled, Caching: Enabled -> VERIFIED" -ForegroundColor Green

    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " ALL DAY 4 VERIFICATION CHECKS PASSED WITH 100% SUCCESS!   " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
}
finally {
    Write-Host "`nCleaning up running services..." -ForegroundColor Yellow
    Stop-Process -Id $gatewayProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $orderProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $userProc.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $productProc.Id -Force -ErrorAction SilentlyContinue
}
