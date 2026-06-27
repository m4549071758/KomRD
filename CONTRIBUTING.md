[English](CONTRIBUTING.en.md)

# コントリビューションガイド

KomRDへの貢献に感謝します。
バグ報告・機能提案・コード修正など、どんな貢献も歓迎です。

## バグ報告 / 機能提案

[Issues](../../issues)からテンプレートを選んで報告してください。

## 開発環境

- JDK 17以上
- Android SDK（compileSdk 36）
- Android Studio（推奨）

```bash
git clone https://github.com/m4549071758/KomRD.git
cd KomRD
./gradlew assembleDebug
```

## コミット規約

[Conventional Commits](https://www.conventionalcommits.org/)形式を使います。

```
feat: ユーザー認証にOAuth2を追加
fix: サムネイルが表示されない問題を修正
```

type: `feat` / `fix` / `docs` / `refactor` / `test` / `chore`

## ブランチ / PR

- `main`で直接作業せず、作業ごとにブランチを切ってください
- PRは関連するIssueがあれば`Closes #n`で紐付けてください

## コードスタイル / テスト

PRを出す前に以下を通してください。

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

CIでも同じチェックが自動実行されます。

## ライセンス

本プロジェクトへの貢献は[MIT License](LICENSE)の下で提供されます。
