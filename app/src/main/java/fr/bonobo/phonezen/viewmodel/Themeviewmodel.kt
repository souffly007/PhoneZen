package fr.bonobo.phonezen.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")
private val THEME_KEY = stringPreferencesKey("app_theme")

class ThemeViewModel(app: Application) : AndroidViewModel(app) {

    private val _theme = MutableStateFlow(AppTheme.CYBER_DARK)
    val theme = _theme.asStateFlow()

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                val saved = prefs[THEME_KEY]
                if (saved != null) {
                    _theme.value = AppTheme.valueOf(saved)
                }
            }
        }
    }

    fun setTheme(appTheme: AppTheme) {
        _theme.value = appTheme
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[THEME_KEY] = appTheme.name
            }
        }
    }
}