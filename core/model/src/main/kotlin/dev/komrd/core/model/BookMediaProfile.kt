package dev.komrd.core.model

/**
 * Bookの媒体プロファイル（Komga `media.mediaProfile`）。
 * ページ画像リーダー系([IMAGE]/[PDF]/[DIVINA])とEPUBテキストリーダー系([EPUB])の分岐起点(M6)。
 * Komgaは `"EPUB"` / `"PDF"` / `"DIVINA"` を送る。null・未知は[IMAGE]扱い
 * (後方互換・既存のページ画像リーダー経路で扱う)。
 */
enum class BookMediaProfile {
    PDF,
    EPUB,
    DIVINA,
    IMAGE,
}

/** EPUBテキストリーダー系統で扱うべき媒体か(WebView描画・Readium API・R2Locator進捗)。 */
val BookMediaProfile.isEpub: Boolean
    get() = this == BookMediaProfile.EPUB

/**
 * KomgaのmediaProfile文字列をenumへ。null・未知は[BookMediaProfile.IMAGE]
 * (後方互換・ページ画像リーダーで扱う)。
 */
fun String?.toBookMediaProfile(): BookMediaProfile =
    when (this?.uppercase()) {
        "PDF" -> BookMediaProfile.PDF
        "EPUB" -> BookMediaProfile.EPUB
        "DIVINA" -> BookMediaProfile.DIVINA
        else -> BookMediaProfile.IMAGE
    }
