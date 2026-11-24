package com.creativeworkbench.writer

import android.app.Application
import com.creativeworkbench.writer.core.AppContainer
import com.creativeworkbench.writer.core.common.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkbenchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashLogger.install(this)
        // 启动时补一次当天备份；失败不阻塞启动。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { container.backup.autoBackupIfNeeded() }
            runCatching { container.backup.autoDailyPublicBackupIfNeeded() }
        }
    }
}
