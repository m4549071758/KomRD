package dev.komrd.core.model

data class EpubChapter(
    val href: String,
    val type: String?,
    val title: String?,
)

/**
 * EPUB内の非spineリソース（CSS/画像/フォント等）。
 *
 * `readingOrder`に含まれない補助リソース。`rel`はリンク関係（`cover`/`contents`等）。
 */
data class EpubResource(
    val href: String,
    val type: String?,
    val rel: String?,
)

/**
 * EPUB出版物のmanifest（Readium Web Publication）。
 *
 * [readingOrder]はspine（読書順の章）、[toc]は目次ナビ、[resources]は非spineの補助リソース。
 * [title]は出版物タイトル、[readingProgression]は`ltr`/`rtl`/`ttb`/`btt`/`auto`。
 * `EpubRepository.loadManifest`が`WPPublicationDto`から生成する。
 */
data class EpubManifest(
    val readingOrder: List<EpubChapter>,
    val toc: List<EpubChapter>,
    val resources: List<EpubResource>,
    val title: String?,
    val readingProgression: String?,
)

data class EpubLocator(
    val href: String,
    val progression: Float?,
    val totalProgression: Float?,
    val position: Int?,
    val fragments: List<String> = emptyList(),
)
