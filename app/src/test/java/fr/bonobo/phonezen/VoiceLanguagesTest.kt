package fr.bonobo.phonezen

import fr.bonobo.phonezen.util.VoiceDialHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLanguagesTest {
    @Test fun extractsContactFromEachSupportedLanguage() {
        listOf(
            "Appelle Camille Martin", "Call Camille Martin", "Llama a Camille Martin",
            "Rufe Camille Martin an", "Chiama Camille Martin", "Ligar para Camille Martin",
            "Bel Camille Martin"
        ).forEach { command ->
            assertEquals(command, "Camille Martin", VoiceDialHelper.parseCommand(command))
        }
    }

    @Test fun keepsBareNamesAndUsesTheLongestPrefix() {
        assertEquals("Anne", VoiceDialHelper.parseCommand("Anne"))
        assertEquals("0612345678", VoiceDialHelper.parseCommand("Appeler le 0612345678"))
        assertEquals("An", VoiceDialHelper.parseCommand("Rufe An an"))
        assertEquals(null, VoiceDialHelper.parseCommand("   "))
    }

    @Test fun preservesNamesInTheSixNewLocales() {
        // The six new voice prompts request a bare contact name.
        listOf("Łukasz Żak", "İpek Yılmaz", "أحمد علي", "山田太郎", "김민수", "张伟").forEach { name ->
            assertEquals(name, VoiceDialHelper.parseCommand("  $name  "))
        }
    }
}
