package com.cadence.core.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {

    private fun v(s: String) = SemanticVersion.parse(s)!!

    @Test
    fun `parses core and prerelease`() {
        assertEquals(SemanticVersion(1, 10, 0), v("1.10.0"))
        assertEquals(listOf("beta"), v("1.2.0-beta").prerelease)
        assertEquals(listOf("beta", "2"), v("1.2.0-beta.2").prerelease)
        assertTrue(v("1.2.0-beta").isPreRelease)
        assertFalse(v("1.2.0").isPreRelease)
    }

    @Test
    fun `rejects malformed versions`() {
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("1.0"))
        assertNull(SemanticVersion.parse("1.0.0.1"))
        assertNull(SemanticVersion.parse("v1.0.0"))
        assertNull(SemanticVersion.parse("1.0.0-"))
        assertNull(SemanticVersion.parse("1.0.0 extra"))
    }

    @Test
    fun `numeric precedence 1_10_0 greater than 1_9_9`() {
        assertTrue(v("1.10.0") > v("1.9.9"))
    }

    @Test
    fun `major dominates 2_0_0 greater than 1_99_99`() {
        assertTrue(v("2.0.0") > v("1.99.99"))
    }

    @Test
    fun `release outranks prerelease of same version`() {
        assertTrue(v("1.2.0") > v("1.2.0-beta"))
    }

    @Test
    fun `prerelease ordering numeric alpha`() {
        assertTrue(v("1.0.0-1") < v("1.0.0-alpha"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-alpha.1"))
        assertTrue(v("1.0.0-beta.11") > v("1.0.0-beta.2"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-beta"))
    }

    @Test
    fun `equal versions`() {
        assertEquals(0, v("1.2.3").compareTo(v("1.2.3")))
        assertEquals(0, v("1.2.3-beta").compareTo(v("1.2.3-beta")))
    }
}
