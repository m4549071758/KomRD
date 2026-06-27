package dev.komrd.core.data.epub

import dev.komrd.core.model.EpubChapter
import dev.komrd.core.model.EpubLocator
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.EpubResource
import dev.komrd.core.network.dto.R2LocatorDto
import dev.komrd.core.network.dto.R2PositionsDto
import dev.komrd.core.network.dto.WPLinkDto
import dev.komrd.core.network.dto.WPPublicationDto

internal fun WPPublicationDto.toEpubManifest(): EpubManifest =
    EpubManifest(
        readingOrder = readingOrder.map { it.toEpubChapter() },
        toc = toc.map { it.toEpubChapter() },
        resources = resources.map { it.toEpubResource() },
        title = metadata.title,
        readingProgression = metadata.readingProgression,
    )

/** `WPLinkDto`(spine/toc) → [EpubChapter]。 */
internal fun WPLinkDto.toEpubChapter(): EpubChapter =
    EpubChapter(
        href = href,
        type = type,
        title = title,
    )

/** `WPLinkDto`(非spine) → [EpubResource]。 */
internal fun WPLinkDto.toEpubResource(): EpubResource =
    EpubResource(
        href = href,
        type = type,
        rel = rel,
    )

/** `R2LocatorDto` → [EpubLocator]。`locations`欠落時は進捗/位置をnullとする。 */
internal fun R2LocatorDto.toEpubLocator(): EpubLocator =
    EpubLocator(
        href = href,
        progression = locations?.progression,
        totalProgression = locations?.totalProgression,
        position = locations?.position,
        fragments = locations?.fragments ?: emptyList(),
    )

/** `R2PositionsDto` → 位置リスト。各locatorを[EpubLocator]へ。 */
internal fun R2PositionsDto.toEpubLocators(): List<EpubLocator> = positions.map { it.toEpubLocator() }
