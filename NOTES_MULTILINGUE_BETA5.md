# PhoneZen 1.5-multilingual-beta5 — projet de test

Suite de la bêta 4 testée par Franck. Les sept langues sont conservées : français, anglais, espagnol, allemand, italien, portugais brésilien et néerlandais.

## Ce qui change

80 ressources supplémentaires par langue, soit 302 ressources au total par langue.

- Appel plein écran : états, commandes audio, mise en attente, réponse/refus, double appel, niveaux de risque et badges de confiance.
- Fenêtres compactes et mini : états et descriptions des boutons pour l’accessibilité.
- Sélecteur de thème : descriptions et nom du thème clair. Cyber Dark et Cyanogen gardent leur nom.
- Contacts et favoris : recherche, états vides, menus et confirmations, suppression et liste blanche.
- Création de contact : champs, compte de destination, confirmations informatives et erreurs de validation/enregistrement.
- Second appel : commandes et intitulé de la fenêtre dédiée.

Les données saisies par l’utilisateur (noms, numéros, comptes) ne sont pas traduites. Les changements portent sur les textes ; ils ne corrigent pas le fonctionnement du filtrage, de la double SIM ou des marges des fenêtres.

## Vérifications

`python3 tools/check_translations.py` réussit : 302 ressources dans chacune des sept langues, clés identiques, XML valide, textes non vides, paramètres de format correspondants et références Kotlin existantes.

La compilation et les tests Kotlin ont été tentés avec `./gradlew :app:assemblePreviewDebug :app:testPreviewDebugUnitTest --console=plain`. Ils sont bloqués avant compilation par le téléchargement de Gradle 8.7 : `Network is unreachable`. Aucun APK de cette bêta n’a été produit ici. La compilation Compose et l’affichage sur appareil restent donc à vérifier.

## Compiler et tester

Ouvrir ce projet séparément dans Android Studio, sélectionner la variante `previewDebug`, puis compiler avec `./gradlew :app:assemblePreviewDebug`.

- Application : PhoneZen Test ; package : `fr.bonobo.phonezen.test`.
- Version : `1.5-multilingual-beta5-test` ; versionCode : 7.
- APK attendu : `app/build/outputs/apk/preview/debug/app-preview-debug.apk`.
- Aucun fichier `local.properties` ni identifiant Supabase personnel fourni.

Dans chaque langue, relancer l’application après avoir changé la langue du téléphone. Vérifier les thèmes, contacts/favoris, la création de contact, puis les appels entrants/sortants en plein écran, compact et mini. Vérifier également le second appel et les commandes audio. Contrôler les textes longs, notamment en allemand, avec une grande taille de police. Les suppressions sont réelles : utiliser un contact de test pour vérifier les confirmations.

## Travail restant identifié

Cette livraison termine le lot appels/thèmes/contacts, pas toute l’internationalisation de PhoneZen. Des textes restent en français dans l’accueil initial, le mode conduite, la liste des numéros bloqués, la messagerie vocale visuelle et certains messages système/notifications. Les raisons de risque et catégories provenant des données ARCEP peuvent aussi rester françaises à l’écran pendant les appels.

L’annonce vocale de l’appelant utilise encore le français. Les règles ARCEP et les données françaises ne deviennent pas internationales par traduction. Le lien WhatsApp des contacts conserve également une construction de numéro basée sur +33 ; ce comportement existant doit être corrigé dans un lot consacré aux numéros internationaux.

La relecture par des locuteurs natifs et les essais réels restent nécessaires. Aucune nouvelle langue n’est ajoutée dans cette bêta.
