package dev.komrd.core.data.reader

import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.model.toBookMediaProfile
import dev.komrd.core.network.KomgaThumbnails
import dev.komrd.core.network.dto.BookDto
import dev.komrd.core.network.dto.BookPageDto

internal fun BookDto.toBookDetail(
    server: Server,
    pages: List<BookPageDto>,
    seriesReadingDirection: ReadingDirection?,
): BookDetail =
    BookDetail(
        id = id,
        seriesId = seriesId,
        serverId = server.id,
        name = metadata.title?.takeIf { it.isNotBlank() } ?: name,
        pages = pages.map { it.toDomain(server.baseUrl, id) },
        readingDirection = seriesReadingDirection,
        mediaProfile = media.mediaProfile.toBookMediaProfile(),
        mediaType = media.mediaType,
    )

internal fun BookPageDto.toDomain(
    baseUrl: String,
    bookId: String,
): BookPage =
    BookPage(
        number = number,
        url = KomgaThumbnails.bookPageUrl(baseUrl, bookId, number),
        width = width,
        height = height,
        sizeBytes = sizeBytes,
    )

/**
 * KomgaのreadingDirection文字列をenumへ。LTR/RTL/VERTICAL/WEBTOONの4値に対応し、
 * 不明・nullはnull（③グローバル既定で解決させる）。
 */
internal fun String?.toReadingDirection(): ReadingDirection? =
    when (this) {
        "LEFT_TO_RIGHT" -> ReadingDirection.LEFT_TO_RIGHT
        "RIGHT_TO_LEFT" -> ReadingDirection.RIGHT_TO_LEFT
        "VERTICAL" -> ReadingDirection.VERTICAL
        "WEBTOON" -> ReadingDirection.WEBTOON
        else -> null
    }
