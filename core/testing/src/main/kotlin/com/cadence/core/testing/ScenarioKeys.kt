package com.cadence.core.testing

/** Script keys understood by [FakeSourceProvider] (spike spec section 10). */
object ScenarioKeys {
    const val OK = "ok/"
    const val UNAVAILABLE = "unavailable/"
    const val RATE_LIMITED = "rate-limited/"
    const val AUTH_FAILED = "auth-failed/"
    const val MALFORMED = "malformed/"
    const val CANCELLED_IMMEDIATELY = "cancelled-immediately/"
    const val DELAYED = "delayed/"

    /** `delayed/<millis>-<name>` -> millis part. */
    fun delayMillis(nativeId: String): Long? =
        nativeId.removePrefix(DELAYED).substringBefore('-').toLongOrNull()
}
