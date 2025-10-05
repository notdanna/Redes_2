// src/components/ProductCard.jsx
import { useState } from 'react';

function ProductCard({ product, onAddToCart }) {
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);

  const handleAdd = async () => {
    setAdding(true);
    try {
      await onAddToCart(product.id, quantity);
      setQuantity(1);
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow">
      
      {/* CORREGIDO: Contenedor único con posición relativa */}
      <div className="aspect-square bg-gray-100 relative overflow-hidden">
        <img 
          src={`http://localhost:8080${product.imageUrl}`}
          alt={product.name}
          className="w-full h-full object-cover absolute inset-0"
          onError={(e) => {
            console.error('Error loading image:', `http://localhost:8080${product.imageUrl}`);
            e.target.style.display = 'none';
            e.target.nextElementSibling.style.display = 'flex';
          }}
          onLoad={() => {
            console.log('Image loaded:', product.name);
          }}
        />
        <div className="w-full h-full absolute inset-0 hidden items-center justify-center text-gray-400 bg-gray-100">
          <div className="text-center p-4">
            <svg 
              className="w-24 h-24 mx-auto mb-2" 
              fill="none" 
              stroke="currentColor" 
              viewBox="0 0 24 24"
            >
              <path 
                strokeLinecap="round" 
                strokeLinejoin="round" 
                strokeWidth={1} 
                d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" 
              />
            </svg>
            <p className="text-sm">No image available</p>
          </div>
        </div>
      </div>

      <div className="p-4">
        <div className="flex items-start justify-between mb-2">
          <h3 className="text-lg font-semibold text-gray-900">{product.name}</h3>
          <span className="text-xs bg-green-100 text-green-800 px-2 py-1 rounded">
            {product.type}
          </span>
        </div>

        <p className="text-sm text-gray-600 mb-1">{product.brand}</p>
        <p className="text-sm text-gray-500 mb-3 line-clamp-2">{product.info}</p>

        <div className="flex items-center justify-between mb-3">
          <span className="text-2xl font-bold text-gray-900">
            ${product.price.toFixed(2)}
          </span>
          <span className="text-sm text-gray-500">
            Existencias: {product.stock}
          </span>
        </div>

        <div className="flex gap-2">
          <input
            type="number"
            min="1"
            max={product.stock}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center"
            disabled={product.stock === 0}
          />
          <button
            onClick={handleAdd}
            disabled={product.stock === 0 || adding}
            className="flex-1 bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
          >
            {adding ? 'Adding...' : product.stock === 0 ? 'No hay :(' : 'Pal carrito'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default ProductCard;