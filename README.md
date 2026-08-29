# NoxoClaim

> **Protection de terrains moderne pour Paper — simple pour les joueurs, complète pour les administrateurs.**

NoxoClaim est un plugin de protection de terrains développé par **NoxoDEV** pour les serveurs Minecraft **Paper 26.x**. Il permet aux joueurs de protéger leurs constructions, gérer leurs membres de confiance, définir des règles de protection et visualiser les claims directement en jeu.

[![Minecraft](https://img.shields.io/badge/Minecraft-Paper%2026.x-1f1f1f?style=flat-square)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-see%20repository-blue?style=flat-square)](https://github.com/Noxo123/NoxoClaim)

---

## ✨ Fonctionnalités

### 🛡️ Protection

- Claims rectangulaires persistants.
- Sélection de deux coins avec la wand NoxoClaim.
- Protection des blocs contre les joueurs non autorisés.
- Protection des interactions avec les conteneurs et blocs protégés.
- Gestion des membres de confiance.
- Support des flags de protection : PVP, explosions, feu, mob-griefing et entrée.
- Suppression de ses propres claims avec `/uclaim` ou `/claim unclaim`.

### 🗺️ Visualisation

- `/claim voir` pour visualiser les frontières des claims autour du joueur.
- Distance de visualisation configurable.
- `/claim map` pour ouvrir la carte des claims.
- Interface GUI intégrée sans mod client obligatoire.
- Conception prévue pour pouvoir évoluer vers une compatibilité avec des solutions de cartographie côté client lorsque celles-ci sont disponibles.

### 🏠 Homes

- Définition d'un home par claim.
- Téléportation différée vers le home.
- Gestion des homes via `/chome`.

### 💰 Économie

- Intégration optionnelle avec **Vault**.
- Les claims payants peuvent utiliser l'économie fournie par un plugin compatible Vault.
- Le plugin reste fonctionnel sans système économique.

### ⚙️ Administration

- Commandes administrateur dédiées.
- Sauvegarde et rechargement de la configuration.
- Inspection et suppression de claims.
- Vérification manuelle des mises à jour avec `/claimadmin update`.
- Canal de mise à jour basé sur les commits GitHub.
- Vérification SHA-256 des artefacts téléchargés avant préparation d'une mise à jour.

### 🔄 Mise à jour

NoxoClaim utilise un canal de distribution séparé afin de ne pas dépendre directement des GitHub Releases.

Le fonctionnement est volontairement sécurisé :

1. GitHub Actions compile le plugin.
2. Le build est associé au commit Git exact.
3. Le workflow publie le JAR dans la branche `updates`.
4. `update.json` décrit les builds disponibles pour Paper 26.2 et 26.1.2.
5. NoxoClaim compare le commit installé au commit publié.
6. Le JAR compatible est téléchargé dans un fichier temporaire.
7. Le SHA-256 est vérifié.
8. Le fichier n'est préparé pour l'installation qu'après validation.
9. Le serveur peut ensuite être redémarré pour appliquer la mise à jour.

> **Important :** NoxoClaim ne remplace pas le JAR actuellement chargé pendant son exécution. La mise à jour est préparée puis appliquée au redémarrage du serveur.

---

## 📋 Commandes joueur

| Commande | Description |
|---|---|
| `/claim` | Ouvre le menu principal NoxoClaim. |
| `/claim help` | Affiche l'aide des commandes. |
| `/claim wand` | Obtient l'outil de sélection. |
| `/claim create` | Crée un claim à partir de la sélection actuelle. |
| `/claim menu` | Ouvre le menu de gestion. |
| `/claim map` | Ouvre la carte des claims. |
| `/claim voir [distance]` | Affiche les frontières des claims dans la zone de visualisation. |
| `/claim list` | Liste les claims du joueur. |
| `/claim info` | Affiche les informations du claim actuel. |
| `/claim trust <joueur>` | Ajoute un membre de confiance. |
| `/claim untrust <joueur>` | Retire un membre de confiance. |
| `/claim flags` | Consulte les flags du claim. |
| `/claim flags <flag> <true\|false>` | Modifie un flag. |
| `/claim sethome` | Définit le home du claim actuel. |
| `/claim home` | Téléporte vers le home du claim. |
| `/claim delete` | Supprime le claim actuel. |
| `/claim unclaim` | Retire le claim actuel. |
| `/uclaim` | Raccourci pour retirer le claim actuel. |
| `/hclaim ...` | Alias de `/claim`. |
| `/map` | Raccourci vers la carte NoxoClaim. |
| `/chome <nom\|list>` | Gestion des homes de claims. |

> La syntaxe exacte peut évoluer avec la version installée. Utilisez `/claim help` sur le serveur pour obtenir l'aide générée par la version actuelle.

---

## 🔧 Commandes administrateur

| Commande | Description |
|---|---|
| `/claimadmin list` | Liste les claims présents sur le serveur. |
| `/claimadmin info <uuid>` | Affiche les informations d'un claim. |
| `/claimadmin delete <uuid>` | Supprime un claim précis. |
| `/claimadmin deleteall <uuid>` | Supprime tous les claims associés à un joueur. |
| `/claimadmin save` | Force la sauvegarde des données. |
| `/claimadmin reload` | Recharge la configuration. |
| `/claimadmin update` | Vérifie immédiatement le canal de mise à jour. |
| `/claimadmin debug` | Affiche les informations utiles au diagnostic. |

### Permission recommandée

```text
noxoclaim.admin
```

La permission `noxoclaim.admin.update` peut être utilisée pour autoriser uniquement la vérification/préparation des mises à jour.

---

## 🔐 Permissions

| Permission | Défaut | Utilisation |
|---|---:|---|
| `noxoclaim.admin` | OP | Administration complète. |
| `noxoclaim.admin.update` | OP | Vérification des mises à jour. |
| `noxoclaim.admin.reload` | OP | Rechargement de la configuration. |
| `noxoclaim.bypass` | OP | Ignore les protections NoxoClaim. |
| `noxoclaim.claim` | Non | Autorise la création de claims. |
| `noxoclaim.autoclaim` | Non | Autorise le claim automatique. |
| `noxoclaim.menu` | Oui | Accès au menu. |
| `noxoclaim.map` | Oui | Accès à la carte. |
| `noxoclaim.view` | Oui | Visualisation des claims. |
| `noxoclaim.unclaim` | Oui | Suppression de ses claims. |
| `noxoclaim.claim.3` | Non | Jusqu'à 3 claims. |
| `noxoclaim.claim.10` | Non | Jusqu'à 10 claims. |
| `noxoclaim.claim.25` | Non | Jusqu'à 25 claims. |
| `noxoclaim.claim.50` | Non | Jusqu'à 50 claims. |

---

## 📦 Installation

### 1. Prérequis

- Un serveur **Paper** compatible.
- **Java 25** pour les builds actuels.
- **Vault** uniquement si vous souhaitez utiliser l'économie.
- Un plugin d'économie compatible avec Vault si les claims payants sont activés.

### 2. Installation

1. Téléchargez le JAR correspondant à votre version de Paper.
2. Placez-le dans `plugins/`.
3. Redémarrez le serveur.
4. Vérifiez avec :

```text
/plugins
```

5. Configurez NoxoClaim dans `plugins/NoxoClaim/`.

### 3. Première utilisation

En jeu :

```text
/claim wand
```

Sélectionnez les deux coins de votre zone, puis créez le claim avec :

```text
/claim create
```

---

## 🗺️ Système de visualisation

NoxoClaim distingue volontairement deux niveaux de visualisation :

### Sans mod client

Le joueur peut utiliser les outils intégrés au plugin :

```text
/claim voir
/claim voir 32
/claim map
```

Aucun mod n'est requis côté joueur.

### Avec un mod client compatible

L'architecture de NoxoClaim peut être étendue pour envoyer des informations de claims à un client équipé d'un mod de cartographie compatible.

Le serveur reste toutefois utilisable normalement pour les joueurs qui ne possèdent aucun mod.

---

## 💾 Données et persistance

Les claims sont persistants et sont sauvegardés par NoxoClaim afin de survivre aux redémarrages du serveur.

Avant une migration importante ou une mise à jour majeure, il est recommandé de sauvegarder :

```text
plugins/NoxoClaim/
```

En particulier, conservez les fichiers de données des claims et votre configuration personnalisée.

---

## 🔄 Système de mise à jour technique

Le système de mise à jour est basé sur une branche GitHub dédiée :

```text
updates
├── update.json
└── assets/
    ├── NoxoClaim-Paper-26.2-<commit>.jar
    ├── NoxoClaim-Paper-26.2-<commit>.jar.sha256
    ├── NoxoClaim-Paper-26.1.2-<commit>.jar
    └── NoxoClaim-Paper-26.1.2-<commit>.jar.sha256
```

Le manifeste contient notamment :

```json
{
  "plugin": "NoxoClaim",
  "commit": "<40 caractères SHA-1>",
  "downloads": {
    "26.2": {
      "file": "...jar",
      "sha256": "..."
    }
  }
}
```

Cela permet au plugin de déterminer précisément quel build correspond au commit publié.

---

## 🏗️ Compilation depuis les sources

Le projet utilise Gradle Kotlin DSL et compile actuellement avec Java 25.

### Linux / macOS

```bash
./gradlew clean test build
```

### Windows

```powershell
.\gradlew.bat clean test build
```

Le JAR est généré dans :

```text
build/libs/
```

### Build pour une version Paper précise

Exemple Paper 26.2 :

```bash
gradle clean test build -PpaperApiVersion="26.2.build.+"
```

Exemple Paper 26.1.2 :

```bash
gradle clean test build -PpaperApiVersion="26.1.2.build.+"
```

---

## 🤖 GitHub Actions

Le workflow de build :

- compile les variantes Paper supportées ;
- exécute les tests ;
- vérifie la présence du JAR ;
- calcule les SHA-256 ;
- génère le manifeste de mise à jour ;
- publie les artefacts dans la branche `updates`.

Le workflow est déclenché automatiquement lors d'un push sur `main` et peut également être lancé manuellement.

---

## 🧪 Tests

NoxoClaim possède une suite de tests unitaires couvrant notamment les objets métier de claims et leurs flags.

Pour lancer les tests :

```bash
./gradlew test
```

Pour compiler uniquement après les tests :

```bash
./gradlew clean test build
```

---

## 🧩 Compatibilité

| Plateforme | Support |
|---|---|
| Paper 26.2 | ✅ Build dédiée |
| Paper 26.1.2 | ✅ Build dédiée |
| Java 25 | ✅ |
| Vault | 🟡 Optionnel |
| LuckPerms | 🟡 Détection/intégration selon configuration |
| Mod client obligatoire | ❌ Non |

> Les versions Minecraft/Paper non listées ne sont pas garanties. Utilisez une build explicitement publiée pour votre version.

---

## ⚠️ Dépannage

### `Aucune économie Vault détectée`

Ce message signifie que Vault est chargé mais qu'aucun fournisseur d'économie compatible n'est disponible.

Si vous n'utilisez pas de claims payants, vous pouvez ignorer ce message.

### `aucun artefact dans le manifeste`

Le serveur a trouvé `update.json`, mais celui-ci ne contient aucun build exploitable pour NoxoClaim. Vérifiez que le workflow GitHub Actions a terminé correctement et que la branche `updates` contient bien `assets/`.

### `téléchargement HTTP 404`

Le manifeste référence un fichier qui n'est pas accessible à l'emplacement attendu. Vérifiez la branche `updates`, le nom exact de l'artefact et le contenu de `update.json`.

### Le plugin indique qu'il est à jour

Le système compare le commit embarqué dans le JAR avec celui publié dans le canal `updates`. Si les deux commits correspondent, aucune mise à jour n'est nécessaire.

---

## 📁 Structure principale

```text
NoxoClaim/
├── .github/
│   └── workflows/
│       └── build.yml
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
├── build.gradle.kts
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

---

## 🔒 Sécurité

Le système de mise à jour ne doit pas faire confiance aveuglément à un fichier distant.

NoxoClaim applique plusieurs contrôles avant de préparer un artefact :

- le commit doit être présent dans le manifeste ;
- le nom du fichier doit correspondre au commit demandé ;
- les chemins dangereux sont refusés ;
- le téléchargement est effectué dans un fichier temporaire ;
- la taille du JAR est contrôlée ;
- le SHA-256 doit correspondre exactement au manifeste ;
- le fichier vérifié est ensuite déplacé vers le dossier de mise à jour.

---

## 🛠️ Développement

Les contributions et corrections peuvent être proposées directement sur le dépôt GitHub.

Avant de soumettre une modification :

```bash
./gradlew clean test build
```

Vérifiez également que :

- les nouvelles fonctionnalités ont des tests lorsque cela est pertinent ;
- les commandes restent cohérentes avec `/claim help` ;
- les messages joueurs restent en français ;
- les changements de compatibilité Paper sont documentés ;
- aucune donnée de serveur ou information sensible n'est ajoutée au dépôt.

---

## 📜 Licence

Consultez le fichier `LICENSE` du dépôt pour connaître les conditions d'utilisation et de redistribution de NoxoClaim.

---

## 👤 Auteur

**NoxoDEV**

Projet : [github.com/Noxo123/NoxoClaim](https://github.com/Noxo123/NoxoClaim)

---

## ⭐ NoxoClaim

Protection des constructions, gestion des claims, visualisation et administration réunies dans un plugin Paper pensé pour rester simple à utiliser tout en offrant une base technique évolutive.
