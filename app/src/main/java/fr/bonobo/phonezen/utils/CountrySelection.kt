package fr.bonobo.phonezen.utils

/** Pure precedence rules, shared by SIM detection and its regression tests. */
object CountrySelection {
    fun forLine(manual: String?, sim: String?, fallback: String?): String? =
        InternationalNumbers.region(manual) ?: InternationalNumbers.region(sim) ?: InternationalNumbers.region(fallback)

    fun withoutLine(manual: String?, simCountries: List<String?>): String? {
        InternationalNumbers.region(manual)?.let { return it }
        val countries = simCountries.map { InternationalNumbers.region(it) }
        return countries.takeIf { it.isNotEmpty() && it.none { c -> c == null } }?.distinct()?.singleOrNull()
    }
}
