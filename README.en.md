[日本語](README.md)

# KomRD

KomRD is an Android client for [Komga](https://komga.org/).
It automatically prefetches upcoming pages and volumes while you read, keeping page turns seamless even on unreliable connections.

> Status: In development (`v0.1.0`)

## Features

- Register and switch between multiple Komga servers
- Browse libraries, series, and books
- Page image reader (LTR / RTL / vertical scroll / Webtoon, spread view)
- Prefetch — automatic read-ahead for a seamless reading experience
- Read progress sync
- TOFU TLS pinning (custom CA support)

## Install

Download the APK from the [Releases](../../releases) page.

## Build

### Requirements

- JDK 17+
- Android SDK (compileSdk 36)
- Gradle: bundled via Wrapper (no separate install needed)

### Steps

```bash
git clone https://github.com/m4549071758/KomRD.git
cd KomRD
```

Point Gradle to your Android SDK (not needed if you open the project in Android Studio):

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

| Area | Technology |
|---|---|
| Language / UI | Kotlin / Jetpack Compose (Material 3) + [LumoUI](https://github.com/nthieu7393/LumoUI) |
| Architecture | MVVM + UDF + StateFlow |
| DI | Hilt |
| Async | Coroutines + Flow |
| Network | Retrofit + OkHttp + kotlinx.serialization |
| Image | Coil 3 |
| Local DB | Room |
| Paging | Paging 3 |
| Build | Gradle Kotlin DSL + Version Catalog + Convention Plugins |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT License](LICENSE)
