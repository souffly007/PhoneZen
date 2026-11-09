# PhoneZen bêta 8 — première base de protection internationale

Base : bêta 7 à 13 langues validée par Franck. Version : `1.5-international-beta8-test`, versionCode 10, application `fr.bonobo.phonezen.test`.

## Fonctionnement

Dans **Réglages → Pays et protection**, chaque ligne téléphonique accessible affiche son pays et permet un choix manuel. Automatique utilise le pays de la SIM associée au compte téléphonique. L’application n’utilise ni la langue de l’interface, ni le GPS, ni le réseau d’itinérance pour changer ce pays.

Le **pays de secours / contacts** sert aux numéros saisis sans indicatif et aux contacts au format national. Automatique n’en déduit un pays que si toutes les SIM actives connues ont le même pays. Avec deux SIM de pays différents, il faut choisir ce pays pour interpréter les contacts nationaux, ou enregistrer les numéros au format international. Le choix par ligne est prioritaire ; la détection de cette ligne vient ensuite, puis le pays de secours lorsqu’elle manque.

Les numéros internationaux explicites gardent leur identité : un numéro +44 reste britannique même avec une SIM française. Un numéro +33 reste soumis aux règles françaises même sur une ligne étrangère. Le pays du numéro n’est pas une preuve de la localisation physique ni de l’identité de l’appelant.

## Protection

- Base commune : blocage manuel, liste blanche, favoris/contacts selon le profil, mode Ne pas déranger, horaires et blocage communautaire selon les réglages existants.
- Les préfixes et expressions françaises, FINESS, les exceptions professionnelles françaises et les annuaires de confiance français ne s’appliquent plus à un numéro étranger traité comme s’il était français.
- Le lot de règles françaises porte actuellement sur les numéros complets +33 et les codes courts dans un contexte FR. Les plans ultramarins distincts ne disposent pas encore d’une adaptation spécifique dans cette bêta et utilisent la protection générique.
- En l’absence de pays connu, un numéro local n’est pas transformé arbitrairement en +33. Les profils et horaires continuent toutefois de s’appliquer : le profil Maison bloque déjà les inconnus, même sans règle de démarchage locale.
- Les numéros d’urgence reconnus pour la région sont exclus du blocage automatique dans le service de filtrage. Les appels sortants continuent à être laissés à Android. Aucune garantie d’authenticité d’un numéro présenté par l’appelant n’est ajoutée.
- Un échec d’interprétation ou une longueur inhabituelle ne suffit plus à déclarer un numéro frauduleux. Les protections demandées par l’utilisateur restent applicables.
- Aucun paquet de préfixes de démarchage allemand, belge, turc, etc. n’est ajouté dans cette livraison.

## Numéros et compatibilité

La normalisation utilise la bibliothèque hors ligne [libphonenumber 9.0.39](https://github.com/google/libphonenumber/releases/tag/v9.0.39), sans service Google Play et sans requête distante pour analyser un numéro. Les préfixes d’accès international et les zéros significatifs sont gérés par ses métadonnées. Le format E.164 sert aux nouveaux numéros complets enregistrés.

Les nouveaux ajouts aux favoris, à la liste blanche, au blocage manuel et aux signalements exigent un numéro interprétable ; sinon un message demande de choisir le pays ou d’utiliser + et l’indicatif. La normalisation ne prouve pas que le numéro est attribué ni joignable.

- Les anciens favoris français à neuf chiffres conservent leurs clés.
- Les anciennes entrées nationales des listes et notes sont traitées comme des données françaises héritées, sans être réinterprétées selon un nouveau pays. Les fichiers existants ne sont pas supprimés. Si des numéros étrangers avaient été enregistrés au format national avec les anciennes versions françaises, vérifier ces anciennes entrées et les réenregistrer en +indicatif : leur pays d’origine n’était pas stocké et ne peut pas être reconstruit avec certitude.
- Le journal utilise le compte téléphonique lorsqu’il est identifiable, sépare les numéros non résolus par compte et affiche les numéros complets interprétés. Les anciens comptes absents/renommés peuvent rester non résolus. La suppression d’un groupe utilise désormais les identifiants de ses appels afin de rester correcte quand le format affiché change.
- Les nouvelles clés étrangères des signalements communautaires sont internationales ; la clé nationale française existante est conservée côté serveur pour éviter de diviser les compteurs. Les anciennes entrées françaises du Top sont présentées avec +33. Aucune migration ni modification du serveur Supabase n’a été effectuée ici : sa fonction RPC et ses contraintes doivent accepter les nouveaux numéros internationaux. La protection communautaire reste limitée aux données disponibles et au fonctionnement de cache existant.
- Les liens WhatsApp utilisent le véritable numéro international au lieu d’ajouter systématiquement 33.
- Les réglages de pays sont inclus dans les sauvegardes existantes via `phonezen_prefs`. Ils sont liés aux comptes Android, pas simplement à la position SIM1/SIM2. Vérifier les choix après remplacement de SIM, changement de téléphone ou restauration sur un autre appareil.

## Vérifications réalisées

1. Compilation JVM autonome avec Kotlin 2.1.10 des modules `InternationalNumbers`, `CountrySelection`, `VoiceDialHelper` et de leurs trois classes de tests.
2. JUnit : **25 tests réussis**. Cas couverts : FR local/+33/0033, indicatif explicite prioritaire, mobile allemand 0162, séparation FR/Italie, zéro italien, pays inconnu, préfixe 011 américain, chiffres arabes, anciens favoris/listes FR, espace de clés communautaires, codes courts, urgences, données invalides et ordre de priorité des pays en double SIM. Aucun appel ou SMS réel n’a été émis.
3. `python3 tools/check_translations.py` : réussi, **323 ressources par langue dans les 13 langues**.
4. `./gradlew :app:assemblePreviewDebug :app:testPreviewDebugUnitTest --console=plain` : tenté, mais le wrapper échoue au téléchargement de Gradle 8.7 (`Network is unreachable`). Aucun SDK Android n’est configuré dans cet environnement. Les tests JVM ci-dessus ont été exécutés séparément avec des dépendances téléchargées depuis Maven ; cela ne valide pas la compilation complète Android/Compose, Room ou les services.

La suite Android complète, les tests `RegressionTest`, le parcours sur appareil et la génération de l’APK restent à faire. Aucun APK n’est inclus.

## Compiler et tester sur téléphone

Conserver la bêta 7 validée et faire une sauvegarde des réglages/listes. Ouvrir ce projet séparément dans Android Studio, sélectionner **previewDebug** et compiler. APK attendu : `app/build/outputs/apk/preview/debug/app-preview-debug.apk`. Utiliser la même signature de test pour installer la mise à jour en conservant les données.

Aucun identifiant Supabase personnel ni fichier `local.properties` n’est livré. La nouvelle dépendance libphonenumber sera téléchargée par Gradle.

### Parcours conseillé

1. En français puis en anglais : vérifier Pays et protection, les noms de pays localisés, la recherche et le retour à Automatique. Le pays ne doit pas changer avec la langue.
2. Vérifier les anciens favoris, la liste blanche, les blocages manuels et les notes. Vérifier suppression d’une note et d’un groupe d’appels de test.
3. Avec chaque SIM : vérifier le pays affiché et les appels contrôlés d’un correspondant connu. Tester séparément la sélection manuelle par ligne puis la remettre sur Automatique.
4. Pour observer la protection générique, utiliser le profil Travail, sans horaires ni DND pendant l’essai : un inconnu peut sinon être bloqué volontairement par Maison/Vacances. Vérifier ensuite que Maison, Vacances et les horaires conservent leur comportement.
5. Vérifier un contact enregistré en +indicatif étranger, l’ajout/retrait de la liste blanche, le blocage/déblocage manuel, le journal et le lien WhatsApp. Utiliser uniquement des numéros de test maîtrisés ; les exemples des tests unitaires ne sont pas à appeler.
6. Avec deux SIM de pays différents, vérifier que le pays de secours en Automatique est non déterminé et qu’un numéro local ambigu demande un pays avant enregistrement. Un numéro en +indicatif doit rester utilisable.
7. Si Supabase est configuré : tester un signalement maîtrisé autorisé et vérifier que le serveur accepte et restitue la clé internationale. Ne pas signaler un numéro réel légitime pour cet essai.
8. Contrôler les appels entrants/sortants, plein écran/compact/mini, double appel et notifications déjà validés.

Les textes encore codés directement en français dans les modules non traduits restent hors de ce lot. Les profils/explications hérités faisant référence aux services français gardent cette portée ; la carte Pays et protection précise la protection internationale disponible.

## Références techniques

- [libphonenumber : FAQ de normalisation](https://github.com/google/libphonenumber/blob/master/FAQ.md).
- [Android : TelephonyManager et compte téléphonique](https://developer.android.com/reference/android/telephony/TelephonyManager#createForPhoneAccountHandle(android.telecom.PhoneAccountHandle)). Cette API est disponible à partir d’Android 8 ; sur Android 7, le code utilise l’identifiant de souscription fourni dans les extras lorsqu’il existe, sinon le réglage manuel de secours.
