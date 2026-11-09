package fr.bonobo.phonezen

import fr.bonobo.phonezen.utils.InternationalNumbers as N
import org.junit.Assert.*
import org.junit.Test

class InternationalNumbersTest {
    @Test fun frenchFormsShareAnIdentity() {
        listOf("01 62 12 34 56", "+33 1 62 12 34 56", "0033162123456").forEach {
            assertEquals("+33162123456", N.e164(it, "FR"))
            assertEquals("0162123456", N.frenchLocal(it, "FR"))
        }
    }
    @Test fun internationalPrefixWinsOverSimCountry() {
        assertEquals("+442079460018", N.e164("+44 20 7946 0018", "FR"))
        assertNull(N.frenchLocal("+442079460018", "FR"))
        assertEquals("0162123456", N.frenchLocal("+33162123456", "GB"))
    }
    @Test fun localGermanMobileIsNotFrenchTelemarketing() {
        assertEquals("+491621234567", N.e164("0162 1234567", "DE"))
        assertNull(N.frenchLocal("0162 1234567", "DE"))
    }
    @Test fun sameLocalDigitsOnDifferentSimsDoNotCollide() {
        assertNotEquals(N.e164("0212345678", "FR"), N.e164("0212345678", "IT"))
        assertFalse(N.matchesStored("0212345678", "0212345678", "IT"))
        assertFalse(N.matchesStored("+390212345678", "0212345678", "FR"))
    }
    @Test fun italianSignificantZeroIsPreserved() {
        assertEquals("+390212345678", N.e164("02 1234 5678", "IT"))
    }
    @Test fun unknownRegionNeverInventsACountry() {
        assertNull(N.e164("0162123456", null))
        assertNull(N.frenchLocal("0162123456", null))
        assertFalse(N.matchesStored("0162123456", "0162123456", null))
        assertEquals("+33162123456", N.e164("+33162123456", null))
    }
    @Test fun internationalAccessPrefixDependsOnRegion() {
        assertEquals("+442079460018", N.e164("011442079460018", "US"))
        assertNotEquals("+442079460018", N.e164("00442079460018", "US"))
    }
    @Test fun arabicDigitsAreNormalizedWithoutChangingMeaning() {
        assertEquals("+33162123456", N.e164("+٣٣١٦٢١٢٣٤٥٦", null))
    }
    @Test fun oldFrenchFavoritesAndListsRemainCompatible() {
        assertEquals("612345678", N.favoriteKey("+33612345678", "TR"))
        assertTrue(N.matchesStored("+33612345678", "0612345678", "TR"))
        assertEquals("+905321234567", N.favoriteKey("+905321234567", "FR"))
    }
    @Test fun communityNamespacesCannotCollideAcrossCountries() {
        assertEquals("0162123456", N.communityKey("+33162123456", null))
        assertEquals("+491621234567", N.communityKey("01621234567", "DE"))
        assertNull(N.communityKey("0162123456", null))
    }
    @Test fun shortCodesAndUssdAreNeverConvertedToSubscriberNumbers() {
        assertNull(N.e164("112", "FR"))
        assertNull(N.e164("*#06#", "FR"))
        assertTrue(N.isEmergency("112", "FR"))
        assertTrue(N.isEmergency("911", "US"))
        assertFalse(N.isEmergency("9111234567", "US"))
        assertFalse(N.isEmergency("15", "US"))
    }
    @Test fun invalidInputsCannotBecomeGlobalBlockKeys() {
        listOf("", "private", "1+23456789", "12345678901234567890").forEach {
            assertNull(N.e164(it, "FR"))
        }
    }
    @Test fun unresolvedNumbersCannotMatchLegacyFrenchFavorites() {
        assertNotEquals("612345678", N.favoriteKey("612345678", null))
        assertNotEquals(N.favoriteKey("123", "FR"), N.favoriteKey("123", "US"))
    }
    @Test fun legacyShortAllowlistIsScopedToFrance() {
        assertTrue(N.matchesStored("3900", "3900", "FR"))
        assertFalse(N.matchesStored("3900", "3900", "DE"))
        assertFalse(N.matchesStored("3900", "3900", null))
    }
}
