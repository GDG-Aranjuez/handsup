package es.gdgaranjuez.handsup.android

import android.app.Application
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

class HandsupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Options come from build properties (see androidApp/build.gradle.kts), so no google-services.json is needed.
        Firebase.initialize(
            this,
            FirebaseOptions(
                applicationId = BuildConfig.FIREBASE_APP_ID,
                apiKey = BuildConfig.FIREBASE_API_KEY,
                projectId = BuildConfig.FIREBASE_PROJECT_ID,
            ),
        )
    }
}
