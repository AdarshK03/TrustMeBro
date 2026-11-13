import React, { useState } from 'react';
import { userApi, productApi, orderApi, healthApi } from '../api/apiClient';

export default function GatewayRouteTester() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [activeAction, setActiveAction] = useState('');

  const execute = async (name, apiCall) => {
    setLoading(true);
    setActiveAction(name);
    setResult(null);

    try {
      const res = await apiCall();
      setResult({
        status: res.status,
        correlationId: res.correlationId || 'N/A',
        data: res.data
      });
    } catch (err) {
      setResult({
        status: err.status || 'ERROR',
        correlationId: err.correlationId || err.requestId || 'N/A',
        data: err
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="management-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Gateway Route Verification</h2>
          <p className="section-subtitle">
            Execute ad-hoc HTTP operations routed through the Spring Cloud Gateway at port 8080.
          </p>
        </div>
      </div>

      <div className="route-tester-panel">
        <div className="btn-group">
          <button 
            onClick={() => execute('GET /api/products', () => productApi.listProducts())}
            disabled={loading}
            className="btn-primary"
          >
            GET /api/products
          </button>

          <button 
            onClick={() => execute('GET /api/users', () => userApi.listUsers())}
            disabled={loading}
            className="btn-primary"
          >
            GET /api/users
          </button>

          <button 
            onClick={() => execute('GET /api/orders', () => orderApi.listOrders())}
            disabled={loading}
            className="btn-primary"
          >
            GET /api/orders
          </button>

          <button 
            onClick={() => execute('POST /api/products', () => productApi.createProduct({
              name: `Sample Item ${Math.floor(Math.random() * 1000)}`,
              description: 'Created from Gateway Route Tester',
              price: 49.99,
              stock: 30
            }))}
            disabled={loading}
            className="btn-secondary"
          >
            POST /api/products
          </button>

          <button 
            onClick={() => execute('POST /api/orders', () => orderApi.createOrder({
              userId: 1,
              items: [{ productId: 1, quantity: 2 }]
            }))}
            disabled={loading}
            className="btn-secondary"
          >
            POST /api/orders
          </button>

          <button 
            onClick={() => execute('GET /actuator/health (Gateway)', () => healthApi.checkGatewayHealth())}
            disabled={loading}
            className="btn-secondary"
          >
            Gateway /actuator/health
          </button>
        </div>

        <div className="meta-row">
          <span>Action: <strong>{activeAction || 'None selected'}</strong></span>
          <span>Correlation ID: <strong>{result?.correlationId || 'N/A'}</strong></span>
          <span>HTTP Status: <strong>{result?.status ?? 'Idle'}</strong></span>
        </div>

        <pre className="response-box">
          {loading ? 'Executing request via API Gateway...' :
            result ? JSON.stringify(result.data, null, 2) :
            '// Select an action above to trigger an API Gateway routed request...'}
        </pre>
      </div>
    </div>
  );
}
