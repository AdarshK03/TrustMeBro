import React, { useState } from 'react';
import ServiceHealthOverview from './components/ServiceHealthOverview';
import ProductManagement from './components/ProductManagement';
import OrderManagement from './components/OrderManagement';
import PerformanceExperiments from './components/PerformanceExperiments';
import GatewayRouteTester from './components/GatewayRouteTester';
import AuthBar from './components/AuthBar';
import { BASE_URL } from './api/apiClient';

export default function App() {
  const [activeSection, setActiveSection] = useState('health'); // 'health' | 'products' | 'orders' | 'experiments' | 'routes'
  const [, setAuthUser] = useState(null);

  return (
    <div className="container">
      <header className="app-header">
        <div className="header-branding">
          <div className="brand-logo">🌐</div>
          <div>
            <h1>Cloud API Gateway & Microservices Platform</h1>
            <p>Enterprise API Management, Dynamic Ingress Routing & Performance Benchmarking</p>
          </div>
        </div>
        <div className="gateway-target-badge">
          <span className="target-label">Gateway Ingress:</span>
          <code>{BASE_URL}</code>
        </div>
      </header>

      {/* Authentication and session status bar */}
      <AuthBar onAuthChange={setAuthUser} />

      {/* Top Navigation Navigation Bar */}
      <nav className="nav-tabs">
        <button
          className={`nav-tab ${activeSection === 'health' ? 'active' : ''}`}
          onClick={() => setActiveSection('health')}
        >
          <span className="nav-icon">🏥</span>
          <span>Service Health</span>
        </button>

        <button
          className={`nav-tab ${activeSection === 'products' ? 'active' : ''}`}
          onClick={() => setActiveSection('products')}
        >
          <span className="nav-icon">📦</span>
          <span>Product Catalog</span>
        </button>

        <button
          className={`nav-tab ${activeSection === 'orders' ? 'active' : ''}`}
          onClick={() => setActiveSection('orders')}
        >
          <span className="nav-icon">🛒</span>
          <span>Order Management</span>
        </button>

        <button
          className={`nav-tab ${activeSection === 'experiments' ? 'active' : ''}`}
          onClick={() => setActiveSection('experiments')}
        >
          <span className="nav-icon">📊</span>
          <span>Performance Experiments</span>
        </button>

        <button
          className={`nav-tab ${activeSection === 'routes' ? 'active' : ''}`}
          onClick={() => setActiveSection('routes')}
        >
          <span className="nav-icon">⚡</span>
          <span>Route Tester</span>
        </button>
      </nav>

      {/* Main Content Sections */}
      <main className="main-content">
        {activeSection === 'health' && <ServiceHealthOverview />}
        {activeSection === 'products' && <ProductManagement />}
        {activeSection === 'orders' && <OrderManagement />}
        {activeSection === 'experiments' && <PerformanceExperiments />}
        {activeSection === 'routes' && <GatewayRouteTester />}
      </main>

      {/* Architecture Highlights Footer */}
      <footer className="app-footer">
        <div className="footer-architecture-summary">
          <span className="footer-tag">Spring Cloud Gateway (Port 8080)</span>
          <span className="footer-tag">User Service (Port 8081)</span>
          <span className="footer-tag">Product Service (Port 8082)</span>
          <span className="footer-tag">Order Service (Port 8083)</span>
          <span className="footer-tag">Neon PostgreSQL</span>
        </div>
        <p>Day 5 Implementation Milestone • React 18 + Vite • Centralized Gateway Architecture</p>
      </footer>
    </div>
  );
}
