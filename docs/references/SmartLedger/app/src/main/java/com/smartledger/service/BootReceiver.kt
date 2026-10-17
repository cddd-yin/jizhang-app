package com.smartledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartledger.util.ListenerWatchdog

/**
 * 开机广播（保留类名兼容旧安装）。实际逻辑与 [com.smartledger.util.ListenerWakeReceiver] 一致。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        ListenerWatchdog.tick(context.applicationContext)
    }
}
