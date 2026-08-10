package com.safa.account

import android.app.Application
import com.safa.account.data.sync.SyncWorkScheduler

class SafaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorkScheduler.schedule(this)
    }
}
