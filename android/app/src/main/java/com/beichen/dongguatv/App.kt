package com.beichen.dongguatv

import android.app.Application

/**
 * 全局 Application 入口。
 * 阶段2 接入 Retrofit ApiClient、EncryptedSharedPreferences、WorkManager 注册。
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @JvmStatic
        lateinit var instance: App
            private set
    }
}
