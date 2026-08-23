package com.pokerlanka.pokie_talkie

import android.app.Application
import com.pokerlanka.pokie_talkie.di.AppContainer

class PokieApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.initialize()
    }
}
