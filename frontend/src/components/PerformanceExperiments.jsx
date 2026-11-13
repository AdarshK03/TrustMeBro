import React, { useState } from 'react';
import ErrorDisplay from './ErrorDisplay';

export default function PerformanceExperiments() {
  const [importedFile, setImportedFile] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [endpointBreakdown, setEndpointBreakdown] = useState([]);
  const [rawRows, setRawRows] = useState([]);
  const [rawText, setRawText] = useState('');
  const [parseError, setParseError] = useState(null);
  const [showRawInspector, setShowRawInspector] = useState(false);
  const [activeTab, setActiveTab] = useState('summary'); // 'summary' | 'breakdown' | 'raw'

  const handleFileUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setParseError(null);
    setImportedFile({
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
        parseJsonContent(text);
      } else {
        parseCsvContent(text);
      }
    } catch (err) {
      setParseError({
        status: 400,
        error: 'Parsing Error',
        message: `Failed to parse ${fileName}: ${err.message}`,
        path: fileName
      });
      setMetrics(null);
      setEndpointBreakdown([]);
      setRawRows([]);
    }
  };

  const parseJsonContent = (jsonString) => {
    const parsed = JSON.parse(jsonString);

    // Case 1: Pre-computed benchmark report JSON
    if (parsed && !Array.isArray(parsed) && (parsed.totalRequests !== undefined || parsed.metrics !== undefined)) {
      const src = parsed.metrics || parsed;
      setMetrics({
        workloadTitle: parsed.workload || parsed.title || 'Imported Load Test Experiment',
        totalRequests: src.totalRequests ?? 0,
        successfulRequests: src.successfulRequests ?? 0,
        failedRequests: src.failedRequests ?? 0,
        errorRate: src.errorRate !== undefined ? Number(src.errorRate).toFixed(2) : '0.00',
        avgResponseTime: src.avgResponseTime !== undefined ? Number(src.avgResponseTime).toFixed(2) : '0.00',
        p95Latency: src.p95 !== undefined ? Number(src.p95).toFixed(2) : (src.p95Latency !== undefined ? Number(src.p95Latency).toFixed(2) : '—'),
        p99Latency: src.p99 !== undefined ? Number(src.p99).toFixed(2) : (src.p99Latency !== undefined ? Number(src.p99Latency).toFixed(2) : '—'),
        throughput: src.throughput !== undefined ? Number(src.throughput).toFixed(2) : (src.rps !== undefined ? Number(src.rps).toFixed(2) : '—')
      });

      if (Array.isArray(parsed.endpoints)) {
        setEndpointBreakdown(parsed.endpoints);
      } else {
        setEndpointBreakdown([]);
      }
      setRawRows(Array.isArray(parsed.samples) ? parsed.samples.slice(0, 200) : []);
      return;
    }

    // Case 2: Array of raw sample objects
    const samples = Array.isArray(parsed) ? parsed : (parsed.samples || []);
    if (samples.length === 0) {
      throw new Error('No load test sample records found in JSON structure.');
    }

    calculateMetricsFromSamples(samples);
  };

  const parseCsvContent = (csvString) => {
    const lines = csvString.trim().split(/\r?\n/).filter(line => line.trim().length > 0);
    if (lines.length < 2) {
      throw new Error('CSV file contains insufficient data (minimum 1 header line and 1 data row required).');
    }

    const headerParts = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''));
    
    // Find column indexes
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
      if (parts.length < headerParts.length * 0.7) continue;

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
      throw new Error('No valid sample data rows could be extracted from the CSV.');
    }

    calculateMetricsFromSamples(samples);
  };

  const calculateMetricsFromSamples = (samples) => {
    const totalRequests = samples.length;
    let successfulRequests = 0;
    let failedRequests = 0;
    let sumElapsed = 0;
    const elapsedValues = [];
    let minTime = Infinity;
    let maxTime = -Infinity;

    // Per endpoint map
    const endpointMap = {};

    samples.forEach(s => {
      const elapsed = typeof s.elapsed === 'number' ? s.elapsed : parseFloat(s.elapsed) || 0;
      elapsedValues.push(elapsed);
      sumElapsed += elapsed;

      const isSuccess = s.success === true || (s.responseCode && parseInt(s.responseCode, 10) >= 200 && parseInt(s.responseCode, 10) < 400);
      if (isSuccess) {
        successfulRequests++;
      } else {
        failedRequests++;
      }

      if (s.timeStamp) {
        if (s.timeStamp < minTime) minTime = s.timeStamp;
        if (s.timeStamp > maxTime) maxTime = s.timeStamp;
      }

      // Group by endpoint label
      const label = s.label || 'Unknown';
      if (!endpointMap[label]) {
        endpointMap[label] = {
          label,
          total: 0,
          success: 0,
          failed: 0,
          elapsedList: []
        };
      }
      endpointMap[label].total++;
      if (isSuccess) endpointMap[label].success++;
      else endpointMap[label].failed++;
      endpointMap[label].elapsedList.push(elapsed);
    });

    elapsedValues.sort((a, b) => a - b);

    const avgResponseTime = (sumElapsed / totalRequests).toFixed(2);
    const p95Idx = Math.min(Math.floor(totalRequests * 0.95), totalRequests - 1);
    const p99Idx = Math.min(Math.floor(totalRequests * 0.99), totalRequests - 1);
    const p95Latency = elapsedValues[p95Idx].toFixed(2);
    const p99Latency = elapsedValues[p99Idx].toFixed(2);
    const errorRate = ((failedRequests / totalRequests) * 100).toFixed(2);

    let throughput = '—';
    if (minTime !== Infinity && maxTime !== -Infinity && maxTime > minTime) {
      const durationSeconds = (maxTime - minTime) / 1000;
      if (durationSeconds > 0) {
        throughput = (totalRequests / durationSeconds).toFixed(2);
      }
    }

    setMetrics({
      workloadTitle: 'Imported JMeter Benchmark Results',
      totalRequests,
      successfulRequests,
      failedRequests,
      errorRate,
      avgResponseTime,
      p95Latency,
      p99Latency,
      throughput
    });

    // Calculate per-endpoint breakdown
    const breakdown = Object.values(endpointMap).map(e => {
      e.elapsedList.sort((a, b) => a - b);
      const eAvg = (e.elapsedList.reduce((acc, v) => acc + v, 0) / e.total).toFixed(2);
      const eP95 = e.elapsedList[Math.min(Math.floor(e.total * 0.95), e.total - 1)].toFixed(2);
      const eP99 = e.elapsedList[Math.min(Math.floor(e.total * 0.99), e.total - 1)].toFixed(2);
      const eErrRate = ((e.failed / e.total) * 100).toFixed(2);
      return {
        label: e.label,
        total: e.total,
        success: e.success,
        failed: e.failed,
        errorRate: eErrRate,
        avg: eAvg,
        p95: eP95,
        p99: eP99
      };
    });

    setEndpointBreakdown(breakdown);
    setRawRows(samples.slice(0, 150));
  };

  const handleClear = () => {
    setImportedFile(null);
    setMetrics(null);
    setEndpointBreakdown([]);
    setRawRows([]);
    setRawText('');
    setParseError(null);
  };

  return (
    <div className="management-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Performance Experiments</h2>
          <p className="section-subtitle">
            Import and inspect benchmark datasets (CSV/JSON) generated by Apache JMeter load tests.
          </p>
        </div>
        {metrics && (
          <button onClick={handleClear} className="btn-secondary">
            ✕ Clear Dataset
          </button>
        )}
      </div>

      <ErrorDisplay error={parseError} onDismiss={() => setParseError(null)} />

      {/* File Upload Zone */}
      {!metrics ? (
        <div className="upload-dropzone">
          <div className="dropzone-icon">📊</div>
          <h3>Import JMeter Benchmark File</h3>
          <p style={{ color: 'var(--text-muted)', maxWidth: '520px', margin: '0.5rem auto 1.5rem', fontSize: '0.9rem' }}>
            Upload real benchmark output generated from <code>scripts/load-testing/</code>. 
            Supports JMeter CSV results (<code>.csv</code>) or aggregate JSON reports (<code>.json</code>).
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
            Expected format: <code>timeStamp, elapsed, label, responseCode, success, ...</code>
          </div>
        </div>
      ) : (
        <div>
          {/* File Metadata Bar */}
          <div className="dataset-meta-bar">
            <div>
              <span className="meta-label">Imported File:</span>
              <strong> {importedFile?.name}</strong>
              <span className="badge badge-checking" style={{ marginLeft: '0.75rem' }}>{importedFile?.type}</span>
              <span style={{ marginLeft: '0.75rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                ({importedFile?.size}, modified {importedFile?.lastModified})
              </span>
            </div>
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

          {/* Top-Level KPI Summary Cards */}
          <div className="kpi-grid">
            <div className="kpi-card">
              <span className="kpi-title">Average Latency</span>
              <span className="kpi-value">{metrics.avgResponseTime} <small>ms</small></span>
              <span className="kpi-sub">Mean response time</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-title">p95 Latency</span>
              <span className="kpi-value highlight-warning">{metrics.p95Latency} <small>ms</small></span>
              <span className="kpi-sub">95th percentile SLA</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-title">p99 Latency</span>
              <span className="kpi-value highlight-danger">{metrics.p99Latency} <small>ms</small></span>
              <span className="kpi-sub">99th percentile peak</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-title">Throughput</span>
              <span className="kpi-value highlight-success">{metrics.throughput} <small>{metrics.throughput !== '—' ? 'req/s' : ''}</small></span>
              <span className="kpi-sub">Gateway ingress rate</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-title">Total Requests</span>
              <span className="kpi-value">{metrics.totalRequests.toLocaleString()}</span>
              <span className="kpi-sub">{metrics.successfulRequests} OK / {metrics.failedRequests} Err</span>
            </div>

            <div className="kpi-card">
              <span className="kpi-title">HTTP Error Rate</span>
              <span className={`kpi-value ${parseFloat(metrics.errorRate) > 0 ? 'highlight-danger' : 'highlight-success'}`}>
                {metrics.errorRate}%
              </span>
              <span className="kpi-sub">Non-2xx failures</span>
            </div>
          </div>

          {/* View Selection Tabs */}
          <div className="tab-bar">
            <button
              className={`tab-btn ${activeTab === 'summary' ? 'active' : ''}`}
              onClick={() => setActiveTab('summary')}
            >
              Overview & KPIs
            </button>
            <button
              className={`tab-btn ${activeTab === 'breakdown' ? 'active' : ''}`}
              onClick={() => setActiveTab('breakdown')}
            >
              Endpoint Breakdown ({endpointBreakdown.length})
            </button>
            <button
              className={`tab-btn ${activeTab === 'raw' ? 'active' : ''}`}
              onClick={() => setActiveTab('raw')}
            >
              Raw Samples Inspector ({rawRows.length > 0 ? rawRows.length : 'File'})
            </button>
          </div>

          {/* Tab 1: Summary Table */}
          {activeTab === 'summary' && (
            <div className="table-container" style={{ marginTop: '1rem' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Metric Dimension</th>
                    <th style={{ textAlign: 'right' }}>Observed Value</th>
                    <th>Standard / Benchmark Target</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>Total Processed Requests</strong></td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{metrics.totalRequests}</td>
                    <td>Complete test plan sample volume</td>
                  </tr>
                  <tr>
                    <td><strong>Successful Requests (2xx / 3xx)</strong></td>
                    <td style={{ textAlign: 'right', color: '#34d399', fontWeight: 600 }}>{metrics.successfulRequests}</td>
                    <td>Clean gateway proxy & service fulfillment</td>
                  </tr>
                  <tr>
                    <td><strong>Failed Requests (4xx / 5xx / Network)</strong></td>
                    <td style={{ textAlign: 'right', color: metrics.failedRequests > 0 ? '#f87171' : 'var(--text-muted)', fontWeight: 600 }}>
                      {metrics.failedRequests}
                    </td>
                    <td>Throttled, unauthorized, or dropped</td>
                  </tr>
                  <tr>
                    <td><strong>HTTP Error Rate</strong></td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{metrics.errorRate}%</td>
                    <td>Target: 0.00% under baseline conditions</td>
                  </tr>
                  <tr>
                    <td><strong>Mean Response Time</strong></td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{metrics.avgResponseTime} ms</td>
                    <td>Average latency across full run</td>
                  </tr>
                  <tr>
                    <td><strong>95th Percentile (p95)</strong></td>
                    <td style={{ textAlign: 'right', color: '#fbbf24', fontWeight: 600 }}>{metrics.p95Latency} ms</td>
                    <td>95% of requests completed faster than this</td>
                  </tr>
                  <tr>
                    <td><strong>99th Percentile (p99)</strong></td>
                    <td style={{ textAlign: 'right', color: '#f87171', fontWeight: 600 }}>{metrics.p99Latency} ms</td>
                    <td>Tail latency (worst 1% of requests)</td>
                  </tr>
                  <tr>
                    <td><strong>Throughput / Ingress Rate</strong></td>
                    <td style={{ textAlign: 'right', color: '#60a5fa', fontWeight: 600 }}>{metrics.throughput} {metrics.throughput !== '—' ? 'req/sec' : ''}</td>
                    <td>Sustained concurrency throughput</td>
                  </tr>
                </tbody>
              </table>
            </div>
          )}

          {/* Tab 2: Endpoint Breakdown */}
          {activeTab === 'breakdown' && (
            <div className="table-container" style={{ marginTop: '1rem' }}>
              {endpointBreakdown.length === 0 ? (
                <p style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                  No per-endpoint labels detected in this dataset.
                </p>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Endpoint / Sampler Label</th>
                      <th style={{ textAlign: 'right' }}>Requests</th>
                      <th style={{ textAlign: 'right' }}>Avg (ms)</th>
                      <th style={{ textAlign: 'right' }}>p95 (ms)</th>
                      <th style={{ textAlign: 'right' }}>p99 (ms)</th>
                      <th style={{ textAlign: 'right' }}>Errors</th>
                      <th style={{ textAlign: 'right' }}>Error Rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {endpointBreakdown.map((ep, idx) => (
                      <tr key={idx}>
                        <td><code>{ep.label}</code></td>
                        <td style={{ textAlign: 'right', fontWeight: 600 }}>{ep.total}</td>
                        <td style={{ textAlign: 'right' }}>{ep.avg} ms</td>
                        <td style={{ textAlign: 'right', color: '#fbbf24' }}>{ep.p95} ms</td>
                        <td style={{ textAlign: 'right', color: '#f87171' }}>{ep.p99} ms</td>
                        <td style={{ textAlign: 'right' }}>{ep.failed}</td>
                        <td style={{ textAlign: 'right', color: parseFloat(ep.errorRate) > 0 ? '#f87171' : '#34d399' }}>
                          {ep.errorRate}%
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {/* Tab 3: Raw Samples Inspector */}
          {activeTab === 'raw' && (
            <div style={{ marginTop: '1rem' }}>
              {rawRows.length > 0 && (
                <div className="table-container" style={{ marginBottom: '1.5rem', maxHeight: '350px', overflowY: 'auto' }}>
                  <h4 style={{ padding: '0.75rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                    Parsed Sample Stream (Showing first {rawRows.length} requests)
                  </h4>
                  <table className="mini-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Endpoint</th>
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
                            <span className={r.responseCode?.startsWith('2') ? 'badge badge-online' : 'badge badge-offline'}>
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
              )}

              <div>
                <h4 style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
                  Raw File Content Preview (First 5000 characters)
                </h4>
                <pre className="raw-preview-box">
                  {rawText.slice(0, 5000)}
                  {rawText.length > 5000 ? `\n... [Truncated: Total file length ${rawText.length} characters]` : ''}
                </pre>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

