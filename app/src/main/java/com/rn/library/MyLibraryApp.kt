package com.rn.library

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.rn.library.ui.LanguageState
import com.rn.library.update.UpdateNotifier

class MyLibraryApp : Application() {
    companion object {
        var instance: MyLibraryApp? = null
            private set
        var currentActivity: Activity? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LanguageState.applyStoredLanguage(this)
        UpdateNotifier.ensureChannel(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
        })
    }
}
