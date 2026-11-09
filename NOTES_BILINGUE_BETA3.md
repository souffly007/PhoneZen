# PhoneZen 1.5-bilingual-beta3

Cette version complète la beta2 et traduit les trois sous-menus demandés :

- Profils : noms et descriptions Travail/Domicile/Vacances, règles affichées, horaires nocturnes, options de vacances, date et profil de retour, résumé et boutons.
- Numéros signalés : chargement, liste vide, compteurs, expiration, jours restants et durée totale. Le libellé connu « Indésirable » est traduit uniquement à l’affichage ; les données communautaires restent inchangées.
- Liste blanche : titre, formulaire, erreurs de saisie, confirmation d’ajout, liste vide, nombre de numéros protégés, dialogue de retrait et boutons.

Les noms de contacts, numéros et éventuelles étiquettes libres fournies par la communauté sont conservés.
Les règles de filtrage, priorités et horaires ne sont pas modifiés par cette traduction.

## Vérification

- Contrôle XML, correspondance des ressources FR/EN, arguments de format et références Kotlin : réussi (222 ressources par langue).
- Compilation et tests Android tentés : bloqués au téléchargement de Gradle 8.7 (« Network is unreachable »). Aucun APK beta3 généré ici.
- Compiler la variante previewDebug dans Android Studio (versionCode 5, versionName 1.5-bilingual-beta3-test, package fr.bonobo.phonezen.test).

## Parcours de test

1. En anglais, ouvrir Settings → profils : vérifier les trois cartes et le résumé, activer/désactiver les horaires puis ouvrir le calendrier de retour.
2. Ouvrir les numéros signalés : vérifier les libellés des compteurs et des dates si des données communautaires sont disponibles.
3. Ouvrir l’allowlist : vérifier les erreurs de saisie, ajouter un numéro de test puis le retirer avec confirmation.
4. Refaire le parcours en français.

Cette note remplace les limitations de la beta2 concernant ces trois pages. Les autres pages non demandées (par exemple les thèmes ou l’écran d’appel) peuvent encore contenir du français.
