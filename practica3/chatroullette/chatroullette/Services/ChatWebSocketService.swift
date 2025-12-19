import Foundation
import Combine

// MARK: - Message Models
struct ChatMessage: Codable, Identifiable {
    let id = UUID()
    let type: String
    let room: String?
    let from: String?
    let to: String?
    let content: String?
    let audioName: String?
    let audioType: String?
    let audioData: String?
    
    enum CodingKeys: String, CodingKey {
        case type, room, from, to, content, audioName, audioType, audioData
    }
}

struct OutgoingMessage: Codable {
    let type: String
    let username: String?
    let room: String?
    let content: String?
    let to: String?
    let audioName: String?
    let audioType: String?
    let audioData: String?
}

struct RoomsMessage: Codable {
    let type: String
    let rooms: [String]
}

struct UsersMessage: Codable {
    let type: String
    let room: String
    let users: [String]
}

struct AudioStartMessage: Codable {
    let type: String
    let transferId: String
    let room: String
    let from: String
    let audioName: String
    let audioType: String
    let totalChunks: Int
}

struct AudioChunkMessage: Codable {
    let type: String
    let transferId: String
    let seq: Int
    let total: Int
    let data: String
    let isLast: Bool
}

struct AudioCompleteMessage: Codable {
    let type: String
    let transferId: String
    let room: String
}

struct AudioAckMessage: Codable {
    let type: String
    let transferId: String
    let seq: Int
}

// MARK: - Audio Transfer State
class AudioTransferState {
    let transferId: String
    let room: String
    let from: String
    let audioName: String
    let audioType: String
    let totalChunks: Int
    var chunks: [Int: String] = [:]
    var receivedChunks: Int = 0
    let startTime: Date
    
    init(transferId: String, room: String, from: String, audioName: String, audioType: String, totalChunks: Int) {
        self.transferId = transferId
        self.room = room
        self.from = from
        self.audioName = audioName
        self.audioType = audioType
        self.totalChunks = totalChunks
        self.startTime = Date()
    }
}

// MARK: - WebSocket Service
class ChatWebSocketService: NSObject, ObservableObject {
    @Published var isConnected = false
    @Published var rooms: [String] = []
    @Published var messages: [String: [ChatMessage]] = [:]
    @Published var users: [String: [String]] = [:]
    @Published var currentUsername: String?
    
    private var webSocketTask: URLSessionWebSocketTask?
    private let serverURL = URL(string: "ws://localhost:8080/ws/chat")!
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    
    // Audio transfers
    private var activeTransfers: [String: AudioTransferState] = [:]
    
    // MARK: - Connection
    func connect(username: String) {
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: OperationQueue())
        webSocketTask = session.webSocketTask(with: serverURL)
        webSocketTask?.resume()
        
        currentUsername = username
        receiveMessage()
        
        // Send login after connection
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.sendLogin(username: username)
        }
    }
    
    func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        isConnected = false
        currentUsername = nil
        rooms = []
        messages = [:]
        users = [:]
        activeTransfers = [:]
    }
    
    // MARK: - Send Messages
    private func send(_ message: OutgoingMessage) {
        guard let data = try? encoder.encode(message),
              let json = String(data: data, encoding: .utf8) else {
            print("❌ Error encoding message")
            return
        }
        
        let message = URLSessionWebSocketTask.Message.string(json)
        webSocketTask?.send(message) { error in
            if let error = error {
                print("❌ WebSocket send error: \(error)")
            }
        }
    }
    
    func sendLogin(username: String) {
        let msg = OutgoingMessage(type: "login", username: username, room: nil, content: nil, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Login enviado: \(username)")
    }
    
    func createRoom(name: String) {
        let msg = OutgoingMessage(type: "create_room", username: nil, room: name, content: nil, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Crear sala: \(name)")
    }
    
    func joinRoom(name: String) {
        let msg = OutgoingMessage(type: "join_room", username: nil, room: name, content: nil, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Unirse a sala: \(name)")
    }
    
    func leaveRoom(name: String) {
        let msg = OutgoingMessage(type: "leave_room", username: nil, room: name, content: nil, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Salir de sala: \(name)")
    }
    
    func sendMessage(room: String, content: String) {
        let msg = OutgoingMessage(type: "message", username: nil, room: room, content: content, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Mensaje enviado a \(room)")
    }
    
    func sendPrivateMessage(room: String, to: String, content: String) {
        let msg = OutgoingMessage(type: "private_message", username: nil, room: room, content: content, to: to, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Mensaje privado a \(to)")
    }
    
    func sendSticker(room: String, sticker: String) {
        let msg = OutgoingMessage(type: "sticker", username: nil, room: room, content: sticker, to: nil, audioName: nil, audioType: nil, audioData: nil)
        send(msg)
        print("📤 Sticker enviado: \(sticker)")
    }
    
    func sendAudio(room: String, audioName: String, audioType: String, audioData: String) {
        let msg = OutgoingMessage(type: "audio", username: nil, room: room, content: nil, to: nil, audioName: audioName, audioType: audioType, audioData: audioData)
        send(msg)
        print("📤 Audio enviado: \(audioName)")
        
        // Add local message
        appendMessage(room: room, message: ChatMessage(
            type: "audio",
            room: room,
            from: currentUsername,
            to: nil,
            content: audioName,
            audioName: audioName,
            audioType: audioType,
            audioData: audioData
        ))
    }
    
    func sendAudioAck(transferId: String, seq: Int) {
        let ack = AudioAckMessage(type: "audio_ack", transferId: transferId, seq: seq)
        guard let data = try? encoder.encode(ack),
              let json = String(data: data, encoding: .utf8) else { return }
        
        let message = URLSessionWebSocketTask.Message.string(json)
        webSocketTask?.send(message) { _ in }
    }
    
    // MARK: - Receive Messages
    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.receiveMessage()
                
            case .failure(let error):
                print("❌ WebSocket receive error: \(error)")
                DispatchQueue.main.async {
                    self.isConnected = false
                }
            }
        }
    }
    
    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }
        
        do {
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let type = json?["type"] as? String ?? ""
            
            print("📥 Mensaje recibido: \(type)")
            
            switch type {
            case "login_ok":
                DispatchQueue.main.async {
                    self.isConnected = true
                    print("✅ Login exitoso")
                }
                
            case "rooms":
                let msg = try decoder.decode(RoomsMessage.self, from: data)
                DispatchQueue.main.async {
                    self.rooms = msg.rooms
                    print("📋 Salas actualizadas: \(msg.rooms)")
                }
                
            case "users":
                let msg = try decoder.decode(UsersMessage.self, from: data)
                DispatchQueue.main.async {
                    self.users[msg.room] = msg.users
                    print("👥 Usuarios en \(msg.room): \(msg.users)")
                }
                
            case "system", "message", "private_message", "sticker":
                let msg = try decoder.decode(ChatMessage.self, from: data)
                if let room = msg.room {
                    appendMessage(room: room, message: msg)
                }
                
            case "audio_start":
                let msg = try decoder.decode(AudioStartMessage.self, from: data)
                handleAudioStart(msg)
                
            case "audio_chunk":
                let msg = try decoder.decode(AudioChunkMessage.self, from: data)
                handleAudioChunk(msg)
                
            case "audio_complete":
                let msg = try decoder.decode(AudioCompleteMessage.self, from: data)
                handleAudioComplete(msg)
                
            case "error":
                if let content = json?["content"] as? String {
                    print("❌ Error del servidor: \(content)")
                }
                
            default:
                print("⚠️ Tipo de mensaje desconocido: \(type)")
            }
            
        } catch {
            print("❌ Error parsing message: \(error)")
        }
    }
    
    // MARK: - Audio Transfer Handling
    private func handleAudioStart(_ msg: AudioStartMessage) {
        // Skip if it's our own audio
        if msg.from == currentUsername {
            return
        }
        
        print("📥 Iniciando recepción de audio: \(msg.audioName) (\(msg.totalChunks) chunks)")
        
        let transfer = AudioTransferState(
            transferId: msg.transferId,
            room: msg.room,
            from: msg.from,
            audioName: msg.audioName,
            audioType: msg.audioType,
            totalChunks: msg.totalChunks
        )
        
        activeTransfers[msg.transferId] = transfer
        
        appendMessage(room: msg.room, message: ChatMessage(
            type: "system",
            room: msg.room,
            from: nil,
            to: nil,
            content: "📥 \(msg.from) está enviando \"\(msg.audioName)\" (\(msg.totalChunks) paquetes)...",
            audioName: nil,
            audioType: nil,
            audioData: nil
        ))
    }
    
    private func handleAudioChunk(_ msg: AudioChunkMessage) {
        guard let transfer = activeTransfers[msg.transferId] else {
            print("⚠️ Transfer no encontrado: \(msg.transferId)")
            return
        }
        
        // Store chunk if we don't have it
        if transfer.chunks[msg.seq] == nil {
            transfer.chunks[msg.seq] = msg.data
            transfer.receivedChunks += 1
            
            let progress = (Double(transfer.receivedChunks) / Double(transfer.totalChunks) * 100)
            if msg.seq % 10 == 0 {
                print("📦 Chunk \(msg.seq + 1)/\(msg.total) recibido (\(String(format: "%.1f", progress))%)")
            }
        }
        
        // Send ACK
        sendAudioAck(transferId: msg.transferId, seq: msg.seq)
        
        // If we have all chunks, reconstruct
        if transfer.receivedChunks == transfer.totalChunks {
            print("✅ Todos los chunks recibidos, reconstruyendo...")
            reconstructAudio(transfer: transfer)
        }
    }
    
    private func handleAudioComplete(_ msg: AudioCompleteMessage) {
        guard let transfer = activeTransfers[msg.transferId] else { return }
        
        print("🏁 Servidor notificó transferencia completa")
        
        if transfer.receivedChunks == transfer.totalChunks {
            reconstructAudio(transfer: transfer)
        } else {
            print("⚠️ Faltan chunks: \(transfer.totalChunks - transfer.receivedChunks)")
        }
    }
    
    private func reconstructAudio(transfer: AudioTransferState) {
        // Check for missing chunks
        var missingChunks: [Int] = []
        for i in 0..<transfer.totalChunks {
            if transfer.chunks[i] == nil {
                missingChunks.append(i)
            }
        }
        
        if !missingChunks.isEmpty {
            print("❌ Faltan chunks: \(missingChunks)")
            appendMessage(room: transfer.room, message: ChatMessage(
                type: "system",
                room: transfer.room,
                from: nil,
                to: nil,
                content: "❌ Error: faltan \(missingChunks.count) paquetes del audio \"\(transfer.audioName)\"",
                audioName: nil,
                audioType: nil,
                audioData: nil
            ))
            activeTransfers.removeValue(forKey: transfer.transferId)
            return
        }
        
        // Reconstruct audio by concatenating binary data
        var completeData = Data()
        for i in 0..<transfer.totalChunks {
            if let chunkBase64 = transfer.chunks[i],
               let chunkData = Data(base64Encoded: chunkBase64) {
                completeData.append(chunkData)
            }
        }
        
        // Convert back to base64
        let base64Complete = completeData.base64EncodedString()
        
        let elapsed = Date().timeIntervalSince(transfer.startTime)
        let sizeKB = Double(completeData.count) / 1024.0
        
        print("✅ Audio reconstruido exitosamente:")
        print("  - Nombre: \(transfer.audioName)")
        print("  - Tamaño: \(String(format: "%.2f", sizeKB)) KB")
        print("  - Tiempo: \(String(format: "%.0f", elapsed * 1000))ms")
        print("  - Chunks: \(transfer.totalChunks)")
        
        appendMessage(room: transfer.room, message: ChatMessage(
            type: "audio",
            room: transfer.room,
            from: transfer.from,
            to: nil,
            content: transfer.audioName,
            audioName: transfer.audioName,
            audioType: transfer.audioType,
            audioData: base64Complete
        ))
        
        appendMessage(room: transfer.room, message: ChatMessage(
            type: "system",
            room: transfer.room,
            from: nil,
            to: nil,
            content: "✅ Audio \"\(transfer.audioName)\" recibido correctamente (\(String(format: "%.2f", sizeKB)) KB en \(String(format: "%.0f", elapsed * 1000))ms)",
            audioName: nil,
            audioType: nil,
            audioData: nil
        ))
        
        activeTransfers.removeValue(forKey: transfer.transferId)
    }
    
    // MARK: - Helper Methods
    private func appendMessage(room: String, message: ChatMessage) {
        DispatchQueue.main.async {
            if self.messages[room] == nil {
                self.messages[room] = []
            }
            self.messages[room]?.append(message)
        }
    }
}

// MARK: - URLSessionWebSocketDelegate
extension ChatWebSocketService: URLSessionWebSocketDelegate {
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        print("✅ WebSocket conectado")
        DispatchQueue.main.async {
            self.isConnected = true
        }
    }
    
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        print("❌ WebSocket desconectado")
        DispatchQueue.main.async {
            self.isConnected = false
        }
    }
}
