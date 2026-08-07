Je développe PhoneZen, une application Android de gestion d'appels téléphoniques (blocage spam, liste blanche hôpitaux, etc.).

# Architecture actuelle
fr.bonobo.phonezen/
├── service/
│ ├── CallManager.kt ← Actuel gestionnaire d'appels (à améliorer)
│ ├── PhoneZenInCallService.kt ← Service d'appel actif
│ └── PhoneZenCallScreeningService.kt ← Filtrage entrant
├── ui/screens/
│ ├── InCallActivity.kt ← Écran d'appel actuel
│ └── InCallScreen.kt ← UI Compose de l'appel
├── viewmodel/
│ └── InCallViewModel.kt ← State Flow de l'appel
└── data/model/
└── CallState.kt ← Enum des états (existant)


# Objectif

Implémenter un **double appel (Call Waiting)** digne de Google Phone :

## Comportement attendu

1. **Je suis en appel actif** (entrant ou sortant)
2. **Un second appel arrive**
3. **Afficher un écran overlay** (sans couper le 1er appel) avec :
    - Bouton "Répondre" → accepte le 2e, met le 1er en HOLD automatiquement
    - Bouton "Refuser" → rejette le 2e, reste sur le 1er
    - Bouton "Rester sur l'appel 1" → ignore le 2e sans rejeter (persiste en attente)

4. **Après acceptation du 2e appel** :
    - Afficher un bouton "Basculer" (swap) entre les deux appels
    - Possibilité de terminer l'appel actif et revenir automatiquement à l'autre
    - Si appel 2 terminé → appel 1 reprend automatiquement

5. **Zéro coupure audio** pendant toutes ces opérations

## Contraintes techniques

- Android 10+ (API 29+)
- Utilisation de **TelecomManager** et **InCallService** (pas VoIP custom)
- Interface **Jetpack Compose** (InCallScreen déjà existante)
- Architecture **MVVM** (ViewModel + StateFlow)

# Ce que je veux que tu génères

En t'appuyant sur mon architecture existante, génère :

1. **CallManager.kt** (amélioré) : Logique double appel avec états ACTIVE/HOLD/INCOMING_SECOND
2. **InCallViewModel.kt** (amélioré) : Expose les actions (acceptSecondCall, rejectSecondCall, swapCalls, endCall)
3. **IncomingSecondCallOverlay.kt** : Nouvel écran Compose overlay pour le second appel
4. **CallAudioManager.kt** : Gestion audio (focus, mixage) pour éviter les coupures
5. **Modifications nécessaires dans PhoneZenInCallService.kt** pour recevoir l'événement du second appel

## Format attendu

- Code Kotlin complet de chaque fichier
- Pas de pseudo-code, du vrai code fonctionnel
- Compatible avec l'existant (ne pas casser le blocage spam)
- Gestion des permissions (Notification, Display over other apps si besoin)

## Rappel des états CallState existants

Adapter ou compléter mon enum actuel avec : IDLE, ACTIVE, HOLD, INCOMING_SECOND, DISCONNECTED