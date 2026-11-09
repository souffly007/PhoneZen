# PhoneZen bêta 7 — 13 langues

Base : bêta 6 validée sur téléphone par Franck. Version : 1.5-multilingual-beta7-test ; versionCode : 9 ; application : PhoneZen Test (`fr.bonobo.phonezen.test`).

## Six nouvelles langues

| Langue | Ressources Android |
| --- | --- |
| Polonais | values-pl |
| Turc | values-tr |
| Arabe | values-ar |
| Japonais | values-ja |
| Coréen | values-ko |
| Chinois simplifié | values-b+zh+Hans |

Les sept langues précédentes sont conservées : français, anglais, espagnol, allemand, italien, portugais brésilien et néerlandais. Leurs fichiers de ressources ont été comparés à l’archive bêta 6 et sont identiques.

Chaque langue possède les mêmes 314 ressources : 305 chaînes et 9 groupes de pluriels. Cela couvre le périmètre déjà extrait des versions précédentes : navigation, journal, clavier, réglages, profils, liste blanche, numéros signalés, contacts et création de contact, thèmes, commandes d’appel, compact/mini et notifications d’appel et de conduite.

Les traductions sont une première version à relire par des locuteurs natifs. Le contrôle des ressources ne garantit ni la qualité linguistique ni l’absence de troncature sur écran.

## Adaptations

- Pluriels adaptés aux langues : formes one/few/many/other pour le polonais ; zero/one/two/few/many/other pour l’arabe ; one/other pour le turc ; other pour le japonais, le coréen et le chinois. Les formes arabes zéro/un/deux emploient les mots plutôt que répéter un chiffre. Référence : [règles de pluriel Unicode CLDR](https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html).
- Le chinois cible l’écriture simplifiée (Hans) plutôt qu’un seul pays.
- Le support RTL était déjà activé dans le manifeste. Les deux pavés téléphoniques conservent désormais leur ordre habituel de gauche à droite, y compris en arabe. Le numéro saisi conserve le même sens de lecture.
- Les commandes de glissement répondre/refuser gardent des positions et déplacements cohérents avec le geste physique.
- Le sens de lecture des numéros du journal, des contacts, de la liste blanche et de l’écran d’appel est explicité. Les noms conservent le sens de leur propre écriture.
- L’index latéral des contacts utilise les initiales réellement présentes. Il est masqué au-delà de 35 initiales distinctes pour rester utilisable ; défilement et recherche restent disponibles. Il ne s’agit pas d’un classement phonétique pinyin/kana/hangul.
- Dans les six nouvelles langues, la consigne de recherche vocale demande de prononcer directement le nom du contact. Aucun nouveau parseur de phrases n’est ajouté. Les commandes des sept langues précédentes restent inchangées. Un test de conservation des noms dans les six nouveaux jeux de caractères a été ajouté.

## Vérifications réalisées

- `python3 tools/check_translations.py` : réussi pour les 13 langues. Contrôles XML, clés identiques, textes non vides, références Kotlin et paramètres de format ; vérification des catégories de pluriels propres aux six nouvelles langues.
- Comparaison avec la bêta 6 : fichiers de ressources des sept langues précédentes inchangés.
- `./gradlew :app:assemblePreviewDebug :app:testPreviewDebugUnitTest --console=plain` : tenté, interrompu avant compilation lors du téléchargement de Gradle 8.7 (`Network is unreachable`). Les tests Kotlin fournis n’ont donc pas été exécutés ici.
- Aucun APK produit ; aucun essai sur appareil ou émulateur ici. Les adaptations RTL nécessitent en particulier des essais réels de réponse/refus et de saisie.

## Compiler et tester

Garder une copie de la bêta 6 validée. Ouvrir ce projet séparément dans Android Studio, choisir `previewDebug`, puis compiler avec `./gradlew :app:assemblePreviewDebug`. APK attendu : `app/build/outputs/apk/preview/debug/app-preview-debug.apk`. Installer avec la même signature que la version de test précédente pour conserver les données.

Aucun `local.properties`, identifiant Supabase personnel ni ancien APK n’est fourni.

1. Placer successivement chaque nouvelle langue en première position dans Android puis relancer PhoneZen Test. Pour le chinois, choisir le chinois simplifié.
2. Parcourir journal, clavier, réglages et sous-menus, contacts, création de contact et thèmes. Vérifier les textes longs avec une grande police.
3. Contrôler appels entrants/sortants, compact/mini, second appel et notifications de conduite/appel.
4. En arabe, vérifier l’ordre 1-2-3, 4-5-6, 7-8-9, *-0-# sur les deux claviers ; contrôler le + des numéros, les codes USSD et le geste répondre à droite/refuser à gauche.
5. Vérifier les compteurs avec 0, 1, 2, 3, 5, 11, 21, 22 et 100 éléments lorsque possible, surtout en arabe et polonais.
6. Tester un contact nommé dans chaque nouvel alphabet et la recherche vocale par son nom seul. Vérifier aussi les anciens contacts et l’index latéral.
7. Revenir au français ou à l’anglais et refaire un parcours rapide.

## Limites conservées et suite

Cette version ajoute des langues au périmètre traduit de la bêta 6 ; elle ne traduit pas les textes encore codés directement en français. Il en reste notamment dans l’accueil initial, l’écran du mode conduite, l’annonce vocale de l’appelant, la messagerie vocale visuelle, la liste des numéros bloqués et certains messages de blocage ou de service. Les catégories et raisons issues des données françaises peuvent rester en français.

Le blocage selon le pays n’est pas implémenté ici, conformément à l’ordre demandé. Les règles ARCEP, les numéros d’urgence/configurations françaises, les données FINESS et le traitement WhatsApp basé sur +33 conservent leur portée actuelle. La langue de l’interface ne détermine pas le pays du filtrage. Le prochain lot pourra traiter le pays et les lignes SIM après validation de cette version.
