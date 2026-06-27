package dev.komrd.buildlogic

import org.gradle.api.artifacts.VersionCatalog
fun VersionCatalog.requireLibrary(alias: String) =
    findLibrary(alias).orElseThrow {
        IllegalStateException("version catalog 'libs' にライブラリエイリアス '$alias' が見つかりません。libs.versions.toml を確認してください(レビュー #19)。")
    }