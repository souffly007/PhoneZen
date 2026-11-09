package fr.bonobo.phonezen

import fr.bonobo.phonezen.utils.CountrySelection as C
import org.junit.Assert.*
import org.junit.Test

class CountrySelectionTest {
    @Test fun lineOverrideWins() = assertEquals("BE", C.forLine("BE", "FR", "US"))
    @Test fun detectedLineWinsOverFallback() = assertEquals("TR", C.forLine(null, "TR", "FR"))
    @Test fun fallbackOnlyWhenLineCountryMissing() = assertEquals("FR", C.forLine(null, null, "FR"))
    @Test fun noImplicitFrance() = assertNull(C.forLine(null, null, null))
    @Test fun mixedSimsRemainAmbiguousWithoutALine() = assertNull(C.withoutLine(null, listOf("FR", "DE")))
    @Test fun sameCountrySimsAreUnambiguous() = assertEquals("FR", C.withoutLine(null, listOf("FR", "FR")))
    @Test fun unreadableSimDoesNotInheritTheOtherSimCountry() = assertNull(C.withoutLine(null, listOf("FR", null)))
    @Test fun noSimRequiresManualSelection() {
        assertNull(C.withoutLine(null, emptyList()))
        assertEquals("JP", C.withoutLine("JP", emptyList()))
    }
}
