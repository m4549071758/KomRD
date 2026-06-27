package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.EpubChapter
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.EpubResource
import dev.komrd.core.model.NextBook

object PrefetchPlanner {
    /** 画像系ページリーダー向けWindow計算。 */
    fun plan(
        currentBook: BookDetail,
        currentPageNumber: Int,
        nextBook: NextBook?,
    ): List<PrefetchTarget> {
        val variant = variantFor(currentBook.mediaProfile)
        val targets = mutableListOf<PrefetchTarget>()

        // 現Book: currentPageNumber以降〜末尾。page.numberが一致する位置から、不在なら先頭フォールバック。
        val startIndex =
            currentBook.pages.indexOfFirst { it.number == currentPageNumber }.let { if (it < 0) 0 else it }
        for (i in startIndex until currentBook.pages.size) {
            val page = currentBook.pages[i]
            targets += imageTarget(currentBook.id, page.number, variant)
        }

        // 次Book: 先頭〜末尾(1..pagesCount)。pagesCount<=0なら取得対象なし。
        if (nextBook != null && nextBook.pagesCount > 0) {
            for (pageNumber in 1..nextBook.pagesCount) {
                targets += imageTarget(nextBook.bookId, pageNumber, variant)
            }
        }

        return targets
    }

    fun planEpub(
        currentBookId: String,
        currentManifest: EpubManifest,
        currentChapterIndex: Int,
        nextBookManifests: List<Pair<String, EpubManifest>>? = null,
    ): List<PrefetchTarget> {
        val targets = mutableListOf<PrefetchTarget>()
        val seen = mutableSetOf<Pair<String, String>>() // (resourcePath, variant)

        // 現Book: 現章以降〜readingOrder末尾。
        val chapters = currentManifest.readingOrder
        val startIndex = currentChapterIndex.coerceIn(0, chapters.size)
        for (i in startIndex until chapters.size) {
            addChapter(currentBookId, chapters[i], targets, seen)
        }
        // 現Bookのresources(補助リソース・章以降に紐づくCSS/画像/フォント)。
        for (resource in currentManifest.resources) {
            addResource(currentBookId, resource, targets, seen)
        }

        // 次册: 全章 + 各resources(best-effort・null時スキップ)。
        if (nextBookManifests != null) {
            for ((bookId, manifest) in nextBookManifests) {
                for (chapter in manifest.readingOrder) {
                    addChapter(bookId, chapter, targets, seen)
                }
                for (resource in manifest.resources) {
                    addResource(bookId, resource, targets, seen)
                }
            }
        }

        return targets
    }

    private fun addChapter(
        bookId: String,
        chapter: EpubChapter,
        targets: MutableList<PrefetchTarget>,
        seen: MutableSet<Pair<String, String>>,
    ) {
        val key = chapter.href to PrefetchStore.VARIANT_FULL
        if (seen.add(key)) {
            targets +=
                PrefetchTarget(
                    bookId,
                    chapter.href,
                    PrefetchStore.RESOURCE_KIND_HTML,
                    PrefetchStore.VARIANT_FULL,
                )
        }
    }

    private fun addResource(
        bookId: String,
        resource: EpubResource,
        targets: MutableList<PrefetchTarget>,
        seen: MutableSet<Pair<String, String>>,
    ) {
        val kind = resourceKindFor(resource.type)
        val key = resource.href to PrefetchStore.VARIANT_FULL
        if (seen.add(key)) {
            targets += PrefetchTarget(bookId, resource.href, kind, PrefetchStore.VARIANT_FULL)
        }
    }

    /** EPUBリソースのMIMEから[resourceKind]を判定(不明はIMAGE扱いで安全側)。 */
    private fun resourceKindFor(type: String?): String =
        when {
            type == null -> PrefetchStore.RESOURCE_KIND_IMAGE
            type.contains("css", ignoreCase = true) -> PrefetchStore.RESOURCE_KIND_CSS
            type.contains("font", ignoreCase = true) ||
                type.contains("woff", ignoreCase = true) ||
                type.contains("ttf", ignoreCase = true) ||
                type.contains("otf", ignoreCase = true) -> PrefetchStore.RESOURCE_KIND_FONT
            type.startsWith("image/", ignoreCase = true) -> PrefetchStore.RESOURCE_KIND_IMAGE
            type.startsWith("text/html", ignoreCase = true) ||
                type.contains("xhtml", ignoreCase = true) -> PrefetchStore.RESOURCE_KIND_HTML
            else -> PrefetchStore.RESOURCE_KIND_IMAGE
        }

    private fun imageTarget(
        bookId: String,
        pageNumber: Int,
        variant: String,
    ): PrefetchTarget =
        PrefetchTarget(
            bookId = bookId,
            resourcePath = pageNumber.toString(),
            resourceKind = PrefetchStore.RESOURCE_KIND_PAGE,
            variant = variant,
            pageNumber = pageNumber,
        )
}
