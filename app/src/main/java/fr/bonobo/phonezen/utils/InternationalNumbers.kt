package fr.bonobo.phonezen.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.ShortNumberInfo
import java.util.Locale

/** Pure numbering logic. A region is a numbering context, never the UI language. */
object InternationalNumbers {
    private val util by lazy { PhoneNumberUtil.getInstance() }
    val regions: Set<String> get() = util.supportedRegions

    fun region(value: String?): String? = value?.uppercase(Locale.ROOT)?.takeIf { it in regions }

    fun clean(raw: String?): String = raw.orEmpty().trim().map { c ->
        if (c.isDigit()) Character.digit(c, 10).digitToChar() else c
    }.joinToString("").replace(Regex("[\\s().-]"), "")

    fun e164(raw: String?, region: String?): String? {
        val text = clean(raw)
        if (!text.matches(Regex("\\+?[0-9]+"))) return null
        if (!text.startsWith("+") && region == null) return null
        return runCatching {
            val number = util.parse(text, region ?: "ZZ")
            if (util.isPossibleNumber(number)) util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164) else null
        }.getOrNull()
    }

    fun canonical(raw: String?, region: String?): String = e164(raw, region) ?: clean(raw)

    // French rules apply only to an identified +33 full number, or a French short code.
    fun frenchLocal(raw: String?, region: String?): String? {
        val canonical = e164(raw, region)
        if (canonical != null) return canonical.takeIf { it.startsWith("+33") && it.length == 12 }
            ?.let { "0" + it.drop(3) }
        val text = clean(raw)
        return text.takeIf { region == "FR" && it.matches(Regex("[0-9]{2,6}")) }
    }

    fun isEmergency(raw: String?, region: String?): Boolean = region != null && runCatching {
        ShortNumberInfo.getInstance().isEmergencyNumber(clean(raw), region)
    }.getOrDefault(false)

    /** Old releases stored French national numbers. Never reinterpret them as another country. */
    fun matchesStored(raw: String?, stored: String, region: String?): Boolean {
        val target = e164(raw, region) ?: return region == "FR" &&
            clean(raw).matches(Regex("[0-9]{2,6}")) && clean(raw) == clean(stored)
        val saved = e164(stored, if (clean(stored).startsWith("+")) null else "FR")
        return target == saved
    }

    // Keep the nine-digit keys of existing French favorites; foreign keys stay international.
    fun favoriteKey(raw: String?, region: String?): String {
        val n = e164(raw, region) ?: return "UNRESOLVED:${region ?: "ZZ"}:${clean(raw)}"
        return if (n.startsWith("+33") && n.length == 12) n.drop(3) else n
    }

    // Keep the existing French server namespace. Other countries always use E.164.
    fun communityKey(raw: String?, region: String?): String? = e164(raw, region)?.let { n ->
        if (n.startsWith("+33") && n.length == 12) "0" + n.drop(3) else n
    }
}
