package io.github.eelnicoo.pocketcode

import android.app.Application

class PocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PocketApp
            private set
    }
}
