import SwiftUI
import FirebaseCore
import ComposeApp

/// Build-time configuration lives in Config.xcconfig and reaches the app through Info.plist.
enum Config {
    private static func string(_ key: String) -> String {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? ""
    }
    static var useEmulator: Bool { string("HandsupUseEmulator") == "YES" }
    static var emulatorHost: String { string("HandsupEmulatorHost") }
    static var defaultEvent: String { string("HandsupDefaultEvent") }
    static var webBaseUrl: String { string("HandsupWebBaseUrl") }
    static var firebaseAppId: String { string("HandsupFirebaseAppId") }
    static var firebaseApiKey: String { string("HandsupFirebaseApiKey") }
    static var firebaseProjectId: String { string("HandsupFirebaseProjectId") }
    static var firebaseSenderId: String { string("HandsupFirebaseSenderId") }
}

@main
struct iOSApp: App {
    init() {
        // Options come from build settings, so no GoogleService-Info.plist is needed.
        let options = FirebaseOptions(googleAppID: Config.firebaseAppId, gcmSenderID: Config.firebaseSenderId)
        options.apiKey = Config.firebaseApiKey
        options.projectID = Config.firebaseProjectId
        FirebaseApp.configure(options: options)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.keyboard)
        }
    }
}
