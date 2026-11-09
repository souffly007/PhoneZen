# PhoneZen 1.5-bilingual-beta2 — test français / anglais

Cette mise à jour corrige la beta1, qui ne traduisait que la navigation.

## Textes traduits dans cette version

- Journal : titre, filtres, recherche, sélection, compteurs, détails des appels et menu d’actions.
- Clavier : titre, saisie, collage, choix du contact et de SIM, répondeur et messages de reconnaissance vocale.
- Réglages : sections, descriptions, noms de profils et modes d’affichage, sauvegarde/restauration, diagnostic, confirmations et sélection de l’heure.
- Dialogues du répondeur par SIM et erreurs de restauration.
- Commandes vocales anglaises « Call », « Dial » et « Phone » ; langue de reconnaissance issue de la configuration Android.

Les noms des contacts, notes personnelles et données déjà enregistrées ne sont pas traduits. La catégorie de signalement « Indésirable » conserve sa valeur existante pour éviter de modifier les données communautaires.

Ce correctif couvre les trois écrans principaux cités ci-dessus et leurs dialogues intégrés. Il ne constitue pas encore une traduction de toute l’application : les pages secondaires ouvertes depuis Réglages (édition des profils, thèmes, liste blanche, classement communautaire), l’accueil initial et l’écran d’appel comportent encore des textes français. Les règles anti-spam restent celles de la configuration française.

## Vérification

- `python3 tools/check_translations.py` : réussi ; 156 ressources présentes en français et anglais, références et paramètres de format contrôlés.
- Compilation et tests Android tentés avec `./gradlew :app:assemblePreviewDebug :app:testPreviewDebugUnitTest` : bloqués avant compilation, car le téléchargement de Gradle 8.7 échoue avec « Network is unreachable ».
- Aucun APK beta2 compilé ni test sur téléphone réalisé ici. Les contrôles de ressources ne remplacent pas la compilation Kotlin.

## Installation pour le test

1. Extraire dans un nouveau dossier, ouvrir dans Android Studio et synchroniser Gradle.
2. Choisir la variante `previewDebug` (package `fr.bonobo.phonezen.test`, à côté de la version normale).
3. Compiler avec `./gradlew :app:assemblePreviewDebug` ou Android Studio.
4. Passer le téléphone en anglais, relancer PhoneZen Test et vérifier filtres, menus, choix de SIM et réglages. Refaire le parcours en français.
5. Les paramètres Supabase personnels peuvent être remis dans `local.properties` pour tester le service communautaire. Aucune clé n’est fournie dans cette archive.

VersionCode : 4. VersionName : 1.5-bilingual-beta2 (suffixe -test pour preview).
