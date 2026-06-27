[日本語](CONTRIBUTING.md)

# Contributing Guide

Thank you for your interest in contributing to KomRD.
Bug reports, feature suggestions, and code contributions are all welcome.

## Bug Reports / Feature Requests

Please use the templates available in [Issues](../../issues).

## Development Environment

- JDK 17+
- Android SDK (compileSdk 36)
- Android Studio (recommended)

```bash
git clone https://github.com/m4549071758/KomRD.git
cd KomRD
./gradlew assembleDebug
```

## Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/).

```
feat: add OAuth2 to user authentication
fix: fix thumbnails not displaying
```

Types: `feat` / `fix` / `docs` / `refactor` / `test` / `chore`

## Branches / Pull Requests

- Do not work directly on `main` — create a branch for each task
- Link related issues with `Closes #n` in your PR

## Code Style / Tests

Please pass the following checks before submitting a PR:

```bash
./gradlew ktlintCheck
```

```bash
./gradlew detekt
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug
```

CI runs the same checks automatically.

## License

Contributions to this project are provided under the [MIT License](LICENSE).
