# ==============================================================================
# Baseline Benchmark Runner for Cloud-Based API Gateway
# Day 5 Implementation Milestone: Low, Medium, and High Workloads
# ==============================================================================
param(
    [ValidateSet("low", "medium", "high", "all")]
    [string]$WorkloadLevel = "all",
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AuthToken = "",
    [switch]$SkipWarmup = $false
)

$ErrorActionPreference = "Continue"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Day 5: API Gateway Baseline Benchmarking                         " -ForegroundColor Cyan
Write-Host " Workload Level: $WorkloadLevel | Target: $GatewayUrl             " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Verify Gateway and Downstream Health
Write-Host "`n[Step 1/4] Verifying API Gateway availability..." -ForegroundColor Yellow
try {
    $gwHealth = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5 -ErrorAction Stop
    if ($gwHealth.status -eq "UP") {
        Write-Host "✓ API Gateway is UP on $GatewayUrl" -ForegroundColor Green
    } else {
        Write-Host "⚠ Gateway reported status: $($gwHealth.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[BLOCKER] API Gateway is not responding on $GatewayUrl." -ForegroundColor Red
    Write-Host "Please start the microservices before running benchmark measurements:" -ForegroundColor Yellow
    Write-Host "  1. Start User Service on port 8081"
    Write-Host "  2. Start Product Service on port 8082"
    Write-Host "  3. Start Order Service on port 8083"
    Write-Host "  4. Start API Gateway on port 8080 with baseline settings (Rate Limit & Cache disabled)"
    Write-Host "Exiting without fabricating measurements." -ForegroundColor Red
    exit 1
}

# 2. Verify Representative Target Endpoints
Write-Host "`n[Step 2/4] Verifying representative target endpoints..." -ForegroundColor Yellow
$headers = @{ "Accept" = "application/json" }
if ($AuthToken) {
    $headers["Authorization"] = "Bearer $AuthToken"
}

$endpoints = @("/api/products", "/api/users", "/api/orders")
foreach ($ep in $endpoints) {
    try {
        $resp = Invoke-WebRequest -Uri "$GatewayUrl$ep" -Headers $headers -Method Get -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        Write-Host "✓ GET $ep returned HTTP $($resp.StatusCode)" -ForegroundColor Green
    } catch {
        $sc = $_.Exception.Response.StatusCode.value__
        if ($sc -eq 401 -or $sc -eq 403) {
            Write-Host "⚠ GET $ep returned HTTP $sc (Protected by JWT - Auth token required if security is enabled)" -ForegroundColor Yellow
        } else {
            Write-Host "⚠ GET $ep returned HTTP $sc ($($_.Exception.Message))" -ForegroundColor Yellow
        }
    }
}

# 3. Detect Apache JMeter Installation
Write-Host "`n[Step 3/4] Checking for Apache JMeter installation..." -ForegroundColor Yellow
$jmeterExe = $null
if (Get-Command "jmeter" -ErrorAction SilentlyContinue) {
    $jmeterExe = "jmeter"
} elseif (Get-Command "jmeter.bat" -ErrorAction SilentlyContinue) {
    $jmeterExe = "jmeter.bat"
} elseif ($env:JMETER_HOME -and (Test-Path "$env:JMETER_HOME\bin\jmeter.bat")) {
    $jmeterExe = "$env:JMETER_HOME\bin\jmeter.bat"
}

if ($jmeterExe) {
    Write-Host "✓ Found Apache JMeter executable: $jmeterExe" -ForegroundColor Green
} else {
    Write-Host "ℹ Apache JMeter CLI was not found on PATH." -ForegroundColor Yellow
    Write-Host "  The test plan is prepared at: $scriptDir\api-gateway-baseline.jmx" -ForegroundColor Gray
    Write-Host "  To install JMeter: winget install Apache.JMeter, or download from https://jmeter.apache.org/" -ForegroundColor Gray
    Write-Host "  Using integrated PowerShell high-concurrency benchmark harness for measurement execution." -ForegroundColor Cyan
}

# 4. Define Workload Scenarios
$scenarios = @()
if ($WorkloadLevel -eq "low" -or $WorkloadLevel -eq "all") {
    $scenarios += @{
        Name = "low";
        Title = "Low Workload (5 Concurrent Users, 60s Duration)";
        Threads = 5;
        Duration = 60;
        RampUp = 10;
        WarmUp = 5;
        ResultDir = "$scriptDir\results\baseline\low"
    }
}
if ($WorkloadLevel -eq "medium" -or $WorkloadLevel -eq "all") {
    $scenarios += @{
        Name = "medium";
        Title = "Medium Workload (20 Concurrent Users, 120s Duration)";
        Threads = 20;
        Duration = 120;
        RampUp = 20;
        WarmUp = 10;
        ResultDir = "$scriptDir\results\baseline\medium"
    }
}
if ($WorkloadLevel -eq "high" -or $WorkloadLevel -eq "all") {
    $scenarios += @{
        Name = "high";
        Title = "High Workload (50 Concurrent Users, 180s Duration)";
        Threads = 50;
        Duration = 180;
        RampUp = 30;
        WarmUp = 15;
        ResultDir = "$scriptDir\results\baseline\high"
    }
}

# Helper to run a workload scenario using PowerShell HTTP client
function Execute-HarnessScenario($scenario) {
    $outDir = $scenario.ResultDir
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    $csvPath = "$outDir\results.csv"
    $jsonPath = "$outDir\summary.json"

    Write-Host "`n------------------------------------------------------------------" -ForegroundColor Magenta
    Write-Host " Executing: $($scenario.Title)" -ForegroundColor Magenta
    Write-Host " Concurrency: $($scenario.Threads) virtual users | Duration: $($scenario.Duration)s | Ramp-Up: $($scenario.RampUp)s" -ForegroundColor Magenta
    Write-Host "------------------------------------------------------------------" -ForegroundColor Magenta

    # Warm-up phase
    if (-not $SkipWarmup -and $scenario.WarmUp -gt 0) {
        Write-Host "Running warm-up period ($($scenario.WarmUp)s)..." -NoNewline
        $warmupSw = [System.Diagnostics.Stopwatch]::StartNew()
        while ($warmupSw.Elapsed.TotalSeconds -lt $scenario.WarmUp) {
            try {
                Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Headers $headers -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
            } catch {}
            Start-Sleep -Milliseconds 100
        }
        Write-Host " [Warm-up complete]" -ForegroundColor Green
    }

    # Prepare CSV Header matching standard JMeter output
    $csvHeader = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect"
    $csvLines = [System.Collections.Generic.List[string]]::new()
    $csvLines.Add($csvHeader)

    $sampleRecords = [System.Collections.Generic.List[PSObject]]::new()

    $targetEndpoints = @(
        @{ Label = "GET /api/products"; Path = "/api/products" },
        @{ Label = "GET /api/users"; Path = "/api/users" },
        @{ Label = "GET /api/orders"; Path = "/api/orders" }
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $totalDurationSec = $scenario.Duration
    $numThreads = $scenario.Threads
    $rampUpSec = $scenario.RampUp

    Write-Host "Benchmarking in progress... (target duration: ${totalDurationSec}s)" -ForegroundColor Yellow

    # High performance concurrent execution via RunspacePool
    $session = [System.Net.Http.SocketsHttpHandler]::new()
    $session.PooledConnectionLifetime = [TimeSpan]::FromMinutes(2)
    $session.PooledConnectionIdleTimeout = [TimeSpan]::FromSeconds(30)
    $session.MaxConnectionsPerServer = 100
    $httpClient = [System.Net.Http.HttpClient]::new($session)
    $httpClient.Timeout = [TimeSpan]::FromSeconds(10)

    $runspacePool = [runspacefactory]::CreateRunspacePool(1, $numThreads)
    $runspacePool.Open()

    $tasks = [System.Collections.Generic.List[PSObject]]::new()

    for ($t = 1; $t -le $numThreads; $t++) {
        $threadRampDelayMs = [int]((($t - 1) / [math]::Max(1, $numThreads)) * ($rampUpSec * 1000))
        $scriptBlock = {
            param($threadId, $rampDelay, $durationSec, $baseUrl, $endpointsList, $token)
            Start-Sleep -Milliseconds $rampDelay
            $threadClient = [System.Net.Http.HttpClient]::new()
            $threadClient.Timeout = [TimeSpan]::FromSeconds(10)

            $results = [System.Collections.Generic.List[PSObject]]::new()
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $rand = [System.Random]::new($threadId * 100 + [int]([DateTime]::UtcNow.Ticks % 1000))
            $endpointIdx = 0

            while ($sw.Elapsed.TotalSeconds -lt $durationSec) {
                $target = $endpointsList[$endpointIdx % $endpointsList.Count]
                $endpointIdx++

                $url = "$baseUrl$($target.Path)"
                $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $url)
                $req.Headers.Add("Accept", "application/json")
                $req.Headers.Add("X-Correlation-ID", "jmeter-$([Guid]::NewGuid().ToString())")
                if ($token) {
                    $req.Headers.Add("Authorization", "Bearer $token")
                }

                $tStamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                $reqSw = [System.Diagnostics.Stopwatch]::StartNew()
                $statusCode = 0
                $statusMsg = "OK"
                $isSuccess = $false
                $failMsg = ""
                $byteCount = 0

                try {
                    $resp = $threadClient.Send($req)
                    $reqSw.Stop()
                    $statusCode = [int]$resp.StatusCode
                    $statusMsg = $resp.ReasonPhrase
                    $isSuccess = ($statusCode -ge 200 -and $statusCode -lt 400)
                    if ($resp.Content) {
                        $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
                        $byteCount = if ($bytes) { $bytes.Length } else { 0 }
                    }
                } catch {
                    $reqSw.Stop()
                    $statusCode = 500
                    $statusMsg = "Connection Error"
                    $isSuccess = $false
                    $failMsg = $_.Exception.Message
                }

                $elapsedMs = [math]::Round($reqSw.Elapsed.TotalMilliseconds, 2)
                $results.Add([PSCustomObject]@{
                    TimeStamp = $tStamp
                    Elapsed = $elapsedMs
                    Label = $target.Label
                    ResponseCode = $statusCode
                    ResponseMessage = $statusMsg
                    ThreadName = "VirtualUser-$threadId"
                    Success = $isSuccess
                    FailureMessage = $failMsg
                    Bytes = $byteCount
                    Url = $url
                    Latency = $elapsedMs
                })

                # Pacing delay 200ms +/- 100ms
                $sleepMs = $rand.Next(100, 300)
                Start-Sleep -Milliseconds $sleepMs
            }

            $threadClient.Dispose()
            return $results
        }

        $ps = [powershell]::Create()
        $ps.RunspacePool = $runspacePool
        $ps.AddScript($scriptBlock).AddArgument($t).AddArgument($threadRampDelayMs).AddArgument($totalDurationSec).AddArgument($GatewayUrl).AddArgument($targetEndpoints).AddArgument($AuthToken) | Out-Null
        $asyncHandle = $ps.BeginInvoke()
        $tasks.Add([PSCustomObject]@{ Pipe = $ps; Handle = $asyncHandle; ThreadId = $t })
    }

    # Wait for all virtual users to finish
    $lastReport = 0
    while ($true) {
        $running = ($tasks | Where-Object { -not $_.Handle.IsCompleted }).Count
        $elapsedSec = [math]::Round($stopwatch.Elapsed.TotalSeconds)
        if ($elapsedSec - $lastReport -ge 10) {
            Write-Host "  ... $elapsedSec / ${totalDurationSec}s elapsed ($running active users)" -ForegroundColor Gray
            $lastReport = $elapsedSec
        }
        if ($running -eq 0) { break }
        Start-Sleep -Milliseconds 500
    }

    $allSamples = [System.Collections.Generic.List[PSObject]]::new()
    foreach ($task in $tasks) {
        $res = $task.Pipe.EndInvoke($task.Handle)
        if ($res) {
            foreach ($item in $res) {
                $allSamples.Add($item)
            }
        }
        $task.Pipe.Dispose()
    }
    $runspacePool.Close()
    $runspacePool.Dispose()

    # Sort all samples by timestamp
    $sortedSamples = $allSamples | Sort-Object TimeStamp

    foreach ($s in $sortedSamples) {
        $line = "$($s.TimeStamp),$($s.Elapsed),$($s.Label),$($s.ResponseCode),$($s.ResponseMessage),$($s.ThreadName),text,$($s.Success.ToString().ToLower()),$($s.FailureMessage),$($s.Bytes),0,$numThreads,$numThreads,$($s.Url),$($s.Latency),0,0"
        $csvLines.Add($line)
    }

    [System.IO.File]::WriteAllLines($csvPath, $csvLines)
    Write-Host "✓ Raw JMeter-compatible CSV saved to: $csvPath" -ForegroundColor Green

    # Calculate Benchmark Metrics
    $totalReqs = $sortedSamples.Count
    if ($totalReqs -eq 0) {
        Write-Host "[WARNING] No samples collected for scenario." -ForegroundColor Yellow
        return
    }

    $successCount = ($sortedSamples | Where-Object { $_.Success }).Count
    $failCount = $totalReqs - $successCount
    $errorRate = [math]::Round(($failCount / $totalReqs) * 100, 2)

    $allElapsed = $sortedSamples | ForEach-Object { [double]$_.Elapsed } | Sort-Object
    $avgResp = [math]::Round(($allElapsed | Measure-Object -Average).Average, 2)
    $p95Index = [math]::Min([int]($totalReqs * 0.95), $totalReqs - 1)
    $p99Index = [math]::Min([int]($totalReqs * 0.99), $totalReqs - 1)
    $p95 = $allElapsed[$p95Index]
    $p99 = $allElapsed[$p99Index]

    $minTs = ($sortedSamples | Measure-Object -Property TimeStamp -Minimum).Minimum
    $maxTs = ($sortedSamples | Measure-Object -Property TimeStamp -Maximum).Maximum
    $durSec = if ($maxTs -gt $minTs) { ($maxTs -minTs) / 1000 } else { $totalDurationSec }
    $throughput = [math]::Round($totalReqs / [math]::Max(1, $durSec), 2)

    # Per-Endpoint breakdown
    $endpointGroups = $sortedSamples | Group-Object Label
    $endpointStats = @()
    foreach ($grp in $endpointGroups) {
        $grpElapsed = $grp.Group | ForEach-Object { [double]$_.Elapsed } | Sort-Object
        $grpTotal = $grp.Count
        $grpFail = ($grp.Group | Where-Object { -not $_.Success }).Count
        $grpP95 = $grpElapsed[[math]::Min([int]($grpTotal * 0.95), $grpTotal - 1)]
        $grpP99 = $grpElapsed[[math]::Min([int]($grpTotal * 0.99), $grpTotal - 1)]
        $endpointStats += @{
            label = $grp.Name
            total = $grpTotal
            success = $grpTotal - $grpFail
            failed = $grpFail
            errorRate = [math]::Round(($grpFail / $grpTotal) * 100, 2)
            avg = [math]::Round(($grpElapsed | Measure-Object -Average).Average, 2)
            p95 = $grpP95
            p99 = $grpP99
        }
    }

    $summaryObj = [ordered]@{
        workload = $scenario.Title
        timestamp = [DateTime]::UtcNow.ToString("o")
        totalRequests = $totalReqs
        successfulRequests = $successCount
        failedRequests = $failCount
        errorRate = $errorRate
        avgResponseTime = $avgResp
        p95 = $p95
        p99 = $p99
        throughput = $throughput
        durationSeconds = [math]::Round($durSec, 1)
        virtualUsers = $numThreads
        endpoints = $endpointStats
    }

    $summaryJson = $summaryObj | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($jsonPath, $summaryJson)
    Write-Host "✓ Summary JSON saved to: $jsonPath" -ForegroundColor Green

    # Output formatted console table
    Write-Host "`n--- Summary Metrics for $($scenario.Name.ToUpper()) Workload ---" -ForegroundColor Cyan
    Write-Host ("  Total Requests:       {0}" -f $totalReqs)
    Write-Host ("  Successful Requests:  {0}" -f $successCount)
    Write-Host ("  Failed Requests:      {0}" -f $failCount)
    Write-Host ("  HTTP Error Rate:      {0}%" -f $errorRate) -ForegroundColor $(if ($errorRate -eq 0) { "Green" } else { "Red" })
    Write-Host ("  Average Latency:      {0} ms" -f $avgResp)
    Write-Host ("  p95 Latency:          {0} ms" -f $p95) -ForegroundColor Yellow
    Write-Host ("  p99 Latency:          {0} ms" -f $p99) -ForegroundColor Yellow
    Write-Host ("  Throughput:           {0} req/sec" -f $throughput) -ForegroundColor Green
}

# Execute selected scenarios
foreach ($sc in $scenarios) {
    if ($jmeterExe) {
        $propFile = "$scriptDir\$($sc.Name)-workload.properties"
        $csvOutput = "$($sc.ResultDir)\results.csv"
        New-Item -ItemType Directory -Path $sc.ResultDir -Force | Out-Null

        Write-Host "`nRunning Apache JMeter CLI for $($sc.Title)..." -ForegroundColor Yellow
        $jmeterArgs = @(
            "-n",
            "-t", "$scriptDir\api-gateway-baseline.jmx",
            "-q", $propFile,
            "-l", $csvOutput
        )
        if ($AuthToken) {
            $jmeterArgs += "-Jauth.token=$AuthToken"
        }

        & $jmeterExe $jmeterArgs
        Write-Host "✓ JMeter run finished. Output: $csvOutput" -ForegroundColor Green
    } else {
        Execute-HarnessScenario $sc
    }
}

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " Baseline Benchmarking Execution Complete!                        " -ForegroundColor Cyan
Write-Host " You can now import the generated CSV/JSON files into the         " -ForegroundColor Cyan
Write-Host " 'Performance Experiments' tab on the React dashboard.            " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

