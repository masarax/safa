package com.safa.account

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.safa.account.data.sync.SyncWorkScheduler
import com.safa.account.telemetry.MobileTelemetryReporter
import com.safa.account.ui.security.SensitiveWindowPolicy

class SafaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileTelemetryReporter.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                SensitiveWindowPolicy.apply(activity.window)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        SyncWorkScheduler.schedule(this)
    }
}
