# NoxoClaim

Plugin Paper de protection de terrains, en français, développé par NoxoDEV.

## Fonctionnalités

- Sélection de deux coins avec une wand.
- Claims rectangulaires persistants en YAML.
- Limite de claims par joueur.
- Protection des blocs et interactions.
- Membres de confiance.
- Flags PVP, explosions, feu, mob-griefing et entrée.
- GUI de gestion.
- Home de claim et téléportation différée.
- Commandes administrateur.
- Tests JUnit : 25 tests de `Claim` + 4 tests de `ClaimFlag`.
- Build automatique avec GitHub Actions.

## Commandes

- `/claim wand`
- `/claim create`
- `/claim delete`
- `/claim info`
- `/claim trust <joueur>`
- `/claim untrust <joueur>`
- `/claim flags [flag] [true|false]`
- `/claim list`
- `/claim gui`
- `/claim sethome`
- `/claim home`
- `/claimadmin list`
- `/claimadmin delete <uuid>`
- `/claimadmin deleteall <uuid>`
- `/claimadmin save`
- `/claimadmin reload`

## Build

Le projet utilise Gradle 8.12 et Java 21.

```bash
./gradlew clean test build
```

Le JAR est généré dans `build/libs/`.
