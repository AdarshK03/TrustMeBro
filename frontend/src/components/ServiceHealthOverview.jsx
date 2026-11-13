import React, { useState } from 'react';
import { healthApi } from '../api/apiClient';

const SERVICES = [
  {
    id: 'gw',
    name: 'API Gateway',
    port: 8080,
    role: 'Central Edge Ingress & Routing',
    gatewayRoute: '/actuator/health'
  },
  {
    id: 'user',
    name: 'User Service',
    port: 8081,
    role: 'User Management & Auth',
    gatewayRoute: '/api/health/users'
  },
  {
    id: 'product',
    name: 'Product Service',
    port: 8082,
    role: 'Product Catalog & Inventory',
    gatewayRoute: '/api/health/products'
  },
  {
    id: 'order',
    name: 'Order Service',
    port: 8083,
    role: 'Order Processing & Line Items',
    gatewayRoute: '/api/health/orders'
  }
];

export default function ServiceHealthOverview() {
  const [statuses, setStatuses] = useState({
    gw: { status: 'UNKNOWN', lastChecked: null, latencyMs: null, details: null },
    user: { status: 'UNKNOWN', lastChecked: null, latencyMs: null, details: null },
    product: { status: 'UNKNOWN', lastChecked: null, latencyMs: null, details: null },
    order: { status: 'UNKNOWN', lastChecked: null, latencyMs: null, details: null }
  });
  const [loadingMap, setLoadingMap] = useState({});
  const [refreshingAll, setRefreshingAll] = useState(false);

  const checkSingle = async (serviceId) => {
    setLoadingMap(prev => ({ ...prev, [serviceId]: true }));
    const startTime = performance.now();
    try {
      const res = await healthApi.checkServiceHealth(serviceId);
      const latencyMs = Math.round(performance.now() - startTime);
      const isUp = res.status === 200 && res.data?.status === 'UP';
      setStatuses(prev => ({
        ...prev,
        [serviceId]: {
          status: isUp ? 'UP' : 'DOWN',
          lastChecked: new Date().toLocaleTimeString(),
          latencyMs,
          details: res.data
        }
      }));
    } catch (err) {
      const latencyMs = Math.round(performance.now() - startTime);
      setStatuses(prev => ({
        ...prev,
        [serviceId]: {
          status: 'DOWN',
          lastChecked: new Date().toLocaleTimeString(),
          latencyMs,
          details: err
        }
      }));
    } finally {
      setLoadingMap(prev => ({ ...prev, [serviceId]: false }));
    }
  };

  const refreshAll = async () => {
    setRefreshingAll(true);
    await Promise.all(SERVICES.map(s => checkSingle(s.id)));
    setRefreshingAll(false);
  };

  const getStatusBadge = (status) => {
    if (status === 'UP') return <span className="badge badge-online">UP</span>;
    if (status === 'DOWN') return <span className="badge badge-offline">DOWN</span>;
    return <span className="badge badge-checking">UNKNOWN</span>;
  };

  return (
    <div className="health-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Service Health Overview</h2>
          <p className="section-subtitle">
            Spring Boot Actuator health status queried exclusively through Gateway ingress routes.
          </p>
        </div>
        <button
          onClick={refreshAll}
          disabled={refreshingAll || Object.values(loadingMap).some(Boolean)}
          className="btn-primary"
        >
          {refreshingAll ? 'Refreshing All...' : '↻ Refresh All Services'}
        </button>
      </div>

      <div className="grid">
        {SERVICES.map((s) => {
          const info = statuses[s.id] || {};
          const isLoading = !!loadingMap[s.id];

          return (
            <div key={s.id} className="card service-card">
              <div className="service-card-header">
                <h3 className="service-name">{s.name}</h3>
                {getStatusBadge(info.status)}
              </div>
              
              <div className="service-role-tag">{s.role}</div>

              <div className="service-meta-list">
                <div className="service-meta-row">
                  <span className="meta-label">Port:</span>
                  <span className="meta-value"><strong>{s.port}</strong></span>
                </div>
                <div className="service-meta-row">
                  <span className="meta-label">Gateway Endpoint:</span>
                  <code className="meta-code">{s.gatewayRoute}</code>
                </div>
                <div className="service-meta-row">
                  <span className="meta-label">Last Checked:</span>
                  <span className="meta-value">{info.lastChecked || 'Not yet checked'}</span>
                </div>
                {info.latencyMs !== null && info.latencyMs !== undefined && (
                  <div className="service-meta-row">
                    <span className="meta-label">Response Time:</span>
                    <span className="meta-value">{info.latencyMs} ms</span>
                  </div>
                )}
              </div>

              <button
                className="btn-secondary service-check-btn"
                onClick={() => checkSingle(s.id)}
                disabled={isLoading || refreshingAll}
              >
                {isLoading ? 'Checking Health...' : `Check Health (Port ${s.port})`}
              </button>
            </div>
          );
        })}
      </div>

      <div className="architecture-note-callout">
        <span className="callout-icon">ℹ️</span>
        <div className="callout-content">
          <strong>Gateway Routing Architecture Note:</strong>
          <p>
            All frontend health checks strictly target Gateway port <code>8080</code> (e.g. <code>/api/health/users</code>, <code>/api/health/products</code>, <code>/api/health/orders</code>). 
            The Gateway dynamically rewrites and forwards these to downstream <code>/actuator/health</code> endpoints without exposing internal service ports (8081, 8082, 8083) directly to the browser.
          </p>
        </div>
      </div>
    </div>
  );
}

