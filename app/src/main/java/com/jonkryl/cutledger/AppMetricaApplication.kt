package com.jonkryl.cutledger

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

/** Release installation/session analytics. Project contents stay on the device. */
class AppMetricaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds have no key and never activate portfolio telemetry.
        if (BuildConfig.APPMETRICA_API_KEY.isBlank()) return

        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
            .withLocationTracking(false)
            .withAdvIdentifiersTracking(false)
            .withCrashReporting(false)
            .withNativeCrashReporting(false)
            .withAnrMonitoring(false)
            .withRevenueAutoTrackingEnabled(false)
            .withSessionsAutoTrackingEnabled(true)
            .handleFirstActivationAsUpdate(packageInfo.lastUpdateTime > packageInfo.firstInstallTime)
            .build()
        AppMetrica.activate(this, config)
    }
}
