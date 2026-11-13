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
        correlationId: err.correlationId || 'N/A',
        data: err
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="route-tester">
      <h3 style={{ marginBottom: '1rem' }}>Interactive Gateway Route Verification</h3>
      <p style={{ color: 'var(--text-muted)', marginBottom: '1.25rem', fontSize: '0.9rem' }}>
        Trigger API calls routed through the Spring Cloud Gateway (<code>http://localhost:8080</code>).
        Notice the <code>X-Correlation-ID</code> generated and attached to each request.
      </p>

      <div className="btn-group">
        <button 
          onClick={() => execute('GET /api/users', () => userApi.listUsers())}
          disabled={loading}
        >
          GET /api/users
        </button>

        <button 
          onClick={() => execute('POST /api/users', () => userApi.createUser({
            name: `Test User ${Math.floor(Math.random() * 1000)}`,
            email: `user${Date.now()}@example.com`,
            role: 'CUSTOMER'
          }))}
          disabled={loading}
        >
          POST /api/users (Create User)
        </button>

        <button 
          onClick={() => execute('GET /api/products', () => productApi.listProducts())}
          disabled={loading}
        >
          GET /api/products
        </button>

        <button 
          onClick={() => execute('POST /api/products', () => productApi.createProduct({
            name: `Product ${Math.floor(Math.random() * 1000)}`,
            description: 'Item created from gateway route tester',
            price: 49.99,
            stock: 25
          }))}
          disabled={loading}
        >
          POST /api/products (Create Product)
        </button>

        <button 
          onClick={() => execute('GET /api/orders', () => orderApi.listOrders())}
          disabled={loading}
        >
          GET /api/orders
        </button>

        <button 
          onClick={() => execute('POST /api/orders', () => orderApi.createOrder({
            userId: 1,
            items: [{ productId: 1, quantity: 2 }]
          }))}
          disabled={loading}
        >
          POST /api/orders (Create Order)
        </button>

        <button 
          className="btn-secondary"
          onClick={() => execute('GET /actuator/health (Gateway)', () => healthApi.checkGatewayHealth())}
          disabled={loading}
        >
          Gateway Health
        </button>
      </div>

      <div className="meta-row">
        <span>Action: <strong>{activeAction || 'None'}</strong></span>
        <span>Correlation ID: <strong>{result?.correlationId || 'N/A'}</strong></span>
        <span>HTTP Status: <strong>{result?.status ?? 'Waiting'}</strong></span>
      </div>

      <pre className="response-box">
        {loading ? 'Executing request via API Gateway...' :
          result ? JSON.stringify(result.data, null, 2) :
          '// Click one of the buttons above to test gateway routing...'}
      </pre>
    </div>
  );
}
