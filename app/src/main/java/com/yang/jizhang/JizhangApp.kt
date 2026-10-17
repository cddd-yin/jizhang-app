package com.yang.jizhang

import android.app.Application
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.notify.keepalive.KeepAliveService
import com.yang.jizhang.notify.keepalive.ListenerStatus
import com.yang.jizhang.notify.keepalive.ListenerWatchdog
import com.yang.jizhang.notify.keepalive.NotificationChannels

/** 全局 Application：持有数据库单例 + 启动时拉起支付监听 */
class JizhangApp : Application() {
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        // 升级后系统会撤销通知使用权，标记需重新开启
        ListenerStatus.checkAppUpdated(this)
        NotificationChannels.ensure(this)
        ListenerWatchdog.schedule(this)
        if (ListenerStatus.isEnabledInSettings(this)) {
            KeepAliveService.start(this)
            ListenerStatus.ensureListening(this)
        }
    }
}
