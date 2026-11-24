package com.creativeworkbench.writer

import android.app.Application
import com.creativeworkbench.writer.core.AppContainer

class WorkbenchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
