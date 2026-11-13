import React from 'react';

/**
 * Standardized API Error Display Component.
 * Visualizes errors formatted using the canonical platform error envelope:
 * { timestamp, status, error, message, path, requestId, correlationId }
 */
export default function ErrorDisplay({ error, onDismiss }) {
  if (!error) return null;

  const status = error.status !== undefined ? error.status : null;
  const correlationId = error.correlationId || error.requestId || 'N/A';
  const path = error.path || 'N/A';
  const message = error.message || (typeof error === 'string' ? error : 'An unexpected error occurred');

  // Determine status label, badge style, and user guidance
  let badgeClass = 'badge badge-error';
  let statusText = 'Error';
  let guidance = null;

  if (status === 0) {
    statusText = 'Gateway Offline / Network Error';
    guidance = 'Could not establish connection to the API Gateway on port 8080. Verify that the Gateway service is running.';
  } else if (status === 400) {
    statusText = '400 Bad Request';
    guidance = 'The request payload failed validation or contained malformed syntax. Please verify required fields.';
  } else if (status === 401) {
    statusText = '401 Unauthorized';
    guidance = 'Authentication token is missing, expired, or invalid. Please log in to obtain a valid JWT token.';
  } else if (status === 403) {
    statusText = '403 Forbidden';
    guidance = 'You do not have the required role (e.g. ADMIN) to perform this operation.';
  } else if (status === 404) {
    statusText = '404 Not Found';
    guidance = 'The requested resource identifier does not exist in the upstream microservice.';
  } else if (status === 409) {
    statusText = '409 Conflict';
    guidance = 'The entity could not be created or updated due to a conflict (e.g., duplicate email or unique constraint).';
  } else if (status === 429) {
    statusText = '429 Too Many Requests';
    guidance = 'Rate limit exceeded. The API Gateway token-bucket has throttled this request to protect downstream services.';
  } else if (status >= 500) {
    statusText = `${status} Internal Server Error`;
    guidance = 'A downstream service encountered an unexpected error or is currently unavailable (503).';
  }

  return (
    <div className="error-banner" role="alert">
      <div className="error-header">
        <div className="error-title-group">
          <span className="error-icon">⚠️</span>
          <span className="error-status-badge">{statusText}</span>
          {status !== null && status !== 0 && (
            <span className="error-code">HTTP {status}</span>
          )}
        </div>
        {onDismiss && (
          <button 
            type="button" 
            className="error-dismiss-btn" 
            onClick={onDismiss}
            aria-label="Dismiss error"
          >
            ✕
          </button>
        )}
      </div>

      <div className="error-body">
        <p className="error-message"><strong>Message:</strong> {message}</p>
        {guidance && <p className="error-guidance">{guidance}</p>}
      </div>

      <div className="error-metadata">
        <div className="meta-item">
          <span className="meta-label">Path:</span>
          <code>{path}</code>
        </div>
        <div className="meta-item">
          <span className="meta-label">Correlation ID:</span>
          <code className="meta-code-highlight">{correlationId}</code>
        </div>
        {error.timestamp && (
          <div className="meta-item">
            <span className="meta-label">Timestamp:</span>
            <span>{new Date(error.timestamp).toLocaleTimeString()}</span>
          </div>
        )}
      </div>
    </div>
  );
}

