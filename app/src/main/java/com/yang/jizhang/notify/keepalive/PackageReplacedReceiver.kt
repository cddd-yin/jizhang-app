package com.yang.jizhang.notify.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 覆盖安装（升级）后系统会撤销通知使用权，需提示用户重新开启。
 * 收到 MY_PACKAGE_REPLACED 后标记，App 下次打开时引导。
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageReplaced"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        ListenerStatus.markNeedsRegrantAfterUpdate(context.applicationContext)
    }
}
