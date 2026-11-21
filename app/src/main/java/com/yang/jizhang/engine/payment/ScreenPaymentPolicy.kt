package com.yang.jizhang.engine.payment

internal object ScreenPaymentPolicy {
    const val EVENT_PREFIX = "SCREENPAY|"
    const val REFERENCE_PREFIX = "${EVENT_PREFIX}REF|"
    const val CONFIRMED_PREFIX = "REVIEW_CONFIRMED|"
    const val REVIEW_WINDOW_MS = 120_000L
    const val POSSIBLE_DUPLICATE_REASON = "可能与支付页识别重复"

    fun isScreenEvent(eventKey: String): Boolean = eventKey.startsWith(EVENT_PREFIX)

    fun isConfirmedScreen(dedupeKey: String): Boolean = dedupeKey.startsWith(CONFIRMED_PREFIX + EVENT_PREFIX)

    fun requiresDuplicateReview(eventKey: String, savedKey: String): Boolean {
        if (!isScreenEvent(eventKey) && !isConfirmedScreen(savedKey)) return false
        // Two explicit, different transaction references identify two distinct payments.
        return !eventKey.startsWith(REFERENCE_PREFIX) ||
            !savedKey.startsWith(CONFIRMED_PREFIX + REFERENCE_PREFIX) ||
            savedKey == CONFIRMED_PREFIX + eventKey
    }
}

/** Suppresses re-rendering of a receipt while its window stays visible. */
internal class VisiblePaymentGate {
    private var lastWindow: Int? = null
    private var lastPayment: DetectedPayment? = null

    fun accept(windowId: Int, payment: DetectedPayment?): Boolean {
        val previous = lastPayment
        val sameWindow = lastWindow == windowId
        lastWindow = windowId
        if (payment == null) {
            lastPayment = null
            return false
        }

        if (sameWindow && previous != null && previous.source == payment.source && previous.cents == payment.cents) {
            val oldReference = previous.referenceDigest
            val newReference = payment.referenceDigest
            if (oldReference == null || newReference == null || oldReference == newReference) {
                if (newReference != null) lastPayment = payment
                return false
            }
        }
        lastPayment = payment
        return true
    }
}
