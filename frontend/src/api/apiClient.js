/**
 * Central API Client for communicating with the Spring Cloud Gateway.
 * All client requests flow through the Gateway base URL.
 */

const BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';

/**
 * Executes an HTTP fetch request against the Gateway with standard JSON headers and error handling.
 */
export async function apiRequest(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;
  
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    const correlationId = response.headers.get('X-Correlation-ID');

    let data = null;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = await response.text();
    }

    if (!response.ok) {
      const errorObj = typeof data === 'object' && data !== null ? data : { message: data };
      throw {
        status: response.status,
        statusText: response.statusText,
        correlationId,
        ...errorObj
      };
    }

    return {
      status: response.status,
      correlationId,
      data
    };
  } catch (error) {
    if (error.status) {
      throw error;
    }
    // Network failure / Gateway offline
    throw {
      status: 0,
      statusText: 'Network Error',
      message: error.message || 'Failed to reach API Gateway. Ensure Gateway is running on port 8080.',
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
  })
};

// Product Service API methods via Gateway
export const productApi = {
  listProducts: () => apiRequest('/api/products'),
  getProductById: (id) => apiRequest(`/api/products/${id}`),
  createProduct: (productData) => apiRequest('/api/products', {
    method: 'POST',
    body: JSON.stringify(productData)
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

// Direct health checks
export const healthApi = {
  checkGatewayHealth: () => apiRequest('/actuator/health'),
  checkDirectHealth: async (servicePort) => {
    try {
      const res = await fetch(`http://localhost:${servicePort}/actuator/health`);
      const data = await res.json();
      return { status: res.status, data };
    } catch (e) {
      return { status: 0, error: e.message };
    }
  }
};

export default {
  BASE_URL,
  apiRequest,
  userApi,
  productApi,
  orderApi,
  healthApi
};
