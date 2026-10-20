package es.gdgaranjuez.handsup

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.preferredLanguages
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

private class IosServices : PlatformServices {
    override fun systemLanguage(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "es"

    override fun share(text: String) {
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    override fun openUrl(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
    }

    // Google sign-in on iOS needs the GoogleSignIn SDK; email/password works everywhere (docs/mobile.md).
    override suspend fun googleIdToken(webClientId: String): String? = null

    private fun topViewController(): UIViewController? {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

/** Entry point called from Swift (iosApp/iosApp/ContentView.swift). */
fun MainViewController(
    useEmulator: Boolean,
    emulatorHost: String,
    defaultEventId: String,
    webBaseUrl: String,
): UIViewController = ComposeUIViewController {
    HandsupApp(
        AppConfig(
            useEmulator = useEmulator,
            emulatorHost = emulatorHost,
            defaultEventId = defaultEventId,
            webBaseUrl = webBaseUrl,
            googleWebClientId = null,
        ),
        IosServices(),
    )
}
