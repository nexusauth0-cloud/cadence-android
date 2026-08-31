package com.cadence.core.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic, manually-advanced clock for tests (spike spec section 10).
 * Production mappers receive Clock; tests drive this one — no wall-time reads.
 */
class MutableClock private constructor(
    private var epochMillis: Long,
) : Clock() {

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this // deliberately fixed to UTC

    override fun instant(): Instant = Instant.ofEpochMilli(epochMillis)

    fun advanceMillis(millis: Long) {
        epochMillis += millis
    }

    fun set(instant: Instant) {
        epochMillis = instant.toEpochMilli()
    }

    companion object {
        fun at(isoInstant: String): MutableClock =
            MutableClock(Instant.parse(isoInstant).toEpochMilli())

        fun at(dateTime: ZonedDateTime): MutableClock = MutableClock(dateTime.toInstant().toEpochMilli())
    }
}
