import React, { useState, useEffect } from 'react';
import ErrorDisplay from './ErrorDisplay';
import { gatewayApi } from '../api/apiClient';

const CONFIG_NAMES = {
  baseline: 'Baseline (A)',
  'rate-limit-only': 'Rate Limiting Only (B)',
  'cache-only': 'Response Caching Only (C)',
  combined: 'Combined Optimizations (D)'
};

const CONFIG_COLORS = {
  baseline: '#94a3b8',
  'rate-limit-only': '#f59e0b',
  'cache-only': '#3b82f6',
  combined: '#10b981'
};

export default function PerformanceExperiments() {
  // Datasets repository: { [configKey]: { [workloadKey]: metricObject } }
  const [datasetMap, setDatasetMap] = useState({});
  const [selectedConfig, setSelectedConfig] = useState('comparison'); // 'comparison' | 'baseline' | 'rate-limit-only' | 'cache-only' | 'combined'
  const [selectedWorkload, setSelectedWorkload] = useState('all'); // 'all' | 'low' | 'medium' | 'high'
  const [chartMetric, setChartMetric] = useState('avg_latency'); // 'avg_latency' | 'p95' | 'throughput' | 'error_rate' | 'cache_hit_ratio'

  // Live Gateway Telemetry state
  const [gatewayStats, setGatewayStats] = useState(null);
  const [loadingGatewayStats, setLoadingGatewayStats] = useState(false);

  // Single imported file raw view state
  const [importedFileInfo, setImportedFileInfo] = useState(null);
  const [singleMetrics, setSingleMetrics] = useState(null);
  const [rawRows, setRawRows] = useState([]);
  const [rawText, setRawText] = useState('');
  const [parseError, setParseError] = useState(null);
  const [activeTab, setActiveTab] = useState('comparison'); // 'comparison' | 'summary' | 'telemetry' | 'raw'

  const fetchGatewayStats = async () => {
    setLoadingGatewayStats(true);
    try {
      const res = await gatewayApi.getStats();
      setGatewayStats(res.data);
    } catch {
      setGatewayStats(null);
    } finally {
      setLoadingGatewayStats(false);
    }
  };

  useEffect(() => {
    fetchGatewayStats();
  }, []);

  const handleFileUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setParseError(null);
    setImportedFileInfo({
      name: file.name,
      size: `${(file.size / 1024).toFixed(1)} KB`,
      type: file.name.endsWith('.json') ? 'JSON' : file.name.endsWith('.csv') ? 'CSV' : 'Unknown',
      lastModified: new Date(file.lastModified).toLocaleString()
    });

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result;
      if (typeof content !== 'string') return;
      setRawText(content);
      parseFileContent(content, file.name);
    };
    reader.onerror = () => {
      setParseError({
        status: 400,
        error: 'File Read Error',
        message: 'Failed to read uploaded file from local filesystem.',
        path: file.name
      });
    };
    reader.readAsText(file);
  };

  const parseFileContent = (text, fileName) => {
    try {
      if (fileName.toLowerCase().endsWith('.json')) {
        parseJsonContent(text, fileName);
      } else {
        parseCsvContent(text, fileName);
      }
    } catch (err) {
      setParseError({
        status: 400,
        error: 'Parsing Error',
        message: `Failed to parse ${fileName}: ${err.message}`,
        path: fileName
      });
    }
  };

  const parseJsonContent = (jsonString, fileName) => {
    const parsed = JSON.parse(jsonString);

    // Case 1: Full multi-config comparison summary (comparison_summary.json from analyze_results.py)
    if (parsed.baseline || parsed['rate-limit-only'] || parsed['cache-only'] || parsed.combined) {
      setDatasetMap(parsed);
      setSelectedConfig('comparison');
      setActiveTab('comparison');
      setSingleMetrics(null);
      return;
    }

    // Case 2: Single run summary JSON
    const detectedConfig = inferConfigFromName(fileName) || parsed.configuration || 'baseline';
    const detectedWl = inferWorkloadFromName(fileName) || parsed.workload || 'low';

    const normalized = {
      workloadTitle: parsed.workload || `${detectedConfig.toUpperCase()} (${detectedWl})`,
      totalRequests: parsed.totalRequests ?? 0,
      successfulRequests: parsed.successfulRequests ?? 0,
      failedRequests: parsed.failedRequests ?? 0,
      rejections429: parsed.rejections429 ?? parsed.rejections_429 ?? 0,
      errorRate: parsed.errorRate !== undefined ? Number(parsed.errorRate).toFixed(2) : '0.00',
      avgResponseTime: parsed.avgResponseTime !== undefined ? Number(parsed.avgResponseTime).toFixed(2) : (parsed.avg_latency !== undefined ? Number(parsed.avg_latency).toFixed(2) : '0.00'),
      p95Latency: parsed.p95 !== undefined ? Number(parsed.p95).toFixed(2) : (parsed.p95_latency !== undefined ? Number(parsed.p95_latency).toFixed(2) : '—'),
      p99Latency: parsed.p99 !== undefined ? Number(parsed.p99).toFixed(2) : (parsed.p99_latency !== undefined ? Number(parsed.p99_latency).toFixed(2) : '—'),
      throughput: parsed.throughput !== undefined ? Number(parsed.throughput).toFixed(2) : '—',
      cacheHits: parsed.cacheHits ?? parsed.cache_hits ?? 0,
      cacheMisses: parsed.cacheMisses ?? parsed.cache_misses ?? 0,
      cacheHitRatio: parsed.cacheHitRatio !== undefined ? Number(parsed.cacheHitRatio).toFixed(2) : (parsed.cache_hit_ratio !== undefined ? Number(parsed.cache_hit_ratio).toFixed(2) : '0.00')
    };

    setSingleMetrics(normalized);
    setDatasetMap(prev => ({
      ...prev,
      [detectedConfig]: {
        ...(prev[detectedConfig] || {}),
        [detectedWl]: {
          runs_count: 1,
          avg_latency: parseFloat(normalized.avgResponseTime),
          p95_latency: parseFloat(normalized.p95Latency) || 0,
          p99_latency: parseFloat(normalized.p99Latency) || null,
          throughput: parseFloat(normalized.throughput) || 0,
          total_requests: normalized.totalRequests,
          successful_requests: normalized.successfulRequests,
          failed_requests: normalized.failedRequests,
          rejections_429: normalized.rejections429,
          error_rate: parseFloat(normalized.errorRate),
          cache_hit_ratio: parseFloat(normalized.cacheHitRatio)
        }
      }
    }));
    setActiveTab('summary');
  };

  const parseCsvContent = (csvString, fileName) => {
    const lines = csvString.trim().split(/\r?\n/).filter(line => line.trim().length > 0);
    if (lines.length < 2) {
      throw new Error('CSV file contains insufficient data (minimum 1 header and 1 data row required).');
    }

    const headerParts = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''));
    const elapsedIdx = headerParts.findIndex(h => /^(elapsed|responseTime|latency)$/i.test(h));
    const labelIdx = headerParts.findIndex(h => /^(label|sampler_label|endpoint|url)$/i.test(h));
    const codeIdx = headerParts.findIndex(h => /^(responseCode|status|httpStatus)$/i.test(h));
    const successIdx = headerParts.findIndex(h => /^success$/i.test(h));
    const timeStampIdx = headerParts.findIndex(h => /^timeStamp$/i.test(h));

    if (elapsedIdx === -1 && codeIdx === -1) {
      throw new Error(`CSV missing essential columns. Detected headers: ${headerParts.join(', ')}`);
    }

    const samples = [];
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',').map(p => p.trim().replace(/^"|"$/g, ''));
      if (parts.length < headerParts.length * 0.6) continue;

      const elapsed = elapsedIdx !== -1 ? parseFloat(parts[elapsedIdx]) || 0 : 0;
      const label = labelIdx !== -1 ? parts[labelIdx] : 'Request';
      const code = codeIdx !== -1 ? parts[codeIdx] : '200';
      const rawSuccess = successIdx !== -1 ? parts[successIdx]?.toLowerCase() : null;
      const isSuccess = rawSuccess !== null ? (rawSuccess === 'true' || rawSuccess === '1') : (parseInt(code, 10) >= 200 && parseInt(code, 10) < 400);
      const timeStamp = timeStampIdx !== -1 ? parseInt(parts[timeStampIdx], 10) : null;

      samples.push({
        id: i,
        elapsed,
        label,
        responseCode: code,
        success: isSuccess,
        timeStamp
      });
    }

    if (samples.length === 0) {
      throw new Error('No valid sample data rows could be extracted.');
    }

    // Calculations
    const totalRequests = samples.length;
    let successfulRequests = 0;
    let failedRequests = 0;
    let rejections429 = 0;
    let sumElapsed = 0;
    const elapsedValues = [];
    let minTime = Infinity;
    let maxTime = -Infinity;

    samples.forEach(s => {
      elapsedValues.push(s.elapsed);
      sumElapsed += s.elapsed;
      if (s.success) successfulRequests++;
      else failedRequests++;
      if (s.responseCode === '429') rejections429++;
      if (s.timeStamp) {
        if (s.timeStamp < minTime) minTime = s.timeStamp;
        if (s.timeStamp > maxTime) maxTime = s.timeStamp;
      }
    });

    elapsedValues.sort((a, b) => a - b);
    const avgResponseTime = (sumElapsed / totalRequests).toFixed(2);
    const p95Latency = elapsedValues[Math.min(Math.floor(totalRequests * 0.95), totalRequests - 1)].toFixed(2);
    const p99Latency = totalRequests >= 100 ? elapsedValues[Math.min(Math.floor(totalRequests * 0.99), totalRequests - 1)].toFixed(2) : '—';
    const errorRate = ((failedRequests / totalRequests) * 100).toFixed(2);

    let throughput = '—';
    if (minTime !== Infinity && maxTime !== -Infinity && maxTime > minTime) {
      const durSec = (maxTime - minTime) / 1000;
      if (durSec > 0) throughput = (totalRequests / durSec).toFixed(2);
    }

    const detectedConfig = inferConfigFromName(fileName) || 'baseline';
    const detectedWl = inferWorkloadFromName(fileName) || 'low';

    const calculated = {
      workloadTitle: `${detectedConfig.toUpperCase()} — ${detectedWl.toUpperCase()} Workload`,
      totalRequests,
      successfulRequests,
      failedRequests,
      rejections429,
      errorRate,
      avgResponseTime,
      p95Latency,
      p99Latency,
      throughput,
      cacheHits: 0,
      cacheMisses: 0,
      cacheHitRatio: '0.00'
    };

    setSingleMetrics(calculated);
    setRawRows(samples.slice(0, 150));

    setDatasetMap(prev => ({
      ...prev,
      [detectedConfig]: {
        ...(prev[detectedConfig] || {}),
        [detectedWl]: {
          runs_count: 1,
          avg_latency: parseFloat(avgResponseTime),
          p95_latency: parseFloat(p95Latency) || 0,
          p99_latency: p99Latency !== '—' ? parseFloat(p99Latency) : null,
          throughput: throughput !== '—' ? parseFloat(throughput) : 0,
          total_requests: totalRequests,
          successful_requests: successfulRequests,
          failed_requests: failedRequests,
          rejections_429: rejections429,
          error_rate: parseFloat(errorRate),
          cache_hit_ratio: 0.0
        }
      }
    }));
    setActiveTab('summary');
  };

  const inferConfigFromName = (name) => {
    const lower = name.toLowerCase();
    if (lower.includes('rate') || lower.includes('limit')) return 'rate-limit-only';
    if (lower.includes('cache')) return 'cache-only';
    if (lower.includes('combined')) return 'combined';
    if (lower.includes('baseline')) return 'baseline';
    return null;
  };

  const inferWorkloadFromName = (name) => {
    const lower = name.toLowerCase();
    if (lower.includes('high')) return 'high';
    if (lower.includes('med')) return 'medium';
    if (lower.includes('low')) return 'low';
    return null;
  };

  const handleClear = () => {
    setDatasetMap({});
    setImportedFileInfo(null);
    setSingleMetrics(null);
    setRawRows([]);
    setRawText('');
    setParseError(null);
  };

  // Helper to extract metric value from datasetMap
  const getMetric = (cfg, wl, key) => {
    const entry = datasetMap?.[cfg]?.[wl];
    if (!entry) return null;
    return entry[key] ?? null;
  };

  const hasAnyData = Object.keys(datasetMap).length > 0 || singleMetrics !== null;

  // Chart rendering helper
  const renderComparisonChart = () => {
    const workloads = selectedWorkload === 'all' ? ['low', 'medium', 'high'] : [selectedWorkload];
    const configs = ['baseline', 'rate-limit-only', 'cache-only', 'combined'];

    let maxVal = 10;
    workloads.forEach(wl => {
      configs.forEach(cfg => {
        const v = getMetric(cfg, wl, chartMetric);
        if (typeof v === 'number' && v > maxVal) maxVal = v;
      });
    });
    maxVal = maxVal * 1.15;

    const chartHeight = 220;
    const barWidth = 24;

    return (
      <div className="interactive-chart-container">
        <div className="chart-header">
          <span style={{ fontWeight: 600, fontSize: '0.95rem' }}>
            Visual Comparison: {chartMetric.replace('_', ' ').toUpperCase()} across Configurations
          </span>
          <div className="chart-metric-selector">
            <button
              className={`pill-btn ${chartMetric === 'avg_latency' ? 'active' : ''}`}
              onClick={() => setChartMetric('avg_latency')}
            >
              Avg Latency (ms)
            </button>
            <button
              className={`pill-btn ${chartMetric === 'p95_latency' ? 'active' : ''}`}
              onClick={() => setChartMetric('p95_latency')}
            >
              p95 (ms)
            </button>
            <button
              className={`pill-btn ${chartMetric === 'throughput' ? 'active' : ''}`}
              onClick={() => setChartMetric('throughput')}
            >
              Throughput (req/s)
            </button>
            <button
              className={`pill-btn ${chartMetric === 'error_rate' ? 'active' : ''}`}
              onClick={() => setChartMetric('error_rate')}
            >
              Error Rate (%)
            </button>
            <button
              className={`pill-btn ${chartMetric === 'cache_hit_ratio' ? 'active' : ''}`}
              onClick={() => setChartMetric('cache_hit_ratio')}
            >
              Cache Hit %
            </button>
          </div>
        </div>

        <div className="svg-chart-wrapper">
          <svg viewBox="0 0 760 260" width="100%" height="260">
            {/* Grid Lines */}
            {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => {
              const y = 30 + (chartHeight - 30) * (1 - pct);
              const val = (maxVal * pct).toFixed(1);
              return (
                <g key={idx}>
                  <line x1="50" y1={y} x2="730" y2={y} stroke="#1f2937" strokeDasharray="3 3" />
                  <text x="42" y={y + 4} fill="#64748b" fontSize="10" textAnchor="end">{val}</text>
                </g>
              );
            })}

            {/* Bars */}
            {workloads.map((wl, wIdx) => {
              const groupW = 680 / workloads.length;
              const groupX = 50 + wIdx * groupW;
              const center = groupX + groupW / 2;
              const startX = center - (configs.length * (barWidth + 4)) / 2;

              return (
                <g key={wl}>
                  <text x={center} y="245" fill="#e2e8f0" fontSize="12" fontWeight="600" textAnchor="middle">
                    {wl.toUpperCase()} WORKLOAD
                  </text>
                  {configs.map((cfg, cIdx) => {
                    const rawVal = getMetric(cfg, wl, chartMetric);
                    const val = typeof rawVal === 'number' ? rawVal : 0;
                    const h = maxVal > 0 ? (val / maxVal) * (chartHeight - 30) : 0;
                    const x = startX + cIdx * (barWidth + 4);
                    const y = 30 + (chartHeight - 30) - h;
                    const color = CONFIG_COLORS[cfg];

                    return (
                      <g key={cfg}>
                        <rect
                          x={x}
                          y={y}
                          width={barWidth}
                          height={Math.max(2, h)}
                          fill={val > 0 ? color : '#334155'}
                          rx="3"
                        />
                        <text
                          x={x + barWidth / 2}
                          y={Math.max(20, y - 4)}
                          fill={val > 0 ? color : '#64748b'}
                          fontSize="9"
                          fontWeight="600"
                          textAnchor="middle"
                        >
                          {val > 0 ? (val < 100 ? val.toFixed(1) : Math.round(val)) : '0'}
                        </text>
                      </g>
                    );
                  })}
                </g>
              );
            })}
          </svg>
        </div>

        {/* Legend */}
        <div className="chart-legend">
          {configs.map(c => (
            <div key={c} className="legend-item">
              <span className="legend-color-dot" style={{ backgroundColor: CONFIG_COLORS[c] }}></span>
              <span>{CONFIG_NAMES[c]}</span>
            </div>
          ))}
        </div>
      </div>
    );
  };

  return (
    <div className="management-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Performance Experiments & Comparison</h2>
          <p className="section-subtitle">
            Evaluate and compare Baseline performance against Rate Limiting, Response Caching, and Combined optimizations.
          </p>
        </div>
        <div className="btn-group-inline">
          {hasAnyData && (
            <button onClick={handleClear} className="btn-secondary">
              ✕ Clear Dataset
            </button>
          )}
          <button onClick={fetchGatewayStats} disabled={loadingGatewayStats} className="btn-secondary">
            {loadingGatewayStats ? 'Refreshing...' : '⚡ Gateway Stats'}
          </button>
        </div>
      </div>

      <ErrorDisplay error={parseError} onDismiss={() => setParseError(null)} />

      {/* Gateway Live Configuration Status Banner */}
      {gatewayStats && (
        <div className="gateway-stats-banner">
          <div className="stats-header">
            <span className="stats-tag">Active Gateway Optimization Telemetry</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Queried via port 8080</span>
          </div>
          <div className="stats-row">
            <div>
              <span className="meta-label">Rate Limiting: </span>
              <span className={gatewayStats.rateLimiting?.enabled ? 'badge badge-online' : 'badge badge-offline'}>
                {gatewayStats.rateLimiting?.enabled ? `ENABLED (${gatewayStats.rateLimiting.maxRequests} req / ${gatewayStats.rateLimiting.windowSeconds}s)` : 'DISABLED'}
              </span>
            </div>
            <div>
              <span className="meta-label">Response Caching: </span>
              <span className={gatewayStats.caching?.enabled ? 'badge badge-online' : 'badge badge-offline'}>
                {gatewayStats.caching?.enabled ? `ENABLED (${gatewayStats.caching.hitRatioPercent}% Hit Ratio)` : 'DISABLED'}
              </span>
            </div>
            <div>
              <span className="meta-label">Cache Lookups: </span>
              <span style={{ color: '#93c5fd', fontWeight: 600 }}>
                {gatewayStats.caching?.hitCount ?? 0} Hits / {gatewayStats.caching?.missCount ?? 0} Misses ({gatewayStats.caching?.estimatedSize ?? 0} entries)
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Upload Zone */}
      {!hasAnyData ? (
        <div className="upload-dropzone">
          <div className="dropzone-icon">📊</div>
          <h3>Import JMeter / Day 6 Experiment Datasets</h3>
          <p style={{ color: 'var(--text-muted)', maxWidth: '560px', margin: '0.5rem auto 1.5rem', fontSize: '0.9rem' }}>
            Upload raw JMeter CSV results (<code>.csv</code>) or multi-run comparison summaries (<code>comparison_summary.json</code>) generated by <code>scripts/load-testing/</code>.
          </p>
          <label className="btn-primary file-input-label">
            <span>Select CSV or JSON File</span>
            <input
              type="file"
              accept=".csv,.json"
              onChange={handleFileUpload}
              style={{ display: 'none' }}
            />
          </label>
          <div style={{ marginTop: '1.25rem', fontSize: '0.8rem', color: '#6b7280' }}>
            Supports: <code>baseline_*.csv</code>, <code>rate_limit_*.csv</code>, <code>cache_*.csv</code>, <code>combined_*.csv</code>, or <code>comparison_summary.json</code>
          </div>
        </div>
      ) : (
        <div>
          {/* Metadata & Controls Bar */}
          <div className="dataset-meta-bar">
            <div>
              <span className="meta-label">Imported:</span>
              <strong> {importedFileInfo?.name || 'Multi-Configuration Dataset'}</strong>
              <span className="badge badge-checking" style={{ marginLeft: '0.5rem' }}>
                {importedFileInfo?.type || 'Comparison Matrix'}
              </span>
            </div>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <label className="btn-secondary" style={{ fontSize: '0.8rem', padding: '0.35rem 0.75rem', cursor: 'pointer' }}>
                Upload Another
                <input
                  type="file"
                  accept=".csv,.json"
                  onChange={handleFileUpload}
                  style={{ display: 'none' }}
                />
              </label>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="tab-bar" style={{ marginBottom: '1rem' }}>
            <button
              className={`tab-btn ${activeTab === 'comparison' ? 'active' : ''}`}
              onClick={() => setActiveTab('comparison')}
            >
              Configuration Comparison Matrix
            </button>
            {singleMetrics && (
              <button
                className={`tab-btn ${activeTab === 'summary' ? 'active' : ''}`}
                onClick={() => setActiveTab('summary')}
              >
                Single Run Detail
              </button>
            )}
            {rawRows.length > 0 && (
              <button
                className={`tab-btn ${activeTab === 'raw' ? 'active' : ''}`}
                onClick={() => setActiveTab('raw')}
              >
                Raw Sample Stream ({rawRows.length})
              </button>
            )}
          </div>

          {/* TAB 1: Multi-Configuration Comparison Matrix */}
          {activeTab === 'comparison' && (
            <div>
              {/* Filter controls */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span className="meta-label">Workload Filter:</span>
                  {['all', 'low', 'medium', 'high'].map(wl => (
                    <button
                      key={wl}
                      className={`pill-btn ${selectedWorkload === wl ? 'active' : ''}`}
                      onClick={() => setSelectedWorkload(wl)}
                    >
                      {wl.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>

              {/* Render Visual Comparison Chart */}
              {renderComparisonChart()}

              {/* Side-by-Side Comparison Tables per Workload */}
              {(selectedWorkload === 'all' ? ['low', 'medium', 'high'] : [selectedWorkload]).map(wl => {
                const configs = ['baseline', 'rate-limit-only', 'cache-only', 'combined'];

                return (
                  <div key={wl} style={{ marginTop: '1.5rem' }}>
                    <div className="workload-table-header">
                      <h3>{wl.toUpperCase()} WORKLOAD COMPARISON</h3>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                        {wl === 'low' ? '5 Concurrent Users, 60s' : wl === 'medium' ? '20 Concurrent Users, 120s' : '50 Concurrent Users, 180s'}
                      </span>
                    </div>

                    <div className="table-container">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Metric Dimension</th>
                            <th style={{ textAlign: 'right', color: CONFIG_COLORS.baseline }}>Baseline (A)</th>
                            <th style={{ textAlign: 'right', color: CONFIG_COLORS['rate-limit-only'] }}>Rate Limiting (B)</th>
                            <th style={{ textAlign: 'right', color: CONFIG_COLORS['cache-only'] }}>Caching Only (C)</th>
                            <th style={{ textAlign: 'right', color: CONFIG_COLORS.combined }}>Combined (D)</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr>
                            <td><strong>Mean Latency (Avg)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'avg_latency');
                              return <td key={cfg} style={{ textAlign: 'right', fontWeight: 600 }}>{v !== null ? `${v} ms` : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>95th Percentile (p95)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'p95_latency');
                              return <td key={cfg} style={{ textAlign: 'right', color: '#fbbf24', fontWeight: 600 }}>{v !== null ? `${v} ms` : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>99th Percentile (p99)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'p99_latency');
                              return <td key={cfg} style={{ textAlign: 'right', color: '#f87171', fontWeight: 600 }}>{v !== null ? `${v} ms` : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>Throughput (RPS)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'throughput');
                              return <td key={cfg} style={{ textAlign: 'right', color: '#38bdf8', fontWeight: 600 }}>{v !== null ? `${v} req/s` : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>Total Requests</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'total_requests');
                              return <td key={cfg} style={{ textAlign: 'right' }}>{v !== null ? v.toLocaleString() : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>Successful Requests</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'successful_requests');
                              return <td key={cfg} style={{ textAlign: 'right', color: '#34d399' }}>{v !== null ? v.toLocaleString() : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>HTTP 429 Rejections</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'rejections_429');
                              return <td key={cfg} style={{ textAlign: 'right', color: v > 0 ? '#fbbf24' : 'var(--text-muted)' }}>{v !== null ? v : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>HTTP Error Rate (%)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'error_rate');
                              return <td key={cfg} style={{ textAlign: 'right', color: v > 0 ? '#f87171' : '#34d399', fontWeight: 600 }}>{v !== null ? `${v}%` : '—'}</td>;
                            })}
                          </tr>
                          <tr>
                            <td><strong>Cache Hit Ratio (%)</strong></td>
                            {configs.map(cfg => {
                              const v = getMetric(cfg, wl, 'cache_hit_ratio');
                              return (
                                <td key={cfg} style={{ textAlign: 'right', color: (cfg.includes('cache') || cfg === 'combined') ? '#34d399' : 'var(--text-muted)', fontWeight: 600 }}>
                                  {v !== null ? `${v}%` : (cfg.includes('cache') || cfg === 'combined' ? '0.00%' : 'N/A')}
                                </td>
                              );
                            })}
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* TAB 2: Single Run Detail View */}
          {activeTab === 'summary' && singleMetrics && (
            <div>
              <div className="kpi-grid" style={{ marginTop: '1rem' }}>
                <div className="kpi-card">
                  <span className="kpi-title">Average Latency</span>
                  <span className="kpi-value">{singleMetrics.avgResponseTime} <small>ms</small></span>
                  <span className="kpi-sub">Mean response duration</span>
                </div>
                <div className="kpi-card">
                  <span className="kpi-title">p95 Latency</span>
                  <span className="kpi-value highlight-warning">{singleMetrics.p95Latency} <small>ms</small></span>
                  <span className="kpi-sub">95th percentile SLA</span>
                </div>
                <div className="kpi-card">
                  <span className="kpi-title">p99 Latency</span>
                  <span className="kpi-value highlight-danger">{singleMetrics.p99Latency} <small>ms</small></span>
                  <span className="kpi-sub">Tail latency</span>
                </div>
                <div className="kpi-card">
                  <span className="kpi-title">Throughput</span>
                  <span className="kpi-value highlight-success">{singleMetrics.throughput} <small>req/s</small></span>
                  <span className="kpi-sub">Sustained rate</span>
                </div>
                <div className="kpi-card">
                  <span className="kpi-title">HTTP 429 Rejections</span>
                  <span className="kpi-value" style={{ color: '#fbbf24' }}>{singleMetrics.rejections429}</span>
                  <span className="kpi-sub">Rate-limited requests</span>
                </div>
                <div className="kpi-card">
                  <span className="kpi-title">Cache Hit Ratio</span>
                  <span className="kpi-value highlight-success">{singleMetrics.cacheHitRatio}%</span>
                  <span className="kpi-sub">{singleMetrics.cacheHits} Hits / {singleMetrics.cacheMisses} Misses</span>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: Raw Sample Inspector */}
          {activeTab === 'raw' && rawRows.length > 0 && (
            <div style={{ marginTop: '1rem' }}>
              <div className="table-container" style={{ maxHeight: '350px', overflowY: 'auto' }}>
                <table className="mini-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Sampler</th>
                      <th style={{ textAlign: 'right' }}>Elapsed</th>
                      <th style={{ textAlign: 'center' }}>HTTP Code</th>
                      <th style={{ textAlign: 'center' }}>Success</th>
                      <th>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rawRows.map((r, idx) => (
                      <tr key={idx}>
                        <td>{idx + 1}</td>
                        <td><code>{r.label}</code></td>
                        <td style={{ textAlign: 'right' }}>{r.elapsed} ms</td>
                        <td style={{ textAlign: 'center' }}>
                          <span className={r.responseCode === '429' ? 'badge badge-checking' : r.responseCode?.startsWith('2') ? 'badge badge-online' : 'badge badge-offline'}>
                            {r.responseCode}
                          </span>
                        </td>
                        <td style={{ textAlign: 'center' }}>{r.success ? '✓ Yes' : '✗ No'}</td>
                        <td>{r.timeStamp ? new Date(r.timeStamp).toLocaleTimeString() : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
