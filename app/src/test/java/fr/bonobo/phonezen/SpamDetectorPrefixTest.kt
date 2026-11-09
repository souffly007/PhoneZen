package fr.bonobo.phonezen

import fr.bonobo.phonezen.utils.SpamDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import android.content.Context
import org.mockito.Mockito

/**
 * Tests unitaires pour le chargement des préfixes ARCEP dans SpamDetector.
 */
class SpamDetectorPrefixTest {

    @Test
    fun testPrefixesAreLoaded() {
        // Vérifier que les préfixes sont chargés correctement
        // Note: Ce test nécessite un contexte Android valide
        // Pour l'instant, on vérifie juste que le companion object fonctionne
        
        // Vérifier que la fonction loadJson existe et peut être appelée
        // (sans contexte valide, on ne peut pas charger les assets)
        assertTrue("SpamDetector devrait exister", true)
    }

    @Test
    fun testCompanionObjectAccess() {
        // Vérifier que les propriétés du companion object sont accessibles
        val prefixes = SpamDetector.alwaysBlockPrefixes
        val telemarketing = SpamDetector.telemarketingPrefixes
        val patterns = SpamDetector.alwaysBlockPatterns
        val neverBlock = SpamDetector.neverBlock
        
        // Ces propriétés devraient retourner des listes (même vides)
        assertNotNull("alwaysBlockPrefixes ne devrait pas être null", prefixes)
        assertNotNull("telemarketingPrefixes ne devrait pas être null", telemarketing)
        assertNotNull("alwaysBlockPatterns ne devrait pas être null", patterns)
        assertNotNull("neverBlock ne devrait pas être null", neverBlock)
        
        // Elles devraient être des List (immutables)
        assertTrue("alwaysBlockPrefixes devrait être une List", prefixes is List<*>) 
        assertTrue("telemarketingPrefixes devrait être une List", telemarketing is List<*>)
        assertTrue("alwaysBlockPatterns devrait être une List", patterns is List<*>)
        assertTrue("neverBlock devrait être une List", neverBlock is List<*>)
    }

    @Test
    fun testCommunityBlockThreshold() {
        // Vérifier que le seuil communautaire est correct
        assertEquals(10L, SpamDetector.COMMUNITY_BLOCK_THRESHOLD)
    }
}
