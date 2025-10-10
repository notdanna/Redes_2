// src/services/api.js
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8081/api';

const SESSION_ID = 'user-' + Math.random().toString(36).substr(2, 9);

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'Session-Id': SESSION_ID
  }
});

const api = {
  getProducts: async () => {
    const response = await apiClient.get('/products');
    return response.data;
  },

  getProductsByType: async (type) => {
    const response = await apiClient.get(`/products?type=${type}`);
    return response.data;
  },

  searchProducts: async (query) => {
    const response = await apiClient.get(`/search?q=${encodeURIComponent(query)}`);
    return response.data;
  },

  getCart: async () => {
    const response = await apiClient.get('/cart');
    return response.data;
  },

  addToCart: async (productId, quantity) => {
    const response = await apiClient.post('/cart', {
      id: productId,
      quantity: quantity
    });
    return response.data;
  },

  updateCart: async (productId, quantity) => {
    const response = await apiClient.put('/cart', {
      id: productId,
      quantity: quantity
    });
    return response.data;
  },

  checkout: async () => {
    const response = await apiClient.post('/checkout');
    return response.data;
  }
};

export default api;