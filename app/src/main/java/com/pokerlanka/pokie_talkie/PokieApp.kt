package com.pokerlanka.pokie_talkie

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pokerlanka.pokie_talkie.di.AppContainer

class PokieApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    companion object {
        var isAppInForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                val becameForeground = !isAppInForeground && startedActivities > 0
                isAppInForeground = startedActivities > 0
                if (becameForeground && ::appContainer.isInitialized) {
                    appContainer.onAppForegroundChanged(true)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                isAppInForeground = startedActivities > 0
                if (!isAppInForeground && ::appContainer.isInitialized) {
                    appContainer.onAppForegroundChanged(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        appContainer = AppContainer(this)
        appContainer.initialize()
    }
}
