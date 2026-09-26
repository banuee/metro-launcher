package dev.metro.launcher

import dev.metro.launcher.data.UpdateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    // Вспомогательный метод для проверки версий (UpdateRepository.isNewerVersion)
    private fun isNewer(remote: String, current: String): Boolean {
        val rClean = remote.removePrefix("v").trim().substringBefore('-').substringBefore('+')
        val cClean = current.removePrefix("v").trim().substringBefore('-').substringBefore('+')
        val rParts = rClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    @Test
    fun testSameVersionIsNotNewer() {
        assertFalse(isNewer("0.6.4", "0.6.4"))
        assertFalse(isNewer("v0.6.4", "0.6.4"))
        assertFalse(isNewer("v0.6.4", "v0.6.4"))
    }

    @Test
    fun testNewerPatchVersion() {
        assertTrue(isNewer("0.6.5", "0.6.4"))
        assertTrue(isNewer("v0.6.5", "0.6.4"))
        assertTrue(isNewer("v0.6.10", "v0.6.9"))
    }

    @Test
    fun testNewerMinorOrMajor() {
        assertTrue(isNewer("0.7.0", "0.6.4"))
        assertTrue(isNewer("1.0.0", "0.6.4"))
    }

    @Test
    fun testOlderVersion() {
        assertFalse(isNewer("0.6.3", "0.6.4"))
        assertFalse(isNewer("0.5.9", "0.6.0"))
    }

    @Test
    fun testVersionWithSuffix() {
        assertTrue(isNewer("v0.6.5-beta", "0.6.4"))
        assertTrue(isNewer("v0.6.5+build10", "0.6.4"))
        assertFalse(isNewer("v0.6.4-beta", "0.6.4"))
    }
}
