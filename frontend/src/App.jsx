import React, { useState } from 'react';
import ServiceStatusCard from './components/ServiceStatusCard';
import GatewayRouteTester from './components/GatewayRouteTester';
import { healthApi } from './api/apiClient';

const SERVICES = [
  { id: 'gw', name: 'API Gateway', port: 8080, prefix: '/api/**' },
  { id: 'user', name: 'User Service', port: 8081, prefix: '/api/users/**' },
  { id: 'product', name: 'Product Service', port: 8082, prefix: '/api/products/**' },
  { id: 'order', name: 'Order Service', port: 8083, prefix: '/api/orders/**' }
];

export default function App() {
  const [statuses, setStatuses] = useState({
    gw: 'UNKNOWN',
    user: 'UNKNOWN',
    product: 'UNKNOWN',
    order: 'UNKNOWN'
  });
  const [loadingMap, setLoadingMap] = useState({});

  const pingService = async (serviceId, port) => {
    setLoadingMap(prev => ({ ...prev, [serviceId]: true }));
    try {
      const res = await healthApi.checkDirectHealth(port);
      if (res.status === 200 && res.data?.status === 'UP') {
        setStatuses(prev => ({ ...prev, [serviceId]: 'UP' }));
      } else {
        setStatuses(prev => ({ ...prev, [serviceId]: 'DOWN' }));
      }
    } catch {
      setStatuses(prev => ({ ...prev, [serviceId]: 'OFFLINE' }));
    } finally {
      setLoadingMap(prev => ({ ...prev, [serviceId]: false }));
    }
  };

  const pingAll = () => {
    SERVICES.forEach(s => pingService(s.id, s.port));
  };

  return (
    <div className="container">
      <header>
        <h1>Cloud-Based API Gateway Platform</h1>
        <p>Microservices Management, Centralized Routing & Observability</p>
      </header>

      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 className="section-title" style={{ margin: 0, border: 'none' }}>Topology & Service Health</h2>
          <button onClick={pingAll} className="btn-secondary" style={{ fontSize: '0.85rem' }}>
            Ping All Services
          </button>
        </div>

        <div className="grid">
          {SERVICES.map(s => (
            <ServiceStatusCard
              key={s.id}
              name={s.name}
              port={s.port}
              prefix={s.prefix}
              status={statuses[s.id]}
              loading={!!loadingMap[s.id]}
              onPing={() => pingService(s.id, s.port)}
            />
          ))}
        </div>
      </section>

      <section>
        <h2 className="section-title">Gateway Ingress & API Contracts</h2>
        <GatewayRouteTester />
      </section>

      <section className="card" style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '0.75rem' }}>Architectural Boundaries (Day 1 Milestone)</h3>
        <ul style={{ paddingLeft: '1.25rem', color: 'var(--text-muted)', fontSize: '0.9rem', lineHeight: '1.7' }}>
          <li><strong>Zero Shared Tables:</strong> Each service strictly owns its domain tables in Neon PostgreSQL.</li>
          <li><strong>Reactive Edge:</strong> The Gateway operates purely on Spring Cloud Gateway with reactive non-blocking I/O.</li>
          <li><strong>Traceability:</strong> Every request forwards an <code>X-Correlation-ID</code> header across downstream services.</li>
          <li><strong>Security Roadmap:</strong> JWT tokens and token-bucket rate limiting hook points are prepared for Day 4.</li>
        </ul>
      </section>

      <footer>
        <p>Day 1 Architecture & Skeleton Implementation • Spring Boot 3.3.4 • Spring Cloud 2023.0.3 • React 18</p>
      </footer>
    </div>
  );
}
