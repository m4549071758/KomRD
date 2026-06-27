[English](README.en.md)

# KomRD

KomRDは[Komga](https://komga.org/)向けのAndroidクライアントです。
読書中に次のページ・次の巻を自動で先読み（Prefetch）し、電波が不安定でもページ送りを途切れさせません。

> ステータス: 開発中（`v0.1.0`）

## 主な機能

- 複数のKomgaサーバ登録・切替
- ライブラリ / シリーズ / ブックの閲覧
- ページ画像リーダー（LTR / RTL / 縦スクロール / Webtoon、見開き対応）
- Prefetch — 自動先読みによるシームレスな読書体験
- Read Progress同期
- TLS信頼のTOFUピン留め（カスタムCA対応）

## インストール

[Releases](../../releases)ページからAPKをダウンロードしてインストールしてください。

## ビルド

### 必要環境

- JDK 17以上
- Android SDK（compileSdk 36）
- Gradle: Wrapper同梱（個別インストール不要）

### 手順

```bash
git clone https://github.com/m4549071758/KomRD.git
cd KomRD
```

Android SDKの場所を設定します（Android Studioで開いた場合は自動設定されるため不要）。

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
```

デバッグAPKのビルド:

```bash
./gradlew assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

## 技術スタック

| 領域 | 採用 |
|---|---|
| 言語 / UI | Kotlin / Jetpack Compose (Material 3) + [LumoUI](https://github.com/nthieu7393/LumoUI) |
| アーキ | MVVM + UDF + StateFlow |
| DI | Hilt |
| 非同期 | Coroutines + Flow |
| ネットワーク | Retrofit + OkHttp + kotlinx.serialization |
| 画像 | Coil 3 |
| ローカルDB | Room |
| ページング | Paging 3 |
| ビルド | Gradle Kotlin DSL + Version Catalog + Convention Plugins |

## コントリビュート

[CONTRIBUTING.md](CONTRIBUTING.md)を参照してください。

## ライセンス

[MIT License](LICENSE)
