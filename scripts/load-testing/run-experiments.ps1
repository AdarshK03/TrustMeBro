# ==============================================================================
# Controlled Experiment Runner for API Gateway Optimizations (Day 6)
# Supports:
#   - Baseline (A)
#   - Rate Limiting Only (B)
#   - Response Caching Only (C)
#   - Combined Optimizations (D)
# Across Low (5 users, 60s), Medium (20 users, 120s), and High (50 users, 180s)
# Repeats each experiment at least 3 times.
# ==============================================================================
param(
    [ValidateSet("baseline", "rate-limit-only", "cache-only", "combined", "all")]
    [string]$Configuration = "all",

    [ValidateSet("low", "medium", "high", "all")]
    [string]$WorkloadLevel = "all",

    [int]$Repetitions = 3,
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AuthToken = "",
    [switch]$SkipWarmup = $false
)

$ErrorActionPreference = "Continue"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Day 6: Controlled API Gateway Optimization Experiments           " -ForegroundColor Cyan
Write-Host " Target: $GatewayUrl | Config: $Configuration | Workload: $WorkloadLevel " -ForegroundColor Cyan
Write-Host " Repetitions: $Repetitions runs per configuration                 " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Verify Gateway Health and Optimization Endpoint
Write-Host "`n[Step 1/3] Verifying API Gateway connectivity & telemetry..." -ForegroundColor Yellow
$stats = $null
try {
    $gwHealth = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Host "✓ API Gateway is UP on $GatewayUrl" -ForegroundColor Green
    
    # Query /api/gateway/stats
    try {
        $stats = Invoke-RestMethod -Uri "$GatewayUrl/api/gateway/stats" -Method Get -TimeoutSec 3 -ErrorAction Stop
        Write-Host "✓ Gateway telemetry active: RateLimit=$($stats.rateLimiting.enabled), Cache=$($stats.caching.enabled)" -ForegroundColor Green
    } catch {
        Write-Host "ℹ /api/gateway/stats endpoint not yet reachable; telemetry will rely on HTTP headers." -ForegroundColor Gray
    }
} catch {
    Write-Host "[BLOCKER] API Gateway is not responding on $GatewayUrl." -ForegroundColor Red
    Write-Host "Please start the microservices before executing experiments:" -ForegroundColor Yellow
    Write-Host "  User Service (8081), Product Service (8082), Order Service (8083)"
    Write-Host "  API Gateway (8080) with desired optimization configuration."
    Write-Host "Exiting without fabricating measurements." -ForegroundColor Red
    exit 1
}

# 2. Check for Apache JMeter
$jmeterExe = $null
if (Get-Command "jmeter" -ErrorAction SilentlyContinue) {
    $jmeterExe = "jmeter"
} elseif (Get-Command "jmeter.bat" -ErrorAction SilentlyContinue) {
    $jmeterExe = "jmeter.bat"
} elseif ($env:JMETER_HOME -and (Test-Path "$env:JMETER_HOME\bin\jmeter.bat")) {
    $jmeterExe = "$env:JMETER_HOME\bin\jmeter.bat"
}

if ($jmeterExe) {
    Write-Host "✓ Detected Apache JMeter: $jmeterExe" -ForegroundColor Green
} else {
    Write-Host "ℹ Apache JMeter CLI not on PATH; using high-concurrency PowerShell runspace benchmark harness." -ForegroundColor Cyan
}

# 3. Workload Definitions
$workloadMap = @{
    "low" = @{ Threads = 5; Duration = 60; RampUp = 10; WarmUp = 5 };
    "medium" = @{ Threads = 20; Duration = 120; RampUp = 20; WarmUp = 10 };
    "high" = @{ Threads = 50; Duration = 180; RampUp = 30; WarmUp = 15 }
}

$workloadsToRun = if ($WorkloadLevel -eq "all") { @("low", "medium", "high") } else { @($WorkloadLevel) }
$configsToRun = if ($Configuration -eq "all") { @("baseline", "rate-limit-only", "cache-only", "combined") } else { @($Configuration) }

# Helper to verify gateway state matches target configuration
function Test-GatewayConfigurationMatch($cfgName) {
    try {
        $st = Invoke-RestMethod -Uri "$GatewayUrl/api/gateway/stats" -Method Get -TimeoutSec 3 -ErrorAction Stop
        $rl = $st.rateLimiting.enabled
        $cache = $st.caching.enabled

        $expectedRl = ($cfgName -eq "rate-limit-only" -or $cfgName -eq "combined")
        $expectedCache = ($cfgName -eq "cache-only" -or $cfgName -eq "combined")

        if ($rl -ne $expectedRl -or $cache -ne $expectedCache) {
            Write-Host "`n[CONFIGURATION MISMATCH WARNING]" -ForegroundColor Red
            Write-Host "Currently running Gateway configuration does not match target '$cfgName':" -ForegroundColor Yellow
            Write-Host "  Active: RateLimiting=$rl, Caching=$cache"
            Write-Host "  Target '$cfgName': RateLimiting=$expectedRl, Caching=$expectedCache"
            Write-Host "To switch configurations, restart Gateway with:" -ForegroundColor Cyan
            if ($cfgName -eq "baseline") {
                Write-Host "  java -DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar" -ForegroundColor White
            } elseif ($cfgName -eq "rate-limit-only") {
                Write-Host "  java -DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DCACHE_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar" -ForegroundColor White
            } elseif ($cfgName -eq "cache-only") {
                Write-Host "  java -DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60 -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar" -ForegroundColor White
            } elseif ($cfgName -eq "combined") {
                Write-Host "  java -DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60 -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar" -ForegroundColor White
            }
            return $false
        }
        return $true
    } catch {
        return $true
    }
}

# Function to execute a single run using the multi-threaded harness
function Execute-HarnessRun($cfg, $wl, $runIdx) {
    $wlDef = $workloadMap[$wl]
    $numThreads = $wlDef.Threads
    $durationSec = $wlDef.Duration
    $rampUpSec = $wlDef.RampUp
    $warmUpSec = if ($SkipWarmup) { 0 } else { $wlDef.WarmUp }

    $outDir = "$scriptDir\results\$cfg"
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    $csvFile = "$outDir\${cfg}_${wl}_run${runIdx}.csv"
    $jsonFile = "$outDir\${cfg}_${wl}_run${runIdx}_summary.json"

    Write-Host "`n>>> Running: Config=[$cfg] | Workload=[$wl] | Run #$runIdx / $Repetitions" -ForegroundColor Magenta
    Write-Host "    Threads: $numThreads | Duration: ${durationSec}s | RampUp: ${rampUpSec}s" -ForegroundColor Gray

    # Reset cache and rate limit state before the run
    try {
        Invoke-RestMethod -Uri "$GatewayUrl/api/gateway/cache/reset" -Method Post -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
        Invoke-RestMethod -Uri "$GatewayUrl/api/gateway/rate-limit/reset" -Method Post -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
    } catch {}

    # Warm-up phase
    if ($warmUpSec -gt 0) {
        Write-Host "    Pacing warm-up (${warmUpSec}s)..." -NoNewline
        $wuSw = [System.Diagnostics.Stopwatch]::StartNew()
        while ($wuSw.Elapsed.TotalSeconds -lt $warmUpSec) {
            try {
                Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
            } catch {}
            Start-Sleep -Milliseconds 150
        }
        Write-Host " [Ready]" -ForegroundColor Green
    }

    # Prepare RunspacePool for concurrent threads
    $csvHeader = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect"
    $csvLines = [System.Collections.Generic.List[string]]::new()
    $csvLines.Add($csvHeader)

    $targetEndpoints = @(
        @{ Label = "GET /api/products"; Path = "/api/products" },
        @{ Label = "GET /api/users"; Path = "/api/users" },
        @{ Label = "GET /api/orders"; Path = "/api/orders" }
    )

    $runspacePool = [runspacefactory]::CreateRunspacePool(1, $numThreads)
    $runspacePool.Open()
    $tasks = [System.Collections.Generic.List[PSObject]]::new()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    for ($t = 1; $t -le $numThreads; $t++) {
        $rampDelayMs = [int]((($t - 1) / [math]::Max(1, $numThreads)) * ($rampUpSec * 1000))
        $scriptBlock = {
            param($threadId, $delayMs, $dur, $base, $epList, $tok)
            Start-Sleep -Milliseconds $delayMs
            $client = [System.Net.Http.HttpClient]::new()
            $client.Timeout = [TimeSpan]::FromSeconds(10)
            $records = [System.Collections.Generic.List[PSObject]]::new()
            $thSw = [System.Diagnostics.Stopwatch]::StartNew()
            $rnd = [System.Random]::new($threadId * 1000 + [int]([DateTime]::UtcNow.Ticks % 1000))
            $idx = 0

            while ($thSw.Elapsed.TotalSeconds -lt $dur) {
                $target = $epList[$idx % $epList.Count]
                $idx++

                $url = "$base$($target.Path)"
                $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $url)
                $req.Headers.Add("Accept", "application/json")
                $req.Headers.Add("X-Correlation-ID", "jmeter-$([Guid]::NewGuid().ToString())")
                if ($tok) {
                    $req.Headers.Add("Authorization", "Bearer $tok")
                }

                $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                $reqSw = [System.Diagnostics.Stopwatch]::StartNew()
                $sc = 0
                $msg = "OK"
                $ok = $false
                $fail = ""
                $bytes = 0
                $cacheHeader = "N/A"

                try {
                    $resp = $client.Send($req)
                    $reqSw.Stop()
                    $sc = [int]$resp.StatusCode
                    $msg = $resp.ReasonPhrase
                    $ok = ($sc -ge 200 -and $sc -lt 400)
                    if ($resp.Headers.Contains("X-Cache")) {
                        $cacheHeader = [string]::Join(",", $resp.Headers.GetValues("X-Cache"))
                    }
                    if ($resp.Content) {
                        $raw = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
                        $bytes = if ($raw) { $raw.Length } else { 0 }
                    }
                } catch {
                    $reqSw.Stop()
                    $sc = 500
                    $msg = "Network/Gateway Failure"
                    $ok = $false
                    $fail = $_.Exception.Message
                }

                $elapsed = [math]::Round($reqSw.Elapsed.TotalMilliseconds, 2)
                $records.Add([PSCustomObject]@{
                    TimeStamp = $ts
                    Elapsed = $elapsed
                    Label = $target.Label
                    ResponseCode = $sc
                    ResponseMessage = $msg
                    ThreadName = "VirtualUser-$threadId"
                    Success = $ok
                    FailureMessage = $fail
                    Bytes = $bytes
                    Url = $url
                    Latency = $elapsed
                    CacheHeader = $cacheHeader
                })

                # Pacing delay 200ms +/- 100ms
                $sleep = $rnd.Next(100, 300)
                Start-Sleep -Milliseconds $sleep
            }
            $client.Dispose()
            return $records
        }

        $ps = [powershell]::Create()
        $ps.RunspacePool = $runspacePool
        $ps.AddScript($scriptBlock).AddArgument($t).AddArgument($rampDelayMs).AddArgument($durationSec).AddArgument($GatewayUrl).AddArgument($targetEndpoints).AddArgument($AuthToken) | Out-Null
        $asyncHandle = $ps.BeginInvoke()
        $tasks.Add([PSCustomObject]@{ Pipe = $ps; Handle = $asyncHandle; ThreadId = $t })
    }

    # Progress loop
    $lastReport = 0
    while ($true) {
        $active = ($tasks | Where-Object { -not $_.Handle.IsCompleted }).Count
        $elapsed = [math]::Round($sw.Elapsed.TotalSeconds)
        if ($elapsed - $lastReport -ge 15) {
            Write-Host "    ... $elapsed / ${durationSec}s ($active active virtual users)" -ForegroundColor Gray
            $lastReport = $elapsed
        }
        if ($active -eq 0) { break }
        Start-Sleep -Milliseconds 500
    }

    $allSamples = [System.Collections.Generic.List[PSObject]]::new()
    foreach ($task in $tasks) {
        $res = $task.Pipe.EndInvoke($task.Handle)
        if ($res) {
            foreach ($it in $res) {
                $allSamples.Add($it)
            }
        }
        $task.Pipe.Dispose()
    }
    $runspacePool.Close()
    $runspacePool.Dispose()

    $sortedSamples = $allSamples | Sort-Object TimeStamp
    $totalReqs = $sortedSamples.Count

    if ($totalReqs -eq 0) {
        Write-Host "    [WARNING] No samples collected for this run." -ForegroundColor Yellow
        return
    }

    # Write CSV
    foreach ($s in $sortedSamples) {
        $line = "$($s.TimeStamp),$($s.Elapsed),$($s.Label),$($s.ResponseCode),$($s.ResponseMessage),$($s.ThreadName),text,$($s.Success.ToString().ToLower()),$($s.FailureMessage),$($s.Bytes),0,$numThreads,$numThreads,$($s.Url),$($s.Latency),0,0"
        $csvLines.Add($line)
    }
    [System.IO.File]::WriteAllLines($csvFile, $csvLines)

    # Metrics calculation
    $successCount = ($sortedSamples | Where-Object { $_.Success }).Count
    $failedCount = $totalReqs - $successCount
    $rejections429 = ($sortedSamples | Where-Object { [string]$_.ResponseCode -eq "429" }).Count
    $errors5xx = ($sortedSamples | Where-Object { [string]$_.ResponseCode -like "5*" }).Count
    $errorRate = [math]::Round(($failedCount / $totalReqs) * 100, 2)

    $allElapsed = $sortedSamples | ForEach-Object { [double]$_.Elapsed } | Sort-Object
    $avgLatency = [math]::Round(($allElapsed | Measure-Object -Average).Average, 2)
    $p95 = $allElapsed[[math]::Min([int]($totalReqs * 0.95), $totalReqs - 1)]
    $p99 = if ($totalReqs -ge 100) { $allElapsed[[math]::Min([int]($totalReqs * 0.99), $totalReqs - 1)] } else { $null }

    $minTs = ($sortedSamples | Measure-Object -Property TimeStamp -Minimum).Minimum
    $maxTs = ($sortedSamples | Measure-Object -Property TimeStamp -Maximum).Maximum
    $durSec = if ($maxTs -gt $minTs) { ($maxTs - $minTs) / 1000 } else { $durationSec }
    $throughput = [math]::Round($totalReqs / [math]::Max(1, $durSec), 2)

    # Cache hit/miss counts from application telemetry
    $finalStats = $null
    try {
        $finalStats = Invoke-RestMethod -Uri "$GatewayUrl/api/gateway/stats" -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
    } catch {}

    $cacheHits = if ($finalStats -and $finalStats.caching) { [int]$finalStats.caching.hitCount } else { ($sortedSamples | Where-Object { $_.CacheHeader -eq "HIT" }).Count }
    $cacheMisses = if ($finalStats -and $finalStats.caching) { [int]$finalStats.caching.missCount } else { ($sortedSamples | Where-Object { $_.CacheHeader -eq "MISS" }).Count }
    $totalLookups = $cacheHits + $cacheMisses
    $cacheHitRatio = if ($totalLookups -gt 0) { [math]::Round(($cacheHits / $totalLookups) * 100, 2) } else { 0.0 }

    # Save summary JSON
    $summaryObj = [ordered]@{
        configuration = $cfg
        workload = $wl
        run = $runIdx
        timestamp = [DateTime]::UtcNow.ToString("o")
        totalRequests = $totalReqs
        successfulRequests = $successCount
        failedRequests = $failedCount
        rejections429 = $rejections429
        backendErrors5xx = $errors5xx
        errorRate = $errorRate
        avgResponseTime = $avgLatency
        p95 = $p95
        p99 = $p99
        throughput = $throughput
        durationSeconds = [math]::Round($durSec, 1)
        cacheHits = $cacheHits
        cacheMisses = $cacheMisses
        cacheHitRatio = $cacheHitRatio
    }
    $summaryJson = $summaryObj | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($jsonFile, $summaryJson)

    Write-Host "    ✓ Completed Run #$runIdx: Avg=${avgLatency}ms | p95=${p95}ms | TP=${throughput} req/s | 429s=$rejections429 | CacheHitRatio=${cacheHitRatio}%" -ForegroundColor Green
}

# Main execution loop across selected configurations and workloads
foreach ($cfg in $configsToRun) {
    Write-Host "`n==================================================================" -ForegroundColor Yellow
    Write-Host " CONFIGURATION: $cfg" -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Yellow

    $matches = Test-GatewayConfigurationMatch $cfg
    if (-not $matches) {
        Write-Host "Skipping automatic execution of '$cfg' until Gateway is restarted with required switches." -ForegroundColor Yellow
        continue
    }

    foreach ($wl in $workloadsToRun) {
        for ($r = 1; $r -le $Repetitions; $r++) {
            Execute-HarnessRun $cfg $wl $r
            if ($r -lt $Repetitions) {
                Start-Sleep -Seconds 2
            }
        }
    }
}

# 4. Trigger Analysis
Write-Host "`n[Final Step] Running Performance Analysis Tool..." -ForegroundColor Yellow
python "$scriptDir\analysis\analyze_results.py"

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " Day 6 Experiments & Analysis Complete!                           " -ForegroundColor Cyan
Write-Host " Check scripts/load-testing/analysis/comparison_report.md         " -ForegroundColor Cyan
Write-Host " and import into the React dashboard.                             " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

