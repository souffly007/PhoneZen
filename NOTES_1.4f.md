# PhoneZen 1.4f-beta1 — version de validation

Base : version 1.4e, commit cd4506d4fbf29ce43967e83620333723f14da7f3.

## Répondeur et SIM

- Lecture du répondeur Android par abonnement, suppression des numéros français supposés et du repli arbitraire à 123.
- Bouton « Configurer le répondeur / SIM » dans le clavier : numéro manuel ou retour à la détection automatique.
- Le numéro manuel est lié à l’abonnement et non à l’emplacement physique. Il n’est volontairement pas restauré sur une autre SIM depuis une sauvegarde.
- Liste des SIM rafraîchie au retour dans le clavier, et revérification avant un appel au répondeur.
- Identification du compte téléphonique par API Android à partir d’Android 11. Sur les anciennes versions, si la correspondance est indisponible, confirmation explicite de la ligne ; plus de repli silencieux sur la SIM par défaut.

## Écran d’appel

- Plein écran, compact et mini sont hébergés par une activité pouvant apparaître au-dessus du verrouillage Android, sans supprimer ce verrouillage.
- Compact en bas, mini en haut ; agrandissement réel en plein écran sans boucle de réouverture du mode compact.
- Le capteur de proximité dépend du service d’appel : écran éteint à l’oreille uniquement pendant une communication active sur l’écouteur. Il est libéré sur haut-parleur, Bluetooth, casque ou fin d’appel.
- Pas de maintien permanent de l’écran allumé pendant la conversation. L’écran reste allumé pendant la sonnerie.
- Retour demandé à l’accueil Android lorsque le dernier appel se termine. Le verrouillage sécurisé reste respecté : un téléphone verrouillé peut montrer son écran de verrouillage au lieu de dévoiler le bureau.
- Notification d’appel entrant avec intention plein écran. Android 14+ : bouton d’autorisation dans Réglages > Affichage des appels. Les restrictions spécifiques Xiaomi/Samsung et les réglages utilisateur restent à tester.
- Le raccrochage d’un appel ne force plus l’arrêt du Bluetooth ni le retour du système audio au mode normal alors qu’un autre appel continue. Les noms de contacts sont rafraîchis lors du changement d’interlocuteur.
- Les actions de réponse ciblent l’appel qui sonne lors d’un double appel ; la fermeture d’un appel ne ferme pas l’interface s’il en reste un autre.

## Consolidation

- Le service de filtrage décide du blocage ; suppression du second filtrage contradictoire dans le service d’interface d’appel. Il faut activer PhoneZen comme application téléphone ET service de filtrage.
- Priorités : liste blanche utilisateur / santé activée, liste noire manuelle, puis moteur de profils et communauté.
- Favoris PhoneZen reconnus par les profils ; option santé respectée dans le détecteur.
- Seuil communautaire aligné à 10 et expiration vérifiée ; erreurs HTTP de signalement remontées au lieu d’être annoncées comme succès.
- Retour du profil Vacances appliqué à la prochaine lecture du profil après expiration.
- Sauvegarde v2 : liste noire manuelle et paramètres inclus. Lecture des anciennes sauvegardes conservée. Validation des données avant mutation et transaction pour les tables Room. Préférences Android et Room ne constituent pas une transaction inter-stockages ; un échec d’écriture des préférences est signalé.
- Les numéros internationaux explicites ne sont plus fusionnés sur leurs neuf derniers chiffres. Les favoris français existants conservent leur clé. Les anciens favoris étrangers fondés sur une clé tronquée peuvent nécessiter une nouvelle sélection.
- Gardes de version sur la direction des appels et silence du filtrage ; désucrage des API Java temporelles pour Android ancien.
- Cache santé chargé de façon asynchrone et partagé ; réponse de secours autorisant l’appel en cas de filtrage trop lent. La liste santé peut encore être en chargement au tout premier appel.
- Trafic HTTP clair désactivé.

## Installation de l’APK de test

L’APK « PhoneZen Test » utilise le package `fr.bonobo.phonezen.test` et une signature de test. Elle s’installe à côté de PhoneZen ; ne désinstallez pas votre application habituelle.

1. Installez l’APK, puis choisissez **PhoneZen Test** comme téléphone et filtre d’appels.
2. Accordez les permissions utiles. Sur Android 14+, vérifiez l’autorisation d’affichage plein écran.
3. Réglez compact ou mini, verrouillez le téléphone et faites-vous appeler depuis un autre appareil.
4. Testez la réponse, la proximité, le haut-parleur, le Bluetooth, le raccrochage local et distant, puis les deux SIM.
5. Pour revenir à l’ancienne version, remettez **PhoneZen** comme téléphone et filtre par défaut.

**Configuration communautaire :** les paramètres Supabase privés de compilation ne sont pas présents dans le dépôt. L’APK produite ici ne se connecte donc pas à votre serveur communautaire. Le filtrage local reste utilisable. Pour retrouver votre configuration, compilez les sources avec vos valeurs `supabase_url` et `supabase_publishable_key` habituelles dans `local.properties`.

Pour une mise à jour de votre application existante, utilisez la variante `fdroidRelease` (package original) et signez dans Android Studio avec votre clé habituelle. La signature de test ne remplace pas cette clé.

## Limites de cette version

Cette version est destinée à des tests personnels avant diffusion aux testeurs. Une compilation réussie ne prouve pas le comportement du modem, de la SIM, du verrouillage ou du capteur de proximité.

Ce n’est pas une internationalisation complète : les libellés, les profils de numérotation locaux et les bases de préfixes restent principalement français. La messagerie vocale visuelle demeure désactivée. La réponse SMS du mode conduite n’est pas réparée dans cette version. Le cache communautaire reste limité aux numéros vérifiés depuis le journal. La sauvegarde exportée reste un JSON non chiffré. Les politiques serveur Supabase n’ont pas été auditées.


## Vérifications réalisées

- Compilation `assemblePreviewDebug` réussie avec JDK 17 et SDK Android 35.
- Six tests unitaires réussis, zéro erreur et zéro échec : séparation des numéros étrangers, compatibilité des favoris français, préfixes internationaux, numéros courts, expiration et seuil de suspicion des signalements.
- Contrôle du manifeste compilé : package `fr.bonobo.phonezen.test`, libellé « PhoneZen Test », version `1.4f-beta1-test`, versionCode 2, minSdk 24, targetSdk 35.
- Aucun essai sur téléphone réel ; les scénarios SIM, Bluetooth, capteur et verrouillage doivent être validés avant diffusion.

SHA-256 de l’APK : `a113f26562b63581caf76330cf07a6ea14878e0c2fbadd9edbabd20acf043ebc`
