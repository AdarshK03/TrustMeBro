import React, { useState, useEffect } from 'react';
import { userApi, setAuthToken, getAuthToken, getCurrentUser, clearAuthToken } from '../api/apiClient';

export default function AuthBar({ onAuthChange }) {
  const [currentUser, setCurrentUserState] = useState(getCurrentUser());
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [email, setEmail] = useState('alice.dev@example.com');
  const [password, setPassword] = useState('Admin@123');
  const [manualToken, setManualToken] = useState('');
  const [isManualTokenOpen, setIsManualTokenOpen] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [authError, setAuthError] = useState(null);

  useEffect(() => {
    setCurrentUserState(getCurrentUser());
  }, []);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginLoading(true);
    setAuthError(null);
    try {
      const res = await userApi.login({ email, password });
      const user = res.data?.user || { email, role: 'UNKNOWN' };
      setCurrentUserState(user);
      setShowLoginModal(false);
      if (onAuthChange) onAuthChange(user);
    } catch (err) {
      setAuthError(err.message || 'Login failed. Verify credentials or ensure User Service is running.');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleLogout = () => {
    clearAuthToken();
    setCurrentUserState(null);
    if (onAuthChange) onAuthChange(null);
  };

  const handleQuickLogin = async (userType) => {
    setLoginLoading(true);
    setAuthError(null);
    const creds = userType === 'ADMIN' 
      ? { email: 'alice.dev@example.com', password: 'Admin@123' }
      : { email: 'bob.cust@example.com', password: 'Customer@123' };

    try {
      const res = await userApi.login(creds);
      const user = res.data?.user || { email: creds.email, role: userType };
      setCurrentUserState(user);
      setShowLoginModal(false);
      if (onAuthChange) onAuthChange(user);
    } catch (err) {
      setAuthError(err.message || 'Quick login failed. You can paste a JWT token manually.');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleApplyManualToken = () => {
    if (!manualToken.trim()) return;
    setAuthToken(manualToken.trim(), { email: 'manual.jwt@example.com', role: 'CUSTOM' });
    setCurrentUserState({ email: 'manual.jwt@example.com', role: 'CUSTOM' });
    setIsManualTokenOpen(false);
    setShowLoginModal(false);
    if (onAuthChange) onAuthChange({ email: 'manual.jwt@example.com', role: 'CUSTOM' });
  };

  return (
    <div className="auth-bar">
      <div className="auth-status-indicator">
        <span className="auth-dot" style={{ backgroundColor: currentUser ? '#34d399' : '#9ca3af' }}></span>
        {currentUser ? (
          <span className="auth-info">
            Authenticated as <strong>{currentUser.email || currentUser.name}</strong> 
            <span className="badge badge-online" style={{ marginLeft: '0.5rem' }}>{currentUser.role || 'USER'}</span>
          </span>
        ) : (
          <span className="auth-info" style={{ color: 'var(--text-muted)' }}>
            Unauthenticated (Public Ingress Mode)
          </span>
        )}
      </div>

      <div className="auth-actions">
        {currentUser ? (
          <button className="btn-secondary btn-sm" onClick={handleLogout}>
            Sign Out
          </button>
        ) : (
          <button className="btn-secondary btn-sm" onClick={() => setShowLoginModal(true)}>
            Sign In / Set Token
          </button>
        )}
      </div>

      {/* Login Modal */}
      {showLoginModal && (
        <div className="modal-backdrop" onClick={() => setShowLoginModal(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <h3>Gateway Authentication</h3>
              <button className="modal-close-btn" onClick={() => setShowLoginModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                When Gateway JWT security is enabled, protected endpoints (like order management) require a valid token.
              </p>

              {authError && (
                <div className="error-banner" style={{ padding: '0.6rem', marginBottom: '1rem', fontSize: '0.85rem' }}>
                  {authError}
                </div>
              )}

              <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ flex: 1, fontSize: '0.8rem' }}
                  onClick={() => handleQuickLogin('ADMIN')}
                  disabled={loginLoading}
                >
                  ⚡ Alice (ADMIN)
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ flex: 1, fontSize: '0.8rem' }}
                  onClick={() => handleQuickLogin('CUSTOMER')}
                  disabled={loginLoading}
                >
                  ⚡ Bob (CUSTOMER)
                </button>
              </div>

              <form onSubmit={handleLogin}>
                <div className="form-group" style={{ marginBottom: '0.75rem' }}>
                  <label>Email Address</label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                  />
                </div>
                <div className="form-group" style={{ marginBottom: '1rem' }}>
                  <label>Password</label>
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                  />
                </div>
                <button
                  type="submit"
                  disabled={loginLoading}
                  className="btn-primary"
                  style={{ width: '100%', marginBottom: '0.75rem' }}
                >
                  {loginLoading ? 'Authenticating...' : 'Sign In via /api/users/login'}
                </button>
              </form>

              <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '0.75rem', marginTop: '0.75rem' }}>
                <button
                  type="button"
                  className="btn-table-action"
                  style={{ width: '100%' }}
                  onClick={() => setIsManualTokenOpen(!isManualTokenOpen)}
                >
                  {isManualTokenOpen ? 'Hide Manual Token' : 'Or Paste Custom JWT Token...'}
                </button>

                {isManualTokenOpen && (
                  <div style={{ marginTop: '0.75rem' }}>
                    <textarea
                      rows={3}
                      placeholder="eyJhbGciOiJIUzI1NiJ9..."
                      value={manualToken}
                      onChange={e => setManualToken(e.target.value)}
                      style={{ width: '100%', backgroundColor: 'var(--code-bg)', border: '1px solid var(--border-color)', color: '#e5e7eb', padding: '0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontFamily: 'monospace' }}
                    />
                    <button
                      type="button"
                      className="btn-secondary"
                      style={{ marginTop: '0.5rem', width: '100%', fontSize: '0.8rem' }}
                      onClick={handleApplyManualToken}
                    >
                      Apply JWT Token
                    </button>
                  </div>
                )}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setShowLoginModal(false)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

