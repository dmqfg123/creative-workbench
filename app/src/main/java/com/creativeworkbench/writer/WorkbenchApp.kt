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
        // 启动时的当天备份：推迟 1.5 秒等首帧画完；整个备份只构建一次，本机 + Download 共用。
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1500)
            runCatching { container.backup.autoBackupOnStart() }
        }
    }
}
