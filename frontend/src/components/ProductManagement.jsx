import React, { useState, useEffect } from 'react';
import { productApi } from '../api/apiClient';
import ErrorDisplay from './ErrorDisplay';

export default function ProductManagement() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createSuccessMsg, setCreateSuccessMsg] = useState(null);

  const [newProduct, setNewProduct] = useState({
    name: '',
    description: '',
    price: '',
    stock: ''
  });

  const loadProducts = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await productApi.listProducts();
      setProducts(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setError(err);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProducts();
  }, []);

  const viewProductDetails = async (id) => {
    setLoadingDetails(true);
    try {
      const res = await productApi.getProductById(id);
      setSelectedProduct(res.data);
    } catch (err) {
      setError(err);
    } finally {
      setLoadingDetails(false);
    }
  };

  const handleCreateProduct = async (e) => {
    e.preventDefault();
    setCreating(true);
    setError(null);
    setCreateSuccessMsg(null);

    const payload = {
      name: newProduct.name.trim(),
      description: newProduct.description.trim(),
      price: parseFloat(newProduct.price),
      stock: parseInt(newProduct.stock, 10)
    };

    try {
      const res = await productApi.createProduct(payload);
      setCreateSuccessMsg(`Product "${res.data.name}" (ID #${res.data.id}) successfully created!`);
      setNewProduct({ name: '', description: '', price: '', stock: '' });
      setShowAddForm(false);
      await loadProducts();
    } catch (err) {
      setError(err);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="management-section">
      <div className="section-header">
        <div>
          <h2 className="section-title">Product Management</h2>
          <p className="section-subtitle">
            Browse and manage catalog items via Gateway route <code>GET /api/products</code>.
          </p>
        </div>
        <div className="btn-group-inline">
          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="btn-secondary"
          >
            {showAddForm ? '✕ Close Form' : '+ New Product'}
          </button>
          <button
            onClick={loadProducts}
            disabled={loading}
            className="btn-primary"
          >
            {loading ? 'Refreshing...' : '↻ Refresh Listing'}
          </button>
        </div>
      </div>

      <ErrorDisplay error={error} onDismiss={() => setError(null)} />

      {createSuccessMsg && (
        <div className="success-banner">
          <span>✓ {createSuccessMsg}</span>
          <button className="banner-close" onClick={() => setCreateSuccessMsg(null)}>✕</button>
        </div>
      )}

      {showAddForm && (
        <div className="form-card">
          <h3 style={{ marginBottom: '1rem', color: '#60a5fa' }}>Create New Catalog Product</h3>
          <form onSubmit={handleCreateProduct}>
            <div className="form-grid">
              <div className="form-group">
                <label>Product Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Ergonomic Wireless Mouse"
                  value={newProduct.name}
                  onChange={e => setNewProduct({ ...newProduct, name: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Price (USD) *</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  placeholder="e.g. 49.99"
                  value={newProduct.price}
                  onChange={e => setNewProduct({ ...newProduct, price: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Available Stock *</label>
                <input
                  type="number"
                  min="0"
                  step="1"
                  required
                  placeholder="e.g. 100"
                  value={newProduct.stock}
                  onChange={e => setNewProduct({ ...newProduct, stock: e.target.value })}
                />
              </div>

              <div className="form-group form-group-full">
                <label>Description</label>
                <input
                  type="text"
                  placeholder="Brief item specifications and features"
                  value={newProduct.description}
                  onChange={e => setNewProduct({ ...newProduct, description: e.target.value })}
                />
              </div>
            </div>

            <div style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowAddForm(false)}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={creating}
                className="btn-primary"
              >
                {creating ? 'Saving Product...' : 'Create Product (POST /api/products)'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Product Listing Table */}
      {loading ? (
        <div className="state-card loading-state">
          <div className="spinner"></div>
          <p>Fetching catalog products from API Gateway...</p>
        </div>
      ) : products.length === 0 ? (
        <div className="state-card empty-state">
          <span className="state-icon">📦</span>
          <h3>No Products Found</h3>
          <p>No catalog items are currently registered. Click "+ New Product" or enable seed data.</p>
          <button onClick={loadProducts} className="btn-secondary" style={{ marginTop: '0.75rem' }}>
            Check Again
          </button>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '70px' }}>ID</th>
                <th>Name</th>
                <th>Description</th>
                <th style={{ textAlign: 'right' }}>Price</th>
                <th style={{ textAlign: 'center' }}>Stock</th>
                <th style={{ width: '120px', textAlign: 'center' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => {
                const stock = p.stock ?? 0;
                let stockBadgeClass = 'badge badge-in-stock';
                let stockLabel = `${stock} in stock`;
                if (stock <= 0) {
                  stockBadgeClass = 'badge badge-out-of-stock';
                  stockLabel = 'Out of stock';
                } else if (stock < 10) {
                  stockBadgeClass = 'badge badge-low-stock';
                  stockLabel = `${stock} remaining`;
                }

                return (
                  <tr key={p.id}>
                    <td><code>#{p.id}</code></td>
                    <td className="product-title-cell">
                      <strong>{p.name}</strong>
                    </td>
                    <td className="product-desc-cell">{p.description || '—'}</td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>
                      ${typeof p.price === 'number' ? p.price.toFixed(2) : p.price}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span className={stockBadgeClass}>{stockLabel}</span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <button
                        onClick={() => viewProductDetails(p.id)}
                        className="btn-table-action"
                        title="View Product Details"
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Product Detail Modal */}
      {selectedProduct && (
        <div className="modal-backdrop" onClick={() => setSelectedProduct(null)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Product Details (ID #{selectedProduct.id})</h3>
              <button className="modal-close-btn" onClick={() => setSelectedProduct(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="detail-row">
                <span className="detail-label">Name:</span>
                <span className="detail-val"><strong>{selectedProduct.name}</strong></span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Price:</span>
                <span className="detail-val price-highlight">
                  ${typeof selectedProduct.price === 'number' ? selectedProduct.price.toFixed(2) : selectedProduct.price}
                </span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Available Inventory:</span>
                <span className="detail-val">{selectedProduct.stock} units</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Description:</span>
                <span className="detail-val">{selectedProduct.description || 'No description provided'}</span>
              </div>
              {selectedProduct.createdAt && (
                <div className="detail-row">
                  <span className="detail-label">Created At:</span>
                  <span className="detail-val">{new Date(selectedProduct.createdAt).toLocaleString()}</span>
                </div>
              )}
              {selectedProduct.updatedAt && (
                <div className="detail-row">
                  <span className="detail-label">Updated At:</span>
                  <span className="detail-val">{new Date(selectedProduct.updatedAt).toLocaleString()}</span>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setSelectedProduct(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

