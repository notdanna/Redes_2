// src/components/Cart.jsx
function Cart({ cart, onClose, onUpdateCart, onCheckout }) {
  const total = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-hidden flex flex-col">
        <div className="p-6 border-b flex items-center justify-between">
          <h2 className="text-2xl font-bold">Carro de compra</h2>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-700"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {cart.length === 0 ? (
            <p className="text-center text-gray-500 py-8">Your cart is empty</p>
          ) : (
            <div className="space-y-4">
              {cart.map(item => (
                <div key={item.id} className="flex gap-4 p-4 border rounded-lg">
                  <div className="w-20 h-20 bg-gray-100 rounded flex-shrink-0 overflow-hidden">
                      <img 
                        src={`http://localhost:8080${item.imageUrl}`}
                        alt={item.name}
                        className="w-full h-full object-cover"
                      />
                    </div>

                  <div className="flex-1">
                    <h3 className="font-semibold text-gray-900">{item.name}</h3>
                    <p className="text-sm text-gray-600">{item.brand}</p>
                    <p className="text-sm font-medium text-gray-900 mt-1">
                      ${item.price.toFixed(2)} cada una
                    </p>
                  </div>

                  <div className="flex flex-col items-end gap-2">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => onUpdateCart(item.id, item.quantity - 1)}
                        className="w-8 h-8 rounded bg-gray-200 hover:bg-gray-300"
                      >
                        -
                      </button>
                      <span className="w-12 text-center font-medium">{item.quantity}</span>
                      <button
                        onClick={() => onUpdateCart(item.id, item.quantity + 1)}
                        className="w-8 h-8 rounded bg-gray-200 hover:bg-gray-300"
                        disabled={item.quantity >= item.stock}
                      >
                        +
                      </button>
                    </div>
                    <p className="font-semibold text-gray-900">
                      ${(item.price * item.quantity).toFixed(2)}
                    </p>
                    <button
                      onClick={() => onUpdateCart(item.id, 0)}
                      className="text-sm text-red-600 hover:text-red-700"
                    >
                      Quitar
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {cart.length > 0 && (
          <div className="p-6 border-t bg-gray-50">
            <div className="flex justify-between items-center mb-4">
              <span className="text-lg font-semibold">Total</span>
              <span className="text-2xl font-bold text-green-600">
                ${total.toFixed(2)}
              </span>
            </div>
            <button
              onClick={onCheckout}
              className="w-full bg-green-600 text-white py-3 rounded-lg hover:bg-green-700 transition-colors font-semibold"
            >
              Hacer pedido
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default Cart;