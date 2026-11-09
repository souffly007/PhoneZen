package fr.bonobo.phonezen.ui.screens

import android.content.Context
import fr.bonobo.phonezen.R
import fr.bonobo.phonezen.data.model.BlockingProfile
import fr.bonobo.phonezen.data.model.CallPopupMode

internal fun BlockingProfile.localizedLabel(context: Context): String = context.getString(
    when (this) {
        BlockingProfile.WORK -> R.string.profile_work
        BlockingProfile.HOME -> R.string.profile_home
        BlockingProfile.VACATION -> R.string.profile_vacation
    }
)

internal fun BlockingProfile.localizedSubtitle(context: Context): String = context.getString(
    when (this) {
        BlockingProfile.WORK -> R.string.profile_work_subtitle
        BlockingProfile.HOME -> R.string.profile_home_subtitle
        BlockingProfile.VACATION -> R.string.profile_vacation_subtitle
    }
)

internal fun CallPopupMode.localizedLabel(context: Context): String = context.getString(
    when (this) {
        CallPopupMode.FULLSCREEN -> R.string.mode_fullscreen
        CallPopupMode.COMPACT -> R.string.mode_compact
        CallPopupMode.MINI -> R.string.mode_mini
    }
)
