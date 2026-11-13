import React, { useState, useEffect } from 'react';
import { orderApi } from '../api/apiClient';
import ErrorDisplay from './ErrorDisplay';

export default function OrderManagement() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // Order creation form state
  const [showCreateForm, setShowCreateForm] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [createdOrderResult, setCreatedOrderResult] = useState(null);

  const [formUserId, setFormUserId] = useState('1');
  const [formProductId, setFormProductId] = useState('1');
  const [formQuantity, setFormQuantity] = useState('1');

  const loadOrders = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await orderApi.listOrders();
      setOrders(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setError(err);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, []);

  const viewOrderDetails = async (id) => {
    setLoadingDetail(true);
    try {
      const res = await orderApi.getOrderById(id);
      setSelectedOrder(res.data);
    } catch (err) {
      setError(err);
    } finally {
      setLoadingDetail(false);
    }
  };

  const handleCreateOrder = async (e) => {
    e.preventDefault();
    setError(null);
    setCreatedOrderResult(null);

    const uId = parseInt(formUserId, 10);
    const pId = parseInt(formProductId, 10);
    const qty = parseInt(formQuantity, 10);

    if (isNaN(uId) || uId <= 0) {
      setError({
        status: 400,
        error: 'Validation Error',
        message: 'User ID must be a positive number.',
        path: '/api/orders'
      });
      return;
    }
    if (isNaN(pId) || pId <= 0) {
      setError({
        status: 400,
        error: 'Validation Error',
        message: 'Product ID must be a positive number.',
        path: '/api/orders'
      });
      return;
    }
    if (isNaN(qty) || qty <= 0) {
      setError({
        status: 400,
        error: 'Validation Error',
        message: 'Quantity must be at least 1.',
        path: '/api/orders'
      });
      return;
    }

    const payload = {
      userId: uId,
      items: [
        {
          productId: pId,
          quantity: qty
        }
      ]
    };

    setSubmitting(true);
    try {
      const res = await orderApi.createOrder(payload);
      setCreatedOrderResult(res.data);
      // Reload order list
      await loadOrders();
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="management-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Order Management</h2>
          <p className="section-subtitle">
            Create and track purchase orders with multi-service validation via <code>/api/orders</code>.
          </p>
        </div>
        <div className="btn-group-inline">
          <button
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="btn-secondary"
          >
            {showCreateForm ? 'Hide Order Form' : '+ New Order'}
          </button>
          <button
            onClick={loadOrders}
            disabled={loading}
            className="btn-primary"
          >
            {loading ? 'Refreshing...' : '↻ Refresh Orders'}
          </button>
        </div>
      </div>

      <ErrorDisplay error={error} onDismiss={() => setError(null)} />

      {/* Order Creation Form */}
      {showCreateForm && (
        <div className="form-card">
          <h3 style={{ marginBottom: '0.75rem', color: '#60a5fa' }}>Place New Order</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
            Inter-service validation will verify User existence in User Service and check Product stock/pricing in Product Service.
          </p>

          <form onSubmit={handleCreateOrder}>
            <div className="form-grid">
              <div className="form-group">
                <label>User ID *</label>
                <input
                  type="number"
                  min="1"
                  required
                  placeholder="e.g. 1"
                  value={formUserId}
                  onChange={e => setFormUserId(e.target.value)}
                />
                <span className="field-hint">Existing user ID (e.g. 1)</span>
              </div>

              <div className="form-group">
                <label>Product ID *</label>
                <input
                  type="number"
                  min="1"
                  required
                  placeholder="e.g. 1"
                  value={formProductId}
                  onChange={e => setFormProductId(e.target.value)}
                />
                <span className="field-hint">Catalog product ID (e.g. 1)</span>
              </div>

              <div className="form-group">
                <label>Quantity *</label>
                <input
                  type="number"
                  min="1"
                  required
                  placeholder="e.g. 2"
                  value={formQuantity}
                  onChange={e => setFormQuantity(e.target.value)}
                />
                <span className="field-hint">Number of units to purchase</span>
              </div>
            </div>

            <div style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
              <button
                type="submit"
                disabled={submitting}
                className="btn-primary"
              >
                {submitting ? 'Placing Order...' : 'Submit Order (POST /api/orders)'}
              </button>
            </div>
          </form>

          {/* Created Order Confirmation */}
          {createdOrderResult && (
            <div className="order-result-box">
              <div className="order-result-header">
                <span className="badge badge-online">Order Created Successfully</span>
                <span className="order-number">Order #{createdOrderResult.id}</span>
              </div>
              <div className="order-result-grid">
                <div>
                  <span className="meta-label">Customer User ID:</span>
                  <span className="meta-value">#{createdOrderResult.userId}</span>
                </div>
                <div>
                  <span className="meta-label">Order Status:</span>
                  <span className="meta-value"><strong>{createdOrderResult.status}</strong></span>
                </div>
                <div>
                  <span className="meta-label">Calculated Grand Total:</span>
                  <span className="meta-value total-highlight">
                    ${typeof createdOrderResult.totalAmount === 'number' ? createdOrderResult.totalAmount.toFixed(2) : createdOrderResult.totalAmount}
                  </span>
                </div>
                <div>
                  <span className="meta-label">Timestamp:</span>
                  <span className="meta-value">{new Date(createdOrderResult.createdAt || Date.now()).toLocaleTimeString()}</span>
                </div>
              </div>

              {createdOrderResult.items && createdOrderResult.items.length > 0 && (
                <div style={{ marginTop: '1rem' }}>
                  <h4 style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Line Items:</h4>
                  <table className="mini-table">
                    <thead>
                      <tr>
                        <th>Product ID</th>
                        <th style={{ textAlign: 'center' }}>Quantity</th>
                        <th style={{ textAlign: 'right' }}>Unit Price</th>
                        <th style={{ textAlign: 'right' }}>Subtotal</th>
                      </tr>
                    </thead>
                    <tbody>
                      {createdOrderResult.items.map((it, idx) => (
                        <tr key={idx}>
                          <td><code>Product #{it.productId}</code></td>
                          <td style={{ textAlign: 'center' }}>{it.quantity}</td>
                          <td style={{ textAlign: 'right' }}>${typeof it.unitPrice === 'number' ? it.unitPrice.toFixed(2) : it.unitPrice}</td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}>${typeof it.subtotal === 'number' ? it.subtotal.toFixed(2) : it.subtotal}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Orders List Table */}
      {loading ? (
        <div className="state-card loading-state">
          <div className="spinner"></div>
          <p>Fetching orders from API Gateway...</p>
        </div>
      ) : orders.length === 0 ? (
        <div className="state-card empty-state">
          <span className="state-icon">📋</span>
          <h3>No Orders Yet</h3>
          <p>No customer orders have been placed yet. Use the form above to place your first order.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '80px' }}>Order ID</th>
                <th>Customer ID</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Total Amount</th>
                <th>Created At</th>
                <th style={{ width: '120px', textAlign: 'center' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td><code>#{o.id}</code></td>
                  <td>User #{o.userId}</td>
                  <td>
                    <span className="badge badge-online">{o.status || 'CREATED'}</span>
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 600, color: '#34d399' }}>
                    ${typeof o.totalAmount === 'number' ? o.totalAmount.toFixed(2) : o.totalAmount}
                  </td>
                  <td>
                    {o.createdAt ? new Date(o.createdAt).toLocaleString() : '—'}
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <button
                      onClick={() => viewOrderDetails(o.id)}
                      className="btn-table-action"
                      title="Inspect Order Details & Line Items"
                    >
                      Details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Order Detail Modal */}
      {selectedOrder && (
        <div className="modal-backdrop" onClick={() => setSelectedOrder(null)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Order Inspection (ID #{selectedOrder.id})</h3>
              <button className="modal-close-btn" onClick={() => setSelectedOrder(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="detail-row">
                <span className="detail-label">Order ID:</span>
                <span className="detail-val">#{selectedOrder.id}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">User ID:</span>
                <span className="detail-val">#{selectedOrder.userId}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Status:</span>
                <span className="detail-val">
                  <span className="badge badge-online">{selectedOrder.status}</span>
                </span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Calculated Total:</span>
                <span className="detail-val price-highlight">
                  ${typeof selectedOrder.totalAmount === 'number' ? selectedOrder.totalAmount.toFixed(2) : selectedOrder.totalAmount}
                </span>
              </div>
              {selectedOrder.createdAt && (
                <div className="detail-row">
                  <span className="detail-label">Placed At:</span>
                  <span className="detail-val">{new Date(selectedOrder.createdAt).toLocaleString()}</span>
                </div>
              )}

              {selectedOrder.items && selectedOrder.items.length > 0 && (
                <div style={{ marginTop: '1.25rem' }}>
                  <h4 style={{ fontSize: '0.9rem', marginBottom: '0.5rem', color: '#93c5fd' }}>Purchased Line Items</h4>
                  <table className="mini-table">
                    <thead>
                      <tr>
                        <th>Product ID</th>
                        <th style={{ textAlign: 'center' }}>Quantity</th>
                        <th style={{ textAlign: 'right' }}>Unit Price</th>
                        <th style={{ textAlign: 'right' }}>Subtotal</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedOrder.items.map((it, idx) => (
                        <tr key={idx}>
                          <td><code>Product #{it.productId}</code></td>
                          <td style={{ textAlign: 'center' }}>{it.quantity}</td>
                          <td style={{ textAlign: 'right' }}>${typeof it.unitPrice === 'number' ? it.unitPrice.toFixed(2) : it.unitPrice}</td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}>${typeof it.subtotal === 'number' ? it.subtotal.toFixed(2) : it.subtotal}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setSelectedOrder(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

