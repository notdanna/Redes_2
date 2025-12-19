import SwiftUI

struct LoginView: View {
    @StateObject private var webSocketService = ChatWebSocketService()
    @State private var username = ""
    @State private var isLoading = false
    
    var body: some View {
        Group {
            if webSocketService.isConnected {
                ChatView(webSocketService: webSocketService)
            } else {
                loginScreen
            }
        }
        .onChange(of: webSocketService.isConnected) { _, connected in
            if connected {
                isLoading = false
            }
        }
    }
    
    private var loginScreen: some View {
        VStack(spacing: 40) {
            Spacer()
            
            // Icon and title
            VStack(spacing: 20) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 72))
                    .foregroundColor(.accentColor)
                    .symbolRenderingMode(.hierarchical)
                
                Text("Chat en Tiempo Real")
                    .font(.largeTitle)
                    .fontWeight(.bold)
            }
            
            // Username input
            VStack(spacing: 16) {
                TextField("Tu nombre de usuario", text: $username)
                    .textFieldStyle(.plain)
                    .font(.title3)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: 320)
                    .padding(.vertical, 12)
                    .padding(.horizontal, 16)
                    .background(Color(NSColor.textBackgroundColor))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .disabled(isLoading)
                    .onSubmit {
                        handleLogin()
                    }
                
                Button(action: handleLogin) {
                    if isLoading {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Text("Entrar")
                            .frame(maxWidth: 320)
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(username.isEmpty || isLoading)
            }
            
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
    
    private func handleLogin() {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        
        isLoading = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            webSocketService.connect(username: username.trimmingCharacters(in: .whitespaces))
        }
    }
}

#Preview {
    LoginView()
}
