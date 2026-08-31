package com.cadence.core.model

/**
 * Stable identity of a source plugin. Persisted in databases and preferences;
 * must never be renamed once shipped (ADR-0002).
 */
@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderId must not be blank" }
        require(value.length <= 64) { "ProviderId too long" }
    }

    override fun toString(): String = value
}
