package fr.bonobo.phonezen.ui.screens

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.bonobo.phonezen.R
import fr.bonobo.phonezen.utils.CountrySettings
import fr.bonobo.phonezen.utils.InternationalNumbers
import fr.bonobo.phonezen.ui.theme.LocalColors
import java.util.Locale

@Composable
internal fun CountrySettingsSection(settingsRevision: Int = 0, onChanged: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val settings = remember(context) { CountrySettings(context) }
    val locale = context.resources.configuration.locales[0]
    var revision by remember { mutableIntStateOf(0) }
    val lines = remember(revision, settingsRevision, context) { settings.lines() }
    var picker by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<PhoneAccountHandle?>(null) }
    var query by remember { mutableStateOf("") }
    fun name(code: String?) = if (code == null) context.getString(R.string.country_unknown)
        else Locale("", code).getDisplayCountry(locale).ifBlank { code }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVar)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.country_title).removePrefix("🌍 "), color = colors.neonCyan)
            // Summarize resolved line countries without choosing one over another.
            val country = if (lines.isEmpty()) settings.defaultRegion()
                else lines.map { it.country }.distinct().singleOrNull()
            val displayCountry = country?.let { name(it) } ?: "inconnue"
            val protection = if (country == "FR") "Protection complète" else "Protection générique"
            Text(
                "Détection pays : $displayCountry · $protection",
                modifier = Modifier.clickable { target = null; query = ""; picker = true },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecond
            )
            lines.forEach { line ->
                TextButton(onClick = { target = line.handle; query = ""; picker = true }) {
                    val mode = if (settings.manual(line.handle) == null) context.getString(R.string.automatic) else context.getString(R.string.country_manual)
                    Text(line.label + ": " + mode + " · " + name(line.country))
                }
            }
        }
    }
    if (picker) {
        val countries = remember(locale) { InternationalNumbers.regions.map { it to name(it) }.sortedBy { it.second } }
        fun select(code: String?) {
            settings.set(target, code)
            revision++
            picker = false
            onChanged()
        }
        AlertDialog(onDismissRequest = { picker = false },
            title = { Text(stringResource(R.string.country_choose)) },
            text = {
                Column {
                    TextButton(onClick = { target = null; query = "" }) {
                        val mode = if (settings.manual() == null) context.getString(R.string.automatic)
                            else context.getString(R.string.country_manual)
                        Text(context.getString(R.string.country_default) + ": " + mode + " · " + name(settings.defaultRegion()))
                    }
                    target?.let { handle ->
                        Text(lines.firstOrNull { it.handle == handle }?.label.orEmpty(), color = colors.textSecond)
                    } ?: Text(stringResource(R.string.country_default), color = colors.textSecond)
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.extra_search)) })
                    TextButton(onClick = { select(null) }) { Text(stringResource(R.string.automatic)) }
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(countries.filter { it.first.contains(query, true) || it.second.contains(query, true) }, key = { it.first }) { (code, display) ->
                            Text("$display ($code)", Modifier.fillMaxWidth().clickable { select(code) }.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
