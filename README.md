# 📱 PhoneZen

> Application téléphone Android libre, intelligente et respectueuse de votre vie privée.

![Android](https://img.shields.io/badge/Android-8.0%2B-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)
![License](https://img.shields.io/badge/License-GPL%20v3-orange)
![Version](https://img.shields.io/badge/Version-1.2-cyan)

---

## 📸 Présentation

PhoneZen est une application téléphone open-source pour Android qui remplace l'application téléphone native. Elle intègre un moteur anti-spam basé sur les règles ARCEP (Loi Naegelen), une liste participative communautaire et un double thème.

---

## ✨ Fonctionnalités

### 🛡️ Protection anti-spam
- Blocage automatique des numéros de démarchage (préfixes ARCEP / Loi Naegelen)
- Blocage des numéros privés/masqués
- Mode **Ne pas déranger** avec liste blanche
- Blocage programmé par **horaires** (ex: 22h → 8h)

### 🌍 Liste participative communautaire
- Signalez un numéro indésirable en 2 clics depuis le journal
- Badge ⚠️ sur les numéros signalés par la communauté
- Blocage automatique au-delà de **10 signalements**
- Notification lors d'un blocage communautaire
- TTL glissant **40 jours** — les signalements expirés sont supprimés automatiquement
- Écran **Top numéros signalés** dans les Réglages

### 📋 Journal des appels
- Historique groupé par numéro avec compteur
- Filtres : Tous, Manqués, Entrants, Sortants, Bloqués
- Recherche par nom ou numéro
- Suppression par glissement (swipe)
- Export **CSV**

### 👥 Contacts
- Favoris en tête de liste
- Ajout direct à la **liste blanche** depuis les contacts (icône 🛡)
- Affichage du nom du contact dans la liste blanche
- Recherche rapide

### 📞 Clavier intelligent
- Codes **USSD/MMI** supportés (`*#06#`, `*21*`, etc.)
- Messagerie vocale **auto-détectée** selon l'opérateur
  - 35 opérateurs/MVNO français couverts (Orange, SFR, Free, Bouygues + MVNO)
- Touche **1 — RÉP** : appui long pour accéder directement au répondeur

### 🎨 Double thème
| Cyber Dark | Zen Clair |
|-----------|-----------|
| Fond noir haute contraste | Fond clair inspiré de l'icône |
| Accents cyan & orange | Accents vert émeraude & bleu ciel |

---

## 🔧 Stack technique

| Composant | Technologie |
|-----------|------------|
| Langage | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM |
| Base de données locale | Room |
| Persistance | DataStore |
| Images | Coil |
| Backend communautaire | Firebase Firestore |
| Build | Gradle KTS |

---

## 📋 Permissions requises

| Permission | Utilisation |
|-----------|------------|
| `READ_CALL_LOG` / `WRITE_CALL_LOG` | Journal des appels |
| `READ_CONTACTS` | Affichage des noms de contacts |
| `CALL_PHONE` | Passer des appels |
| `READ_PHONE_STATE` | Détection de l'opérateur |
| `ANSWER_PHONE_CALLS` | Gestion des appels entrants |
| `POST_NOTIFICATIONS` | Notifications blocage communautaire (Android 13+) |

---

## 🚀 Installation

### Depuis GitHub Releases
1. Télécharge le dernier APK depuis [Releases](https://github.com/souffly007/PhoneZen/releases)
2. Active **"Sources inconnues"** dans les paramètres Android
3. Installe l'APK
4. Définis PhoneZen comme **application téléphone par défaut**
5. Définis PhoneZen comme **service de filtrage des appels**

### Compiler depuis les sources
```bash
git clone https://github.com/souffly007/PhoneZen.git
cd PhoneZen
./gradlew assembleRelease
```

> ⚠️ Un fichier `google-services.json` valide est nécessaire pour les fonctionnalités Firebase (liste participative). Crée ton propre projet Firebase et place le fichier dans `app/`.

---

## 🏗️ Architecture du projet

```
app/
├── data/
│   ├── local/          # Room — base de données locale
│   ├── model/          # Modèles de données
│   └── repository/     # ReportRepository (Firebase)
├── service/            # CallScreeningService
├── ui/
│   ├── screens/        # Écrans Compose
│   └── theme/          # Thèmes et couleurs
├── utils/              # SpamDetector, PhoneUtils
└── viewmodel/          # MainViewModel, ThemeViewModel
```

---

## 🤝 Contribuer

Les contributions sont les bienvenues !

1. Fork le projet
2. Crée une branche (`git checkout -b feature/ma-fonctionnalite`)
3. Commit tes changements (`git commit -m 'Ajout de ma fonctionnalité'`)
4. Push (`git push origin feature/ma-fonctionnalite`)
5. Ouvre une **Pull Request**

---

## 📜 Licence

Ce projet est sous licence **GPL v3** — voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

## 👨‍💻 Auteur

**Franck R-F** — [@souffly007](https://github.com/souffly007)

---

## 🙏 Remerciements

- [ARCEP](https://www.arcep.fr) — pour les données sur les préfixes de démarchage (Loi Naegelen)
- [signal-spam.fr](https://www.signal-spam.fr) — pour la sensibilisation au spam téléphonique
