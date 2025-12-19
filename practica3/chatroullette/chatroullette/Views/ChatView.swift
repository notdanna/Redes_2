import SwiftUI
import UniformTypeIdentifiers
import AVFoundation
import Combine

struct ChatView: View {
    @ObservedObject var webSocketService: ChatWebSocketService
    @State private var selectedConversation: String?
    @State private var messageText = ""
    @State private var showNewConversation = false
    @State private var newConversationName = ""
    @State private var showAudioPicker = false
    @State private var selectedUser: String?
    
    var conversations: [String] {
        webSocketService.rooms
    }
    
    var currentMessages: [ChatMessage] {
        if let conv = selectedConversation {
            return webSocketService.messages[conv] ?? []
        }
        return []
    }
    
    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 0) {
                // Left sidebar - Conversations list (responsive)
                if geometry.size.width > 500 {
                    conversationsSidebar
                        .frame(width: min(260, geometry.size.width * 0.3))
                    
                    Divider()
                }
                
                // Main chat area
                if let conversation = selectedConversation {
                    chatArea(for: conversation, isCompact: geometry.size.width <= 500)
                } else {
                    emptyState
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigation) {
                HStack(spacing: 8) {
                    // Menu button for compact mode
                    if let conversation = selectedConversation {
                        Menu {
                            ForEach(conversations, id: \.self) { conv in
                                Button(conv) {
                                    selectConversation(conv)
                                }
                            }
                            
                            Divider()
                            
                            Button("Nueva conversación...") {
                                showNewConversation = true
                            }
                        } label: {
                            Image(systemName: "line.3.horizontal")
                        }
                        .menuStyle(.borderlessButton)
                    }
                    
                    if let username = webSocketService.currentUsername {
                        Circle()
                            .fill(.green)
                            .frame(width: 8, height: 8)
                        
                        Text(username)
                            .font(.headline)
                    }
                }
            }
            
            ToolbarItem(placement: .primaryAction) {
                Button {
                    // Logout
                    webSocketService.disconnect()
                } label: {
                    Label("Desconectar", systemImage: "power")
                }
            }
        }
    }
    
    // MARK: - Conversations Sidebar
    private var conversationsSidebar: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Conversaciones")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Spacer()
                
                Button {
                    showNewConversation = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                        .foregroundColor(.accentColor)
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showNewConversation) {
                    newConversationView
                }
            }
            .padding()
            
            Divider()
            
            // Conversations list
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(conversations, id: \.self) { conversation in
                        ConversationRow(
                            name: conversation,
                            isSelected: conversation == selectedConversation,
                            unreadCount: 0
                        ) {
                            selectConversation(conversation)
                        }
                    }
                    
                    if conversations.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "tray")
                                .font(.largeTitle)
                                .foregroundColor(.secondary)
                            
                            Text("Sin conversaciones")
                                .font(.headline)
                                .foregroundColor(.secondary)
                            
                            Text("Crea una nueva para comenzar")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }
        }
        .frame(width: 260)
        .background(Color(NSColor.controlBackgroundColor))
    }
    
    // MARK: - New Conversation View
    private var newConversationView: some View {
        VStack(spacing: 16) {
            Text("Nueva Conversación")
                .font(.headline)
            
            TextField("Nombre de la sala...", text: $newConversationName)
                .textFieldStyle(.roundedBorder)
                .frame(width: 250)
            
            HStack {
                Button("Cancelar") {
                    showNewConversation = false
                    newConversationName = ""
                }
                .keyboardShortcut(.escape)
                
                Button("Crear") {
                    createConversation()
                }
                .keyboardShortcut(.return)
                .disabled(newConversationName.isEmpty)
            }
        }
        .padding()
    }
    
    // MARK: - Chat Area
    private func chatArea(for conversation: String, isCompact: Bool) -> some View {
        VStack(spacing: 0) {
            // Chat header
            chatHeader(for: conversation, isCompact: isCompact)
            
            Divider()
            
            // Messages
            messagesView
            
            Divider()
            
            // Input area
            messageInputArea
        }
    }
    
    private func chatHeader(for conversation: String, isCompact: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(conversation)
                    .font(.title3)
                    .fontWeight(.semibold)
                
                if let users = webSocketService.users[conversation] {
                    Text("\(users.count) \(users.count == 1 ? "participante" : "participantes")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            // Participants menu
            if let users = webSocketService.users[conversation], !users.isEmpty {
                Menu {
                    Section("Participantes") {
                        ForEach(users, id: \.self) { user in
                            Button {
                                if user != webSocketService.currentUsername {
                                    selectedUser = (selectedUser == user) ? nil : user
                                }
                            } label: {
                                HStack {
                                    Text(user)
                                    if user == webSocketService.currentUsername {
                                        Text("(Tú)")
                                            .foregroundColor(.secondary)
                                    }
                                    if selectedUser == user {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                            .disabled(user == webSocketService.currentUsername)
                        }
                    }
                    
                    if selectedUser != nil {
                        Divider()
                        Button("Cancelar mensajes privados") {
                            selectedUser = nil
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "person.2")
                        if let users = webSocketService.users[conversation] {
                            Text("\(users.count)")
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.accentColor.opacity(0.1))
                    .clipShape(Capsule())
                }
                .menuStyle(.borderlessButton)
            }
            
            Button {
                leaveConversation()
            } label: {
                Label("Salir", systemImage: "arrow.right.square")
                    .foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding()
        .background(.ultraThinMaterial)
    }
    
    private var messagesView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 16) {
                    if currentMessages.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "bubble.left.and.bubble.right")
                                .font(.system(size: 48))
                                .foregroundColor(.secondary)
                            
                            Text("Sin mensajes aún")
                                .font(.title3)
                                .foregroundColor(.secondary)
                            
                            Text("Sé el primero en escribir")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxHeight: .infinity)
                        .padding()
                    } else {
                        ForEach(currentMessages) { message in
                            ModernMessageRow(
                                message: message,
                                currentUsername: webSocketService.currentUsername ?? ""
                            )
                            .id(message.id)
                        }
                    }
                }
                .padding()
            }
            .onChange(of: currentMessages.count) { _, _ in
                if let lastMessage = currentMessages.last {
                    withAnimation {
                        proxy.scrollTo(lastMessage.id, anchor: .bottom)
                    }
                }
            }
        }
    }
    
    private var messageInputArea: some View {
        VStack(spacing: 0) {
            if selectedUser != nil {
                HStack {
                    Image(systemName: "lock.fill")
                        .foregroundColor(.orange)
                        .font(.caption)
                    
                    Text("Mensaje privado para \(selectedUser!)")
                        .font(.caption)
                        .foregroundColor(.orange)
                    
                    Spacer()
                    
                    Button("Cancelar") {
                        selectedUser = nil
                    }
                    .buttonStyle(.link)
                    .font(.caption)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(Color.orange.opacity(0.1))
                
                Divider()
            }
            
            HStack(spacing: 12) {
                // Audio button
                Button {
                    showAudioPicker = true
                } label: {
                    Image(systemName: "waveform.circle.fill")
                        .font(.title2)
                        .foregroundColor(.accentColor)
                }
                .buttonStyle(.plain)
                .fileImporter(
                    isPresented: $showAudioPicker,
                    allowedContentTypes: [.audio],
                    allowsMultipleSelection: false
                ) { result in
                    handleAudioSelection(result)
                }
                
                // Text input
                TextField("Escribe un mensaje...", text: $messageText)
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(Color(NSColor.textBackgroundColor))
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .onSubmit {
                        sendMessage()
                    }
                
                // Send button
                Button {
                    sendMessage()
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title2)
                        .foregroundColor(messageText.isEmpty ? .secondary : .accentColor)
                }
                .buttonStyle(.plain)
                .disabled(messageText.isEmpty)
                .keyboardShortcut(.return, modifiers: [.command])
            }
            .padding()
            .background(.ultraThinMaterial)
        }
    }
    
    private var emptyState: some View {
        VStack(spacing: 20) {
            Image(systemName: "message")
                .font(.system(size: 64))
                .foregroundColor(.secondary)
            
            Text("Selecciona una conversación")
                .font(.title2)
                .fontWeight(.medium)
            
            Text("O crea una nueva para comenzar a chatear")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Actions
    private func selectConversation(_ conversation: String) {
        if selectedConversation != conversation {
            if let old = selectedConversation {
                webSocketService.leaveRoom(name: old)
            }
            selectedConversation = conversation
            webSocketService.joinRoom(name: conversation)
            selectedUser = nil
        }
    }
    
    private func createConversation() {
        let name = newConversationName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        
        webSocketService.createRoom(name: name)
        newConversationName = ""
        showNewConversation = false
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            selectConversation(name)
        }
    }
    
    private func leaveConversation() {
        if let conversation = selectedConversation {
            webSocketService.leaveRoom(name: conversation)
            selectedConversation = nil
            selectedUser = nil
        }
    }
    
    private func sendMessage() {
        guard !messageText.isEmpty, let conversation = selectedConversation else { return }
        
        let text = messageText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        
        if let target = selectedUser {
            webSocketService.sendPrivateMessage(room: conversation, to: target, content: text)
        } else {
            webSocketService.sendMessage(room: conversation, content: text)
        }
        
        messageText = ""
    }
    
    private func handleAudioSelection(_ result: Result<[URL], Error>) {
        guard let conversation = selectedConversation else { return }
        
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            
            do {
                let data = try Data(contentsOf: url)
                
                let maxSize = 30 * 1024 * 1024
                guard data.count <= maxSize else { return }
                
                let base64 = data.base64EncodedString()
                let fileName = url.lastPathComponent
                let mimeType = "audio/\(url.pathExtension)"
                
                webSocketService.sendAudio(
                    room: conversation,
                    audioName: fileName,
                    audioType: mimeType,
                    audioData: base64
                )
            } catch {
                print("Error: \(error)")
            }
            
        case .failure(let error):
            print("Error: \(error)")
        }
    }
}

// MARK: - Conversation Row
struct ConversationRow: View {
    let name: String
    let isSelected: Bool
    let unreadCount: Int
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Icon
                Circle()
                    .fill(isSelected ? Color.accentColor : Color.secondary)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "number")
                            .font(.caption)
                            .foregroundColor(.white)
                    )
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(name)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(1)
                    
                    if isSelected {
                        Text("Abierta")
                            .font(.caption2)
                            .foregroundColor(.accentColor)
                    }
                }
                
                Spacer()
                
                if unreadCount > 0 {
                    Text("\(unreadCount)")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.red)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(isSelected ? Color.accentColor.opacity(0.15) : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Modern Message Row
struct ModernMessageRow: View {
    let message: ChatMessage
    let currentUsername: String
    
    var isOwn: Bool {
        message.from == currentUsername
    }
    
    var isSystem: Bool {
        message.type == "system"
    }
    
    var body: some View {
        if isSystem {
            HStack {
                Spacer()
                HStack(spacing: 6) {
                    Image(systemName: "info.circle.fill")
                        .font(.caption)
                    Text(message.content ?? "")
                        .font(.caption)
                }
                .foregroundColor(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.secondary.opacity(0.1))
                .clipShape(Capsule())
                Spacer()
            }
        } else {
            HStack(alignment: .bottom, spacing: 12) {
                if isOwn { Spacer(minLength: 60) }
                
                VStack(alignment: .leading, spacing: 6) {
                    // Header
                    HStack(spacing: 6) {
                        Text(message.from ?? "Unknown")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.secondary)
                        
                        if message.type == "private_message" {
                            HStack(spacing: 4) {
                                Image(systemName: "lock.fill")
                                Text("→ \(message.to ?? "")")
                            }
                            .font(.caption2)
                            .foregroundColor(.orange)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.15))
                            .clipShape(Capsule())
                        }
                    }
                    
                    // Content
                    if message.type == "audio" {
                        ModernAudioView(message: message)
                    } else {
                        Text(message.content ?? "")
                            .font(.body)
                            .foregroundColor(isOwn ? .white : .primary)
                            .multilineTextAlignment(.leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(
                                isOwn ? Color.accentColor : Color.secondary.opacity(0.15)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
                
                if !isOwn { Spacer(minLength: 60) }
            }
        }
    }
}

// MARK: - Modern Audio View
struct ModernAudioView: View {
    let message: ChatMessage
    @StateObject private var audioPlayer = AudioPlayer()
    
    var body: some View {
        HStack(spacing: 12) {
            Button {
                togglePlayback()
            } label: {
                ZStack {
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 44, height: 44)
                    
                    Image(systemName: audioPlayer.isPlaying ? "pause.fill" : "play.fill")
                        .font(.body)
                        .foregroundColor(.white)
                }
            }
            .buttonStyle(.plain)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(message.audioName ?? "Audio")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                
                if audioPlayer.isPlaying {
                    Text(formatTime(audioPlayer.currentTime))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                } else {
                    Text(message.audioType ?? "audio")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            Image(systemName: "waveform")
                .foregroundColor(.secondary)
        }
        .padding(12)
        .background(Color.secondary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .frame(minWidth: 260)
        .onAppear {
            if let audioData = message.audioData,
               let data = Data(base64Encoded: audioData) {
                audioPlayer.loadAudio(data: data, type: message.audioType ?? "audio/mpeg")
            }
        }
        .onDisappear {
            audioPlayer.stop()
        }
    }
    
    private func togglePlayback() {
        if audioPlayer.isPlaying {
            audioPlayer.pause()
        } else {
            audioPlayer.play()
        }
    }
    
    private func formatTime(_ time: TimeInterval) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

// MARK: - Audio Player
class AudioPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isPlaying = false
    @Published var currentTime: TimeInterval = 0
    
    private var player: AVAudioPlayer?
    private var timer: Timer?
    
    func loadAudio(data: Data, type: String) {
        do {
            player = try AVAudioPlayer(data: data)
            player?.delegate = self
            player?.prepareToPlay()
            print("🎵 Audio cargado: \(String(format: "%.2f", Double(data.count) / 1024.0)) KB")
        } catch {
            print("❌ Error cargando audio: \(error)")
        }
    }
    
    func play() {
        guard let player = player else { return }
        player.play()
        isPlaying = true
        startTimer()
        print("▶️ Reproduciendo audio")
    }
    
    func pause() {
        player?.pause()
        isPlaying = false
        stopTimer()
        print("⏸️ Audio pausado")
    }
    
    func stop() {
        player?.stop()
        player?.currentTime = 0
        isPlaying = false
        currentTime = 0
        stopTimer()
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self, let player = self.player else { return }
            self.currentTime = player.currentTime
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        isPlaying = false
        currentTime = 0
        stopTimer()
        print("✅ Audio terminó de reproducirse")
    }
    
    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        isPlaying = false
        stopTimer()
        print("❌ Error reproduciendo audio: \(error?.localizedDescription ?? "unknown")")
    }
}

#Preview {
    LoginView()
}
