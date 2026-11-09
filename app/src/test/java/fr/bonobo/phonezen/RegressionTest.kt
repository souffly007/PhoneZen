package fr.bonobo.phonezen

import org.junit.Test
import org.junit.Assert.*
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.data.model.ReportedNumber

class RegressionTest {
    @Test fun foreignNumbersWithSameSuffixRemainDifferent() {
        assertNotEquals(PhoneUtils.groupKey("+33123456789"), PhoneUtils.groupKey("+44123456789"))
        assertNotEquals(PhoneUtils.groupKey("+44123456789"), PhoneUtils.groupKey("+49123456789"))
    }
    @Test fun frenchFavoritesRemainCompatible() {
        assertEquals("612345678", PhoneUtils.groupKey("06 12 34 56 78", "FR"))
        assertEquals(PhoneUtils.groupKey("0612345678", "FR"), PhoneUtils.groupKey("+33612345678", "FR"))
        assertEquals(PhoneUtils.groupKey("0612345678", "FR"), PhoneUtils.groupKey("0033612345678", "FR"))
    }
    @Test fun internationalPrefixesAreEquivalent() {
        assertEquals(PhoneUtils.groupKey("00442079460018", "FR"), PhoneUtils.groupKey("+442079460018", "FR"))
    }
    @Test fun shortNumbersKeepTheirIdentity() {
        assertEquals("UNRESOLVED:FR:123", PhoneUtils.groupKey("123", "FR"))
        assertEquals("UNRESOLVED:FR:666", PhoneUtils.groupKey("666", "FR"))
        assertNotEquals(PhoneUtils.groupKey("123", "FR"), PhoneUtils.groupKey("123", "US"))
    }
    @Test fun expiredReportsDoNotStaySuspect() {
        assertFalse(ReportedNumber(reports=30, expiresAt="2020-01-01T00:00:00Z").isSuspect())
        assertFalse(ReportedNumber(reports=30, expiresAt="invalid").isSuspect())
        assertTrue(ReportedNumber(reports=30, expiresAt="2099-01-01T00:00:00Z").isSuspect())
    }
    @Test fun freshLowSignalCountsDoNotBecomeSuspect() {
        assertFalse(ReportedNumber(reports=2, expiresAt="2099-01-01T00:00:00Z").isSuspect())
        assertTrue(ReportedNumber(reports=3, expiresAt="2099-01-01T00:00:00Z").isSuspect())
    }
}
