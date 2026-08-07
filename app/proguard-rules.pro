-keep class fr.bonobo.phonezen.** { *; }
-keepattributes *Annotation*
# Empêcher la suppression et le renommage de l'activité d'appel
-keep class fr.bonobo.phonezen.ui.screens.InCallActivity { *; }

# Empêcher la suppression du récepteur de démarrage (Boot)
-keep class fr.bonobo.phonezen.service.BootReceiver { *; }

# Garder le service InCall (indispensable pour que le système Telecom le trouve)
-keep class fr.bonobo.phonezen.service.PhoneZenInCallService { *; }

# Si tu as d'autres services ou receivers (ex: pour le spam ou la messagerie)
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver