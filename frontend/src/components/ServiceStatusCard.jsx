import React from 'react';

export default function ServiceStatusCard({ name, port, prefix, status, onPing, loading }) {
  const getBadgeClass = (s) => {
    if (s === 'UP') return 'badge badge-online';
    if (s === 'DOWN' || s === 'OFFLINE') return 'badge badge-offline';
    return 'badge badge-checking';
  };

  return (
    <div className="card">
      <h3>
        {name}
        <span className={getBadgeClass(status)}>{status}</span>
      </h3>
      <p><strong>Port:</strong> {port}</p>
      <p><strong>Gateway Path:</strong> <code>{prefix}</code></p>
      <button 
        className="btn-secondary" 
        onClick={onPing} 
        disabled={loading}
        style={{ marginTop: '0.5rem', width: '100%', fontSize: '0.8rem', padding: '0.4rem' }}
      >
        {loading ? 'Checking...' : 'Ping /actuator/health'}
      </button>
    </div>
  );
}
