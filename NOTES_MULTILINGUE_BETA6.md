# PhoneZen bêta 6 — notifications des appels et du mode conduite

Version : 1.5-multilingual-beta6-test ; versionCode : 8. Base : bêta 5.

## Corrections

- Mode conduite : titre et texte de détection traduits dans les sept langues.
- Appels : notification de démarrage, titres entrant/en cours, texte de remplacement et boutons Raccrocher/Ouvrir traduits.
- Noms et descriptions des catégories de notification créées par ces services extraits vers les ressources. Les catégories existantes sont mises à jour en conservant leur identifiant et leurs réglages.
- Les services actualisent leur notification lors d’un changement de configuration Android. Cette actualisation ne demande pas d’ouverture plein écran de l’appel et utilise une mise à jour silencieuse.
- Création du canal du mode conduite protégée sur Android antérieur à 8, conformément au minSdk du projet.

En anglais : Driving mode / Detection in progress…, Incoming call / Call in progress, Hang up / Open.

## Vérifications

Contrôle XML, parité des clés, textes non vides, paramètres et références Kotlin : réussi pour 314 ressources par langue, dans les sept langues.

Compilation `./gradlew :app:assemblePreviewDebug --console=plain` tentée, bloquée au téléchargement de Gradle 8.7 (Network is unreachable). Aucun APK produit ni essai sur appareil ici.

## Test sur téléphone

Compiler la variante previewDebug dans Android Studio et installer la mise à jour de PhoneZen Test avec la même signature que précédemment.

1. Avec l’anglais en première langue Android, activer la détection automatique du mode conduite et vérifier son titre et son texte dans le volet des notifications.
2. Passer un appel et recevoir un appel : vérifier les titres et les boutons Hang up/Open. Vérifier que ces boutons restent fonctionnels.
3. Changer de langue avec le service de conduite actif : vérifier l’actualisation de sa notification. Revenir au français pour vérifier le retour des textes français.
4. Vérifier les noms des catégories de notification et la conservation de leurs réglages après la mise à jour. Si une ancienne notification reste visible immédiatement après l’installation, relancer le mode conduite.

Ce correctif concerne les notifications signalées. L’annonce vocale de l’appelant, l’écran du mode conduite, les notifications de blocage et de messagerie vocale émises par d’autres composants ne sont pas entièrement traduits par ce lot. Les autres limites de la bêta 5 restent applicables.
