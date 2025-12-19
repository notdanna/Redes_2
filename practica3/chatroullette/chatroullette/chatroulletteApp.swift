//
//  chatroulletteApp.swift
//  chatroullette
//
//  Created by dam on 19/11/25.
// test

import SwiftUI

@main
struct ChatAppSwiftUI: App {
    var body: some Scene {
        WindowGroup {
            LoginView()
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unified)
    }
}
