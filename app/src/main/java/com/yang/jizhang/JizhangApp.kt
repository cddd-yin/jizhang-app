package com.yang.jizhang

import android.app.Application
import com.yang.jizhang.data.AppDatabase
import com.yang.jizhang.engine.keepalive.GuardianService
import com.yang.jizhang.engine.keepalive.ListenerHealth
import com.yang.jizhang.engine.keepalive.WatchdogScheduler
import com.yang.jizhang.engine.keepalive.ChannelIds

/** 全局 Application：持有数据库单例 + 启动时拉起支付监听 */
class JizhangApp : Application() {
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        // 升级后系统会撤销通知使用权，标记需重新开启
        ListenerHealth.checkAppUpdated(this)
        ChannelIds.ensure(this)
        WatchdogScheduler.schedule(this)
        if (ListenerHealth.isEnabledInSettings(this)) {
            GuardianService.start(this)
            ListenerHealth.ensureListening(this)
        }
    }
}
