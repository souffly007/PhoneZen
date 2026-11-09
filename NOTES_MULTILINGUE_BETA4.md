# PhoneZen 1.5-multilingual-beta4

Base : bêta 3 français/anglais validée par l’utilisateur.

## Langues

Les mêmes 222 ressources (213 chaînes et 9 groupes de pluriels) existent désormais dans les sept langues : français, anglais, espagnol, allemand, italien, portugais brésilien et néerlandais.

Les langues supplémentaires couvrent le périmètre déjà extrait de la bêta 3 : navigation, Journal, Clavier, Réglages, Profils, Numéros signalés, Liste blanche, choix de SIM, dialogues du répondeur et messages de sauvegarde/restauration.

Les ressources sont choisies par Android selon les langues configurées sur le téléphone. Le français reste le jeu de ressources par défaut. Pour tester le portugais, choisir Portugais (Brésil). Aucun nouveau sélecteur de langue interne n’est ajouté.

Les cinq nouvelles traductions nécessitent une relecture en usage réel ; elles n’ont pas été validées par des testeurs natifs. Les pages qui conservaient des textes codés en dur dans la bêta 3, notamment l’écran d’appel, le sélecteur de thème et une partie des Contacts, ne sont pas entièrement traduites par ce lot.

## Commandes vocales

Le parseur accepte les commandes affichées dans les cinq nouvelles langues :

- Espagnol : « Llama a Camille Martin ».
- Allemand : « Rufe Camille Martin an ».
- Italien : « Chiama Camille Martin ».
- Portugais : « Ligar para Camille Martin ».
- Néerlandais : « Bel Camille Martin ».

Les noms seuls et les commandes françaises/anglaises restent reconnus. Les préfixes les plus longs sont traités en premier. Les tests Kotlin correspondants sont fournis dans VoiceLanguagesTest.kt, mais n’ont pas pu être exécutés ici.

## Vérifications et limites

- `python3 tools/check_translations.py` : réussi pour les sept langues, avec contrôle des clés, des ressources non vides, des arguments de format et des références Kotlin.
- Espaces de jonction des phrases et retours à la ligne vérifiés.
- `./gradlew :app:assemblePreviewDebug :app:testPreviewDebugUnitTest` : tenté, mais interrompu avant compilation au téléchargement de Gradle 8.7 (« Network is unreachable »).
- Aucun APK bêta 4 généré, aucune validation sur appareil ni capture de l’affichage dans les nouvelles langues réalisée ici.
- Les règles ARCEP, la base santé et les conseils propres à la France restent français dans leur portée. Changer de langue ne change pas le pays de filtrage ou l’opérateur. Les textes traduits le précisent lorsque nécessaire.

## Compilation et tests

Ouvrir ce projet dans un dossier distinct dans Android Studio et sélectionner `previewDebug`. VersionCode : 6 ; versionName : 1.5-multilingual-beta4-test ; package : fr.bonobo.phonezen.test.

Compiler avec `./gradlew :app:assemblePreviewDebug`. Le fichier attendu est `app/build/outputs/apk/preview/debug/app-preview-debug.apk`.

Pour chaque langue, la placer en première position dans les paramètres du téléphone, relancer PhoneZen Test et parcourir les écrans traduits. Vérifier les boutons, les textes longs, les compteurs à 0/1/plusieurs, les confirmations et le clavier. Refaire un parcours français pour vérifier l’absence de régression.

Les paramètres personnels Supabase peuvent être remis dans `local.properties`. Aucune clé n’est fournie.
