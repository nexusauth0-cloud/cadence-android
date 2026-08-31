package com.cadence.core.media

/**
 * Audio effect chain interface (ADR-0006 §2).
 *
 * Ordered processors (normalize → EQ → trim-silence) each individually
 * disableable. Audio offload auto-disables when any non-pass-through
 * processor is active.
 *
 * The real implementation wraps ExoPlayer's AudioProcessor chain;
 * tests use a no-op or recording fake.
 */
interface AudioEffectChain {
    /** Add an effect at the end of the chain. */
    fun addEffect(effect: AudioEffect)

    /** Remove an effect by identity. */
    fun removeEffect(effect: AudioEffect)

    /** Enable/disable a specific effect. */
    fun setEnabled(effect: AudioEffect, enabled: Boolean)

    /** Whether any non-pass-through processor is active (offload must be disabled). */
    fun hasActiveEffects(): Boolean

    /** All effects currently in the chain. */
    fun effects(): List<AudioEffect>
}

/** Individual audio effect. */
interface AudioEffect {
    val id: String
    val name: String
    var enabled: Boolean
}

/** Preset EQ effect. */
class EqualizerEffect(
    override val id: String = "eq",
    override val name: String = "Equalizer",
    override var enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
) : AudioEffect

enum class EqPreset { FLAT, ROCK, POP, JAZZ, CLASSICAL, BASS_BOOST, TREBLE_BOOST }

/** Loudness normalization effect (ADR-0006 §6). */
class LoudnessNormalizationEffect(
    override val id: String = "loudness",
    override val name: String = "Loudness Normalization",
    override var enabled: Boolean = false,
    val targetLufs: Double = -14.0,
) : AudioEffect
