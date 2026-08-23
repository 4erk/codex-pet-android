package com.fourerk.codexpet.app

import android.app.Application

class CodexPetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
    }
}
