# NoxoClaim

> 🛡️ Système moderne de protection de terrains pour Paper — simple pour les joueurs, complet pour les administrateurs.

[![Minecraft](https://img.shields.io/badge/Minecraft-Paper%2026.x-1f1f1f?style=flat-square)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Build](https://img.shields.io/github/actions/workflow/status/Noxo123/NoxoClaim/build.yml?branch=main&style=flat-square&label=build)](https://github.com/Noxo123/NoxoClaim/actions)
[![Version](https://img.shields.io/badge/version-2.0.1-blue?style=flat-square)](https://github.com/Noxo123/NoxoClaim)

NoxoClaim est un plugin de protection et de gestion de claims développé par **NoxoDEV** pour les serveurs **Paper 26.x**. Le plugin fournit une protection des constructions, une gestion des membres et des règles de protection, une carte interactive en GUI, des homes, une intégration économique optionnelle, un HUD serveur et un système de mise à jour vérifié par SHA-256.

---

## ✨ Fonctionnalités

### 🛡️ Claims & protection

- Création rapide d'un claim sur le **chunk actuel** avec `/claim`.
- Création de claims depuis la carte interactive.
- Modèle de claim rectangulaire côté données, avec indexation par chunks pour les recherches rapides.
- Protection configurable des blocs et interactions.
- Protection des conteneurs, portes, boutons, redstone, entités et projectiles selon la configuration.
- Membres de confiance avec `/claim trust` et `/claim untrust`.
- Flags de protection :
  - `PVP`
  - `EXPLOSIONS`
  - `FIRE`
  - `MOB_GRIEFING`
  - `FLUIDS`
  - `ENTRY`
- Limitation du nombre de claims par joueur.
- Option d'auto-claim à l'entrée dans un chunk libre.
- Permission `noxoclaim.bypass` pour ignorer les protections.

> **Important :** l'interface joueur actuelle crée principalement des claims d'un chunk à la fois. Le moteur de données prend néanmoins en charge des rectangles et empêche les chevauchements.

### 🗺️ Carte & visualisation

- `/claim map` et `/map` ouvrent une carte en inventaire.
- La carte affiche les chunks libres, les claims du joueur et les claims des autres joueurs.
- Un clic sur un chunk libre peut créer directement le claim.
- Un clic sur son propre claim ouvre la gestion des protections.
- Un claim appartenant à un autre joueur peut être visualisé sans permettre sa modification.
- Rafraîchissement automatique de la carte configurable.
- `/claim voir [rayon]` affiche temporairement les frontières des claims.
- `/claim voir off` désactive la visualisation.
- Aucun mod client n'est requis pour les fonctions GUI et la visualisation serveur.

### 🏠 Homes

- Un point de téléportation peut être enregistré pour chaque claim.
- `/claim sethome` définit le home du claim actuel.
- `/claim home` téléporte vers le home.
- `/chome <nom>` permet d'accéder à un claim enregistré par son nom.
- Si aucun home n'est défini, NoxoClaim calcule un point de téléportation dans le claim.
- Téléportation différée et annulation lors d'un déplacement configurables.

### 💰 Économie

- Intégration optionnelle avec **Vault**.
- Coût configurable par chunk (`economy.cost-per-chunk`).
- Si aucun fournisseur d'économie Vault n'est disponible, les claims payants sont refusés mais le plugin continue de fonctionner pour les autres fonctions.
- Valeur par défaut : `500.0` par chunk.

### 🖥️ HUD serveur

NoxoClaim intègre **HUDEngine** pour fournir un HUD/minimap côté serveur.

- Installation automatique de HUDEngine configurable.
- Version HUDEngine actuellement ciblée : `1.0.0`.
- Vérification SHA-256 du JAR HUDEngine avant son installation.
- HUD et minimap configurables dans `config.yml`.
- Administration via `/claimadmin hud ...`.
- Aucun mod client supplémentaire n'est requis par NoxoClaim pour ce système ; le plugin HUDEngine utilise son propre mécanisme côté serveur.

### ⚙️ Administration

- Dashboard administrateur en GUI.
- Liste et inspection des claims.
- Suppression d'un claim par UUID.
- Suppression de tous les claims d'un joueur par UUID.
- Sauvegarde forcée.
- Rechargement de la configuration.
- Commandes de diagnostic et de statut.
- Gestion de l'intégration HUDEngine.
- Vérification manuelle des mises à jour.

### 🔄 Mises à jour sécurisées

NoxoClaim utilise une branche GitHub dédiée `updates` plutôt que de dépendre directement d'une GitHub Release.

Le workflow actuel :

1. GitHub Actions compile les builds Paper supportées.
2. Les tests sont exécutés avant la publication.
3. Chaque JAR est associé au SHA du commit Git qui l'a produit.
4. Un fichier `.sha256` est généré pour chaque JAR.
5. `update.json` décrit les builds disponibles.
6. Le plugin vérifie que le commit publié correspond au HEAD actuel de `main`.
7. Le JAR correspondant à la version de Paper est téléchargé dans un fichier temporaire.
8. Le nom de fichier et son association au commit sont contrôlés.
9. La taille du fichier est contrôlée.
10. Le SHA-256 du JAR est comparé au manifeste.
11. Le JAR vérifié est placé dans `plugins/update/NoxoClaim.jar`.
12. Le serveur doit être redémarré pour appliquer la mise à jour.

NoxoClaim **ne remplace pas le JAR actuellement chargé pendant son exécution**.

---

## 📋 Commandes joueur

| Commande | Description |
|---|---|
| `/claim` | Claim le chunk actuel s'il est libre. |
| `/claim menu` | Ouvre le menu principal. |
| `/claim map` | Ouvre la carte interactive. |
| `/claim voir [1-64\|off]` | Affiche les claims autour du joueur ou désactive l'affichage. |
| `/claim list` | Liste les claims du joueur. |
| `/claim info` | Affiche les informations du claim actuel. |
| `/claim flags` | Ouvre la gestion des flags. |
| `/claim flags <flag> <true\|false>` | Modifie un flag du claim actuel. |
| `/claim trust <joueur>` | Ajoute un membre au claim actuel. |
| `/claim untrust <joueur>` | Retire un membre du claim actuel. |
| `/claim sethome` | Définit le home du claim actuel. |
| `/claim home` | Téléporte vers le home du claim actuel. |
| `/claim unclaim` | Retire le claim actuel si le joueur en est propriétaire. |
| `/claim help` | Affiche l'aide en jeu. |
| `/hclaim ...` | Alias de `/claim`. |
| `/uclaim` | Retire le claim du chunk actuel. |
| `/map` | Alias de `/claim map`. |
| `/chome <nom>` | Téléporte vers un claim par son nom. |
| `/chome list` | Ouvre la liste des claims. |

### Raccourcis acceptés

NoxoClaim accepte également plusieurs variantes dans le sous-menu `/claim` :

- `gui` → `menu`
- `show` / `visual` → `voir`
- `delete` / `remove` / `abandon` → `unclaim`
- `home` / `tp` → téléportation vers le home

---

## 🔧 Commandes administrateur

Permission principale : `noxoclaim.admin`.

| Commande | Description |
|---|---|
| `/claimadmin` | Affiche l'aide administrateur. |
| `/claimadmin dashboard` | Ouvre le dashboard administrateur. |
| `/claimadmin list` | Liste les claims du serveur avec leurs UUID, propriétaires et tailles. |
| `/claimadmin info` | Affiche le statut du plugin. |
| `/claimadmin status` | Affiche le statut du plugin. |
| `/claimadmin delete <uuid>` | Supprime un claim précis. |
| `/claimadmin deleteall <uuid joueur>` | Supprime tous les claims d'un joueur. |
| `/claimadmin save` | Force la sauvegarde des claims. |
| `/claimadmin reload` | Recharge `config.yml`. |
| `/claimadmin update [commit]` | Vérifie et prépare une mise à jour. |
| `/claimadmin updates` | Alias de `update`. |
| `/claimadmin check` | Alias de `update`. |
| `/claimadmin debug` | Affiche les informations techniques de diagnostic. |
| `/claimadmin hud status` | Affiche l'état de HUDEngine. |
| `/claimadmin hud install` | Demande l'installation de HUDEngine. |
| `/claimadmin hud reinstall` | Relance l'installation si nécessaire. |
| `/claimadmin hud enable` | Active l'intégration HUDEngine. |
| `/claimadmin hud disable` | Désactive l'intégration HUDEngine. |
| `/claimadmin hud refresh` | Actualise le HUD. |

### UUID et administration

`/claimadmin delete` attend l'UUID du **claim**.

`/claimadmin deleteall` attend l'UUID du **joueur**.

---

## 🔐 Permissions

| Permission | Défaut | Utilisation |
|---|---:|---|
| `noxoclaim.admin` | OP | Accès administrateur complet. |
| `noxoclaim.admin.dashboard` | OP | Dashboard administrateur. |
| `noxoclaim.admin.list` | OP | Liste des claims. |
| `noxoclaim.admin.delete` | OP | Suppression administrative. |
| `noxoclaim.admin.save` | OP | Sauvegarde forcée. |
| `noxoclaim.admin.reload` | OP | Rechargement de la configuration. |
| `noxoclaim.admin.update` | OP | Vérification/préparation des mises à jour. |
| `noxoclaim.admin.debug` | OP | Diagnostic. |
| `noxoclaim.admin.hud` | OP | Administration de HUDEngine. |
| `noxoclaim.bypass` | OP | Ignore les protections. |
| `noxoclaim.claim` | Oui | Création de claims manuels. |
| `noxoclaim.autoclaim` | Non | Autorise l'auto-claim. |
| `noxoclaim.menu` | Oui | Accès au menu. |
| `noxoclaim.map` | Oui | Accès à la carte. |
| `noxoclaim.view` | Oui | Visualisation des claims. |
| `noxoclaim.unclaim` | Oui | Suppression de ses propres claims. |
| `noxoclaim.claim.3` | Non | Limite de 3 claims. |
| `noxoclaim.claim.10` | Non | Limite de 10 claims. |
| `noxoclaim.claim.25` | Non | Limite de 25 claims. |
| `noxoclaim.claim.50` | Non | Limite de 50 claims. |

Les permissions `noxoclaim.admin.*` sont également déclarées individuellement afin de permettre une délégation fine avec un gestionnaire de permissions.

---

## 📦 Installation

### Prérequis

- Serveur **Paper 26.2** ou **Paper 26.1.2** pour les builds actuellement publiées.
- **Java 25**.
- Vault uniquement si l'économie est utilisée.
- Un plugin d'économie compatible Vault si les claims payants sont activés.

### Installation du JAR

1. Téléchargez la build correspondant à votre version de Paper.
2. Placez `NoxoClaim-2.0.1.jar` dans le dossier `plugins/`.
3. Démarrez ou redémarrez le serveur.
4. Vérifiez avec :

```text
/plugins
```

5. Configurez les options dans :

```text
plugins/NoxoClaim/config.yml
plugins/NoxoClaim/messages.yml
```

Les claims sont stockés dans :

```text
plugins/NoxoClaim/claims.yml
```

### Première utilisation

Dans un chunk libre :

```text
/claim
```

Ou ouvrez le menu :

```text
/claim menu
```

Puis utilisez la carte avec :

```text
/claim map
```

---

## ⚙️ Configuration

La configuration actuelle contient notamment :

```yaml
claim:
  max-per-player: 10
  auto-claim:
    enabled: true
    permission: noxoclaim.autoclaim
    first-only: false
  default-flags:
    pvp: false
    explosions: false
    fire: false
    mob-griefing: false
    fluids: false
    entry: true

economy:
  enabled: true
  cost-per-chunk: 500.0

teleport:
  delay-seconds: 3
  cancel-on-move: true

map:
  visual-radius: 32
  max-visual-radius: 64
  visual-duration-ticks: 120

hudengine:
  enabled: true
  auto-install: true
  minimap:
    enabled: true
    radius: 4
    refresh-ticks: 10

storage:
  file: claims.yml
  autosave-seconds: 300
  backups: true
  backup-count: 5

updates:
  enabled: true
  check-on-startup: true
  check-interval-hours: 1
  auto-update: true
  verify-sha256: true
  notify-console: true
  notify-admins: true
```

### Auto-claim

L'auto-claim est activé dans la configuration, mais la permission `noxoclaim.autoclaim` est désactivée par défaut. Il faut donc explicitement accorder cette permission à un joueur ou à un groupe pour que l'auto-claim soit utilisable.

### Sauvegardes

NoxoClaim utilise `claims.yml` et effectue des sauvegardes configurables. Avant une migration importante, sauvegardez l'ensemble du dossier :

```text
plugins/NoxoClaim/
```

---

## 🧠 Architecture technique

Le projet est organisé autour de plusieurs composants :

```text
NoxoClaim
├── NoxoClaim.java
├── commands/
│   ├── ClaimCommand.java
│   └── ClaimAdminCommand.java
├── effects/
│   ├── ClaimEffects.java
│   └── ClaimVisualizer.java
├── gui/
│   ├── ClaimGui.java
│   └── ClaimAdminGui.java
├── hud/
│   ├── HudEngineInstaller.java
│   ├── HudEngineIntegration.java
│   └── HudEngineListener.java
├── listeners/
│   └── ClaimProtectionListener.java
├── managers/
│   ├── ClaimManager.java
│   └── MessageManager.java
├── map/
│   └── ClaimMapIntegration.java
├── models/
│   ├── Claim.java
│   └── ClaimFlag.java
├── update/
│   ├── UpdateChecker.java
│   └── UpdateInfo.java
└── utils/
    └── TeleportTask.java
```

### Indexation des claims

Le `ClaimManager` maintient :

- un registre des claims par UUID ;
- un index chunk → claim pour les recherches rapides ;
- un index propriétaire → claims ;
- une révision interne pour les intégrations qui doivent détecter les changements.

Les chevauchements sont refusés lors de l'ajout d'un claim.

### Persistance

Les données sont sérialisées en YAML dans `claims.yml`. Les écritures passent par un fichier temporaire puis une tentative de déplacement atomique afin de réduire le risque de corruption pendant une sauvegarde.

---

## 🛡️ Sécurité

Plusieurs contrôles sont appliqués aux téléchargements de mise à jour :

- commit Git attendu de 40 caractères ;
- vérification de la présence du commit dans le manifeste ;
- comparaison du commit publié avec le HEAD de `main` ;
- refus des chemins de fichiers dangereux ;
- obligation que le nom du JAR contienne le commit attendu ;
- téléchargement dans un fichier temporaire ;
- contrôle de taille du JAR ;
- calcul et vérification du SHA-256 ;
- remplacement du fichier cible uniquement après validation.

Le téléchargement de HUDEngine applique également une vérification SHA-256 avant de placer le JAR dans `plugins/`.

---

## 🤖 GitHub Actions

Le workflow `.github/workflows/build.yml` :

- se déclenche sur `push` vers `main` ;
- se déclenche sur les pull requests ;
- peut être lancé manuellement ;
- utilise Java 25 ;
- utilise Gradle 9.7.0 ;
- construit Paper 26.2 et Paper 26.1.2 ;
- lance `clean test build` ;
- vérifie qu'un JAR a bien été produit ;
- calcule les SHA-256 ;
- génère `update.json` ;
- publie les builds dans la branche `updates` après un push réussi sur `main`.

### Canal `updates`

La branche générée suit cette structure :

```text
updates/
├── update.json
└── assets/
    ├── NoxoClaim-Paper-26.2-<commit>.jar
    ├── NoxoClaim-Paper-26.2-<commit>.jar.sha256
    ├── NoxoClaim-Paper-26.1.2-<commit>.jar
    └── NoxoClaim-Paper-26.1.2-<commit>.jar.sha256
```

Le manifeste contient les builds par version de Paper ainsi que leur empreinte SHA-256.

---

## 🧪 Tests

Le projet utilise **JUnit 5**.

Les tests présents couvrent notamment :

- le modèle `Claim` ;
- les flags `ClaimFlag` ;
- le comportement du système de mise à jour.

Pour exécuter les tests :

```bash
./gradlew test
```

Pour effectuer une compilation complète :

```bash
./gradlew clean test build
```

Sous Windows :

```powershell
.\gradlew.bat clean test build
```

Le JAR Shadow est généré dans :

```text
build/libs/NoxoClaim-2.0.1.jar
```

---

## 🏗️ Développement

### Version Java

Le projet utilise le toolchain Java 25 :

```kotlin
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
```

### Version Paper

La version par défaut de l'API Paper est configurable avec `paperApiVersion` et vaut actuellement `26.2.build.+`.

Exemple :

```bash
gradle clean test build -PpaperApiVersion="26.2.build.+"
```

Pour Paper 26.1.2 :

```bash
gradle clean test build -PpaperApiVersion="26.1.2.build.+"
```

Le commit embarqué dans le build est fourni par `GITHUB_SHA` ou `noxoclaimCommit`.

---

## 🧩 Compatibilité

| Composant | Support |
|---|---|
| Paper 26.2 | ✅ Build dédiée |
| Paper 26.1.2 | ✅ Build dédiée |
| Java 25 | ✅ Requis pour les builds actuelles |
| Vault | 🟡 Optionnel |
| LuckPerms | 🟡 Déclaré comme dépendance souple pour la gestion des permissions |
| HUDEngine | 🟡 Intégration optionnelle/configurable |
| Mod client obligatoire | ❌ Non |

Les versions Minecraft/Paper non listées ne sont pas garanties.

---

## 🔍 Diagnostic & dépannage

### `Aucun fournisseur d'économie Vault détecté`

Vault est disponible mais aucun fournisseur d'économie compatible n'a été trouvé. Les claims nécessitant un paiement ne pourront pas être créés tant qu'un fournisseur n'est pas disponible.

### `Le commit demandé n'est pas disponible`

Le commit demandé à `/claimadmin update <commit>` doit correspondre au commit actuellement publié dans le canal `updates`.

### `Canal de mise à jour temporairement obsolète`

Le manifeste `updates/update.json` ne correspond pas au HEAD actuel de `main`. NoxoClaim refuse alors d'installer une ancienne build.

### `aucun artefact dans le manifeste`

Le manifeste a été trouvé mais aucun artefact exploitable n'y est présent. Vérifiez le workflow GitHub Actions et la branche `updates`.

### `téléchargement HTTP 404`

Le manifeste référence un fichier qui n'est pas accessible à l'emplacement attendu. Vérifiez le contenu de `updates/assets/`.

### `SHA-256 invalide`

Le fichier téléchargé ne correspond pas à l'empreinte déclarée dans le manifeste. NoxoClaim supprime le fichier temporaire et n'installe pas la mise à jour.

### `HUDEngine` absent

Si l'installation automatique est activée, NoxoClaim tente de télécharger la version épinglée de HUDEngine. Après installation, un **redémarrage du serveur est nécessaire** pour charger le plugin.

### Vérifier l'état du plugin

Utilisez :

```text
/claimadmin status
/claimadmin debug
/claimadmin hud status
```

---

## 📁 Fichiers importants

```text
NoxoClaim/
├── .github/workflows/build.yml
├── src/main/java/
├── src/main/resources/
│   ├── config.yml
│   ├── messages.yml
│   ├── plugin.yml
│   └── build-info.properties
├── src/test/java/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

Après installation sur un serveur :

```text
plugins/
├── NoxoClaim-2.0.1.jar
├── NoxoClaim/
│   ├── config.yml
│   ├── messages.yml
│   └── claims.yml
└── update/
    └── NoxoClaim.jar    # uniquement lorsqu'une mise à jour est préparée
```

---

## 📜 Licence

Consultez le fichier `LICENSE` du dépôt pour connaître les conditions d'utilisation, de modification et de redistribution.

---

## 👤 Auteur

**NoxoDEV**

Projet : [github.com/Noxo123/NoxoClaim](https://github.com/Noxo123/NoxoClaim)

---

## ⭐ NoxoClaim

**Protection · Claims · Carte · Homes · HUD · Administration · Mises à jour sécurisées**

NoxoClaim est conçu pour fournir une base de protection moderne pour Paper, avec une interface joueur simple et une architecture suffisamment structurée pour évoluer avec le serveur.
