// src/App.jsx
import { useState, useEffect } from 'react';
import api from './services/api';
import Header from './components/Header';
import ProductList from './components/ProductList';
import Cart from './components/Cart';
import SearchBar from './components/SearchBar';
import Checkout from './components/Checkout';

function App() {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCart, setShowCart] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);
  const [selectedType, setSelectedType] = useState('all');

  useEffect(() => {
    loadProducts();
    loadCart();
  }, [selectedType]);

  const loadProducts = async () => {
    try {
      setLoading(true);
      const data = selectedType === 'all' 
        ? await api.getProducts()
        : await api.getProductsByType(selectedType);
      setProducts(data);
    } catch (error) {
      console.error('Error loading products:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadCart = async () => {
    try {
      const data = await api.getCart();
      setCart(data);
    } catch (error) {
      console.error('Error loading cart:', error);
    }
  };

  const handleSearch = async (query) => {
    if (!query.trim()) {
      loadProducts();
      return;
    }
    try {
      setLoading(true);
      const data = await api.searchProducts(query);
      setProducts(data);
    } catch (error) {
      console.error('Error searching:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAddToCart = async (productId, quantity) => {
    try {
      await api.addToCart(productId, quantity);
      await loadCart();
      await loadProducts();
    } catch (error) {
      alert(error.response?.data?.error || 'Error adding to cart');
    }
  };

  const handleUpdateCart = async (productId, quantity) => {
    try {
      await api.updateCart(productId, quantity);
      await loadCart();
      await loadProducts();
    } catch (error) {
      alert(error.response?.data?.error || 'Error updating cart');
    }
  };

  const handleCheckout = async () => {
    try {
      const receipt = await api.checkout();
      setShowCheckout(false);
      setShowCart(false);
      await loadCart();
      await loadProducts();
      alert(`Purchase completed! Total: $${receipt.total.toFixed(2)}`);
    } catch (error) {
      alert(error.response?.data?.error || 'Error processing checkout');
    }
  };

  const types = ['all', 'Interior', 'Exterior', 'Suculenta', 'Decorativa', 'Medicinal', 'Aromática'];

  return (
    <div className="min-h-screen bg-gray-50">
      <Header 
        cartCount={cart.length}
        onCartClick={() => setShowCart(!showCart)}
      />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">Garden Center</h1>
          <p className="text-gray-600">Find the perfect plants for your space</p>
        </div>

        <SearchBar onSearch={handleSearch} />

        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          {types.map(type => (
            <button
              key={type}
              onClick={() => setSelectedType(type)}
              className={`px-4 py-2 rounded-lg whitespace-nowrap transition-colors ${
                selectedType === type
                  ? 'bg-green-600 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-100'
              }`}
            >
              {type === 'all' ? 'Todos' : type}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="text-center py-12">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-green-600"></div>
          </div>
        ) : (
          <ProductList 
            products={products}
            onAddToCart={handleAddToCart}
          />
        )}
      </main>

      {showCart && (
        <Cart
          cart={cart}
          onClose={() => setShowCart(false)}
          onUpdateCart={handleUpdateCart}
          onCheckout={() => {
            setShowCart(false);
            setShowCheckout(true);
          }}
        />
      )}

      {showCheckout && (
        <Checkout
          cart={cart}
          onClose={() => setShowCheckout(false)}
          onConfirm={handleCheckout}
        />
      )}
    </div>
  );
}

export default App;