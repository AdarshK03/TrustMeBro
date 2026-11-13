/**
 * Central API Client for communicating with the Spring Cloud Gateway.
 * All client requests flow through the Gateway base URL (default: http://localhost:8080).
 * No UI component should contact downstream microservices directly.
 */

const RAW_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';
export const BASE_URL = RAW_BASE_URL.replace(/\/+$/, '');

// In-memory or persisted auth token for JWT-secured gateway routes
let authToken = typeof window !== 'undefined' ? localStorage.getItem('auth_token') || '' : '';
let currentUser = typeof window !== 'undefined' ? JSON.parse(localStorage.getItem('auth_user') || 'null') : null;

export function setAuthToken(token, user = null) {
  authToken = token || '';
  currentUser = user;
  if (typeof window !== 'undefined') {
    if (token) {
      localStorage.setItem('auth_token', token);
      if (user) localStorage.setItem('auth_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
    }
  }
}

export function getAuthToken() {
  return authToken;
}

export function getCurrentUser() {
  return currentUser;
}

export function clearAuthToken() {
  setAuthToken('', null);
}

/**
 * Standardized HTTP request handler targeting the API Gateway.
 * Supports GET, POST, PUT, DELETE with JSON payloads, correlation headers,
 * and unified error normalization.
 */
export async function apiRequest(endpoint, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const url = `${BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const headers = {
    'Accept': 'application/json',
    ...(options.headers || {})
  };

  // Attach Content-Type for requests with body
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  // Attach Bearer token if available and not explicitly provided
  if (authToken && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }

  try {
    const response = await fetch(url, {
      ...options,
      method,
      headers
    });

    const correlationId = response.headers.get('X-Correlation-ID') || response.headers.get('requestId') || null;

    // Handle 204 No Content
    if (response.status === 204) {
      return {
        status: 204,
        correlationId,
        data: null
      };
    }

    let responseData = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      responseData = await response.json().catch(() => null);
    } else {
      responseData = await response.text().catch(() => null);
    }

    if (!response.ok) {
      // Standardize error envelope
      const errorEnvelope = {
        timestamp: responseData?.timestamp || new Date().toISOString(),
        status: response.status,
        error: responseData?.error || response.statusText || 'Error',
        message: responseData?.message || (typeof responseData === 'string' ? responseData : 'An unexpected error occurred'),
        path: responseData?.path || endpoint,
        requestId: responseData?.requestId || correlationId,
        correlationId
      };
      throw errorEnvelope;
    }

    return {
      status: response.status,
      correlationId,
      data: responseData
    };
  } catch (error) {
    if (error.status !== undefined) {
      // Already formatted HTTP error response
      throw error;
    }

    // Network / Gateway offline failure
    throw {
      timestamp: new Date().toISOString(),
      status: 0,
      error: 'Gateway Unavailable',
      message: error.message || `Unable to connect to API Gateway at ${BASE_URL}. Ensure Gateway is running on port 8080.`,
      path: endpoint,
      requestId: null,
      correlationId: null
    };
  }
}

// User Service API methods via Gateway
export const userApi = {
  listUsers: () => apiRequest('/api/users'),
  getUserById: (id) => apiRequest(`/api/users/${id}`),
  createUser: (userData) => apiRequest('/api/users', {
    method: 'POST',
    body: JSON.stringify(userData)
  }),
  login: async (credentials) => {
    const res = await apiRequest('/api/users/login', {
      method: 'POST',
      body: JSON.stringify(credentials)
    });
    if (res.data?.token) {
      setAuthToken(res.data.token, res.data.user || null);
    }
    return res;
  }
};

// Product Service API methods via Gateway
export const productApi = {
  listProducts: () => apiRequest('/api/products'),
  getProductById: (id) => apiRequest(`/api/products/${id}`),
  createProduct: (productData) => apiRequest('/api/products', {
    method: 'POST',
    body: JSON.stringify(productData)
  }),
  updateProduct: (id, productData) => apiRequest(`/api/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(productData)
  }),
  deleteProduct: (id) => apiRequest(`/api/products/${id}`, {
    method: 'DELETE'
  })
};

// Order Service API methods via Gateway
export const orderApi = {
  listOrders: () => apiRequest('/api/orders'),
  getOrderById: (id) => apiRequest(`/api/orders/${id}`),
  createOrder: (orderData) => apiRequest('/api/orders', {
    method: 'POST',
    body: JSON.stringify(orderData)
  })
};

// Gateway-compatible Health Check API methods (All routed through Gateway port 8080)
export const healthApi = {
  checkGatewayHealth: () => apiRequest('/actuator/health'),
  checkUserServiceHealth: () => apiRequest('/api/health/users'),
  checkProductServiceHealth: () => apiRequest('/api/health/products'),
  checkOrderServiceHealth: () => apiRequest('/api/health/orders'),
  checkServiceHealth: async (serviceId) => {
    switch (serviceId) {
      case 'gw':
        return healthApi.checkGatewayHealth();
      case 'user':
        return healthApi.checkUserServiceHealth();
      case 'product':
        return healthApi.checkProductServiceHealth();
      case 'order':
        return healthApi.checkOrderServiceHealth();
      default:
        throw new Error(`Unknown service: ${serviceId}`);
    }
  }
};

export default {
  BASE_URL,
  apiRequest,
  userApi,
  productApi,
  orderApi,
  healthApi,
  setAuthToken,
  getAuthToken,
  getCurrentUser,
  clearAuthToken
};
