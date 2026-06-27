package dev.komrd.core.network.dto

import dev.komrd.core.model.ReadStatusFilter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun isCondition(
    field: String,
    value: String,
): JsonObject =
    buildJsonObject {
        put(
            field,
            buildJsonObject {
                put("operator", "is")
                put("value", value)
            },
        )
    }

internal fun seriesInLibraryCondition(
    libraryId: String,
    readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
): JsonObject =
    readStatusFilter.toApiValue()?.let { status ->
        andCondition(isCondition("libraryId", libraryId), readStatusCondition(status))
    } ?: isCondition("libraryId", libraryId)

/** Book一覧をSeriesで絞り込む条件。 */
internal fun booksInSeriesCondition(seriesId: String): JsonObject = isCondition("seriesId", seriesId)

internal fun booksReadStatusCondition(readStatus: String): JsonObject = isCondition("readStatus", readStatus)

/**
 * readStatus条件のみを生成（M5 で `Library` 単位の絞り込みに再利用するため独立化）。
 * [status] は `UNREAD`/`IN_PROGRESS`/`READ`。複数 Book/Series list で横断利用される。
 */
internal fun readStatusCondition(status: String): JsonObject = isCondition("readStatus", status)

/**
 * libraryId条件のみを生成（M5 で検索時のLibrary絞り込み・他 condition との AND 合成に使う）。
 */
internal fun libraryCondition(libraryId: String): JsonObject = isCondition("libraryId", libraryId)

/**
 * 複数条件を AND 合成する（M5 で library×readStatus 等の組み合わせに使う）。
 * Komga 検索 DSL の `allOf` キーで配列として包む（実装ソース [SearchCondition.kt] 準拠）。
 */
internal fun andCondition(vararg conditions: JsonObject): JsonObject =
    buildJsonObject {
        putJsonArray("allOf") {
            conditions.forEach { add(it) }
        }
    }

internal fun searchCondition(libraryId: String?): JsonObject? = libraryId?.let { andCondition(libraryCondition(it)) }
