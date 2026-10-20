import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosEntryKt.MainViewController(
            useEmulator: Config.useEmulator,
            emulatorHost: Config.emulatorHost,
            defaultEventId: Config.defaultEvent,
            webBaseUrl: Config.webBaseUrl
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
