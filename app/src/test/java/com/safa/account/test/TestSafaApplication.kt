package com.safa.account.test

import android.app.Application

/** Lightweight application used by JVM/Robolectric tests.
 * Production startup schedules WorkManager; JVM tests must not initialize it.
 */
class TestSafaApplication : Application()
