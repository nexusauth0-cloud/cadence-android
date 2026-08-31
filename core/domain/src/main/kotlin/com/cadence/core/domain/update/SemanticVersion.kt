package com.cadence.core.domain.update

import java.math.BigInteger

/**
 * Parseable, comparable semantic version (SemVer 2.0 core subset + prerelease).
 *
 * Rules honoured in comparison:
 *  - numeric precedence: 1.10.0 > 1.9.9 (no string/lexicographic comparison);
 *  - major dominates: 2.0.0 > 1.99.99;
 *  - a release outranks any pre-release of the same version: 1.2.0 > 1.2.0-beta;
 *  - pre-release comparison is by dot-separated identifier (numeric vs numeric,
 *    alphanumeric vs alphanumeric lexicographic, numeric < alphanumeric).
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {

    val isPreRelease: Boolean get() = prerelease.isNotEmpty()

    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version components must be non-negative" }
    }

    override fun compareTo(other: SemanticVersion): Int {
        (major - other.major).let { if (it != 0) return it }
        (minor - other.minor).let { if (it != 0) return it }
        (patch - other.patch).let { if (it != 0) return it }
        return comparePrerelease(prerelease, other.prerelease)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (prerelease.isNotEmpty()) "-" + prerelease.joinToString(".") else ""

    companion object {

        private val FULL = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

        /**
         * Strict parse of an exact version string (e.g. "1.10.0" or "1.2.0-beta").
         * Returns null when the string is not a well-formed version.
         */
        fun parse(raw: String): SemanticVersion? {
            val m = FULL.matchEntire(raw.trim()) ?: return null
            val identifiers = m.groupValues[4]
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            return runCatching {
                SemanticVersion(
                    major = m.groupValues[1].toInt(),
                    minor = m.groupValues[2].toInt(),
                    patch = m.groupValues[3].toInt(),
                    prerelease = identifiers,
                )
            }.getOrNull()
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            if (a.isEmpty() && b.isEmpty()) return 0
            // A normal (non pre-release) version outranks any pre-release.
            if (a.isEmpty()) return 1
            if (b.isEmpty()) return -1
            // Identifiers are compared dot-by-dot; a longer set with equal prefix outranks.
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val cmp = compareIdentifier(a[i], b[i])
                if (cmp != 0) return cmp
            }
            return a.size.compareTo(b.size)
        }

        private fun compareIdentifier(a: String, b: String): Int {
            val aNum = a.all(Char::isDigit)
            val bNum = b.all(Char::isDigit)
            return when {
                aNum && bNum -> (a.toBigIntegerOrNull() ?: BigInteger.ZERO)
                    .compareTo(b.toBigIntegerOrNull() ?: BigInteger.ZERO)
                aNum -> -1 // numeric < alphanumeric
                bNum -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
