package com.yang.jizhang

import android.app.Application
import com.yang.jizhang.data.AppDatabase

/** 全局 Application：持有数据库单例 */
class JizhangApp : Application() {
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
    }
}
