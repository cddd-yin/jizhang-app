package com.yang.jizhang.engine.payment

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yang.jizhang.engine.AutoBookEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Suppress("DEPRECATION") // Older Android releases pool AccessibilityNodeInfo objects.
class ScreenPaymentService : AccessibilityService() {
    private data class Window(val source: PaymentSource, val id: Int)

    private val handler = Handler(Looper.getMainLooper())
    private val gate = VisiblePaymentGate()
    private var pending: Window? = null
    private val scan = Runnable {
        val window = pending ?: return@Runnable
        if (inspect(window)) pending = null
        else handler.postDelayed({
            if (pending == window) {
                pending = null
                inspect(window)
            }
        }, 150L)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenPaymentMonitor.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!ScreenPaymentMonitor.isEnabled(this)) return
        val source = PaymentSource.fromPackage(event.packageName) ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val window = Window(source, event.windowId)
        if (pending == window) return
        cancelScan()
        pending = window
        handler.postDelayed(scan, 150L)
    }

    private fun inspect(window: Window): Boolean {
        val app = applicationContext
        if (!ScreenPaymentMonitor.isEnabled(app)) return true
        val result = try {
            val root = rootInActiveWindow ?: return false
            try {
                if (root.windowId != window.id || PaymentSource.fromPackage(root.packageName) != window.source) return false
                val reader = WindowReader(window.source)
                if (reader.visit(root, depth = 0, insideAction = false)) reader.page.result() else null
            } finally {
                root.recycle()
            }
        } catch (_: RuntimeException) {
            return false
        }

        if (!gate.accept(window.id, result) || result == null) return true
        val capturedAt = System.currentTimeMillis()
        writeScope.launch {
            try {
                AutoBookEngine.queueScreenPayment(app, result, capturedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w(TAG, "Could not queue payment result")
            }
        }
        return true
    }

    private fun cancelScan() {
        handler.removeCallbacksAndMessages(null)
        pending = null
    }

    override fun onInterrupt() = cancelScan()

    override fun onUnbind(intent: Intent?): Boolean {
        cancelScan()
        ScreenPaymentMonitor.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelScan()
        ScreenPaymentMonitor.setConnected(false)
        super.onDestroy()
    }

    private class WindowReader(private val source: PaymentSource) {
        val page = PaymentPage(source)
        private var remaining = 256
        private val deadline = SystemClock.uptimeMillis() + 110L

        fun visit(node: AccessibilityNodeInfo, depth: Int, insideAction: Boolean): Boolean {
            if (remaining-- <= 0 || depth > 22 || SystemClock.uptimeMillis() > deadline) return false
            if (!node.isVisibleToUser || PaymentSource.fromPackage(node.packageName) != source) return true
            if (node.isPassword || node.isEditable) return false

            val actionable = insideAction || (node.isClickable && node.isEnabled)
            val text = node.text
            page.observe(text, actionable)
            val description = node.contentDescription
            if (description != null && !description.contentEquals(text)) page.observe(description, actionable)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    if (!visit(child, depth + 1, actionable)) return false
                } finally {
                    child.recycle()
                }
            }
            return true
        }
    }

    private companion object {
        const val TAG = "ScreenPayment"
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
