package fr.bonobo.phonezen

import fr.bonobo.phonezen.utils.PhoneUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitaires pour PhoneUtils.
 */
class PhoneUtilsTest {

    @Test
    fun formatNumber_FrenchMobile10Digits() {
        // Numéro français mobile standard (10 chiffres)
        assertEquals("06 12 34 56 78", PhoneUtils.formatNumber("0612345678"))
        assertEquals("06 12 34 56 78", PhoneUtils.formatNumber("06 12 34 56 78"))
    }

    @Test
    fun formatNumber_FrenchMobileWithSpaces() {
        // Numéro déjà formaté
        assertEquals("06 12 34 56 78", PhoneUtils.formatNumber("06 12 34 56 78"))
    }

    @Test
    fun formatNumber_FrenchInternational() {
        // Numéro français au format international
        assertEquals("+33 6 12 34 56 78", PhoneUtils.formatNumber("+33612345678"))
        assertEquals("+33 7 12 34 56 78", PhoneUtils.formatNumber("+33712345678"))
    }

    @Test
    fun formatNumber_FrenchInternationalWithPlus33() {
        assertEquals("+33 6 12 34 56 78", PhoneUtils.formatNumber("+33612345678"))
    }

    @Test
    fun formatNumber_Empty() {
        assertEquals("", PhoneUtils.formatNumber(""))
    }

    @Test
    fun formatNumber_ShortNumber() {
        // Numéro court (numéro d'urgence, service, etc.)
        assertEquals("15", PhoneUtils.formatNumber("15"))
        assertEquals("112", PhoneUtils.formatNumber("112"))
        assertEquals("3919", PhoneUtils.formatNumber("3919"))
    }

    @Test
    fun formatNumber_LongInternational() {
        // Numéro international long (autre pays)
        assertEquals("+12125551234", PhoneUtils.formatNumber("+12125551234"))
        assertEquals("+442079460018", PhoneUtils.formatNumber("+442079460018"))
    }

    @Test
    fun formatNumber_FrenchLandline() {
        // Numéro fixe français
        assertEquals("01 23 45 67 89", PhoneUtils.formatNumber("0123456789"))
        assertEquals("02 34 56 78 90", PhoneUtils.formatNumber("0234567890"))
    }

    @Test
    fun normalizeNumber_RemovesSpaces() {
        assertEquals("0612345678", PhoneUtils.normalizeNumber("06 12 34 56 78", "FR"))
    }

    @Test
    fun normalizeNumber_HandlesInternational() {
        assertEquals("+33612345678", PhoneUtils.normalizeNumber("+33612345678", "FR"))
        assertEquals("+33612345678", PhoneUtils.normalizeNumber("0033612345678", "FR"))
    }

    @Test
    fun groupKey_FrenchNumbersAreEquivalent() {
        // Les différentes représentations d'un même numéro français doivent avoir la même clé
        val key1 = PhoneUtils.groupKey("0612345678", "FR")
        val key2 = PhoneUtils.groupKey("+33612345678", "FR")
        val key3 = PhoneUtils.groupKey("0033612345678", "FR")
        
        assertEquals(key1, key2)
        assertEquals(key2, key3)
    }

    @Test
    fun isAnonymousNumber_DetectsAnonymous() {
        assertTrue(PhoneUtils.isAnonymousNumber(null))
        assertTrue(PhoneUtils.isAnonymousNumber(""))
        assertTrue(PhoneUtils.isAnonymousNumber("-1"))
        assertTrue(PhoneUtils.isAnonymousNumber("-2"))
        assertTrue(PhoneUtils.isAnonymousNumber("unknown"))
        assertTrue(PhoneUtils.isAnonymousNumber("UNKNOWN"))
        assertTrue(PhoneUtils.isAnonymousNumber("private"))
        assertTrue(PhoneUtils.isAnonymousNumber("PRIVATE"))
        assertTrue(PhoneUtils.isAnonymousNumber("hidden"))
        assertTrue(PhoneUtils.isAnonymousNumber("anonymous"))
        assertTrue(PhoneUtils.isAnonymousNumber("contains anonymous"))
    }

    @Test
    fun isAnonymousNumber_RealNumbersAreNotAnonymous() {
        assertFalse(PhoneUtils.isAnonymousNumber("0612345678"))
        assertFalse(PhoneUtils.isAnonymousNumber("+33612345678"))
        assertFalse(PhoneUtils.isAnonymousNumber("123"))
    }

    @Test
    fun formatDuration_ZeroSeconds() {
        assertEquals("0s", PhoneUtils.formatDuration(0))
    }

    @Test
    fun formatDuration_LessThanMinute() {
        assertEquals("30s", PhoneUtils.formatDuration(30))
        assertEquals("59s", PhoneUtils.formatDuration(59))
    }

    @Test
    fun formatDuration_Minutes() {
        assertEquals("01:00", PhoneUtils.formatDuration(60))
        assertEquals("01:30", PhoneUtils.formatDuration(90))
        assertEquals("05:45", PhoneUtils.formatDuration(345))
    }

    @Test
    fun formatDuration_LongCall() {
        assertEquals("60:00", PhoneUtils.formatDuration(3600))
        assertEquals("90:30", PhoneUtils.formatDuration(5430))
    }
}
