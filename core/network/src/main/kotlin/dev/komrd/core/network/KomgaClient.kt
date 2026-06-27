package dev.komrd.core.network

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Server
import dev.komrd.core.network.api.KomgaApi
import dev.komrd.core.network.dto.BookDto
import dev.komrd.core.network.dto.BookListRequestDto
import dev.komrd.core.network.dto.BookPageDto
import dev.komrd.core.network.dto.CollectionDto
import dev.komrd.core.network.dto.LibraryDto
import dev.komrd.core.network.dto.PageDto
import dev.komrd.core.network.dto.R2PositionsDto
import dev.komrd.core.network.dto.R2ProgressionDto
import dev.komrd.core.network.dto.ReadListDto
import dev.komrd.core.network.dto.ReadProgressUpdateDto
import dev.komrd.core.network.dto.SeriesDto
import dev.komrd.core.network.dto.SeriesListRequestDto
import dev.komrd.core.network.dto.SettingsDto
import dev.komrd.core.network.dto.SettingsUpdateDto
import dev.komrd.core.network.dto.UserDto
import dev.komrd.core.network.dto.WPPublicationDto
import dev.komrd.core.network.dto.booksInSeriesCondition
import dev.komrd.core.network.dto.booksReadStatusCondition
import dev.komrd.core.network.dto.searchCondition
import dev.komrd.core.network.dto.seriesInLibraryCondition
import dev.komrd.core.network.error.toKomgaError
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException

@Suppress("TooManyFunctions")
class KomgaClient(
    val server: Server,
    val api: KomgaApi,
    val okHttpClient: OkHttpClient,
) {
    suspend fun listLibraries(): KomgaResult<List<LibraryDto>> =
        safeKomgaCall {
            api.listLibraries()
        }

    suspend fun listSeries(
        request: SeriesListRequestDto = SeriesListRequestDto(),
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<SeriesDto>> =
        safeKomgaCall {
            api.listSeries(request = request, page = page, size = size, sort = sort)
        }

    suspend fun listBooks(
        request: BookListRequestDto = BookListRequestDto(),
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<BookDto>> =
        safeKomgaCall {
            api.listBooks(request = request, page = page, size = size, sort = sort)
        }

    suspend fun listSeriesInLibrary(
        libraryId: String,
        readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<SeriesDto>> =
        listSeries(
            request = SeriesListRequestDto(condition = seriesInLibraryCondition(libraryId, readStatusFilter)),
            page = page,
            size = size,
            sort = sort,
        )

    /** Seriesで絞り込んだBook一覧（検索条件の構築はnetwork層に閉じる）。 */
    suspend fun listBooksInSeries(
        seriesId: String,
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<BookDto>> =
        listBooks(
            request = BookListRequestDto(condition = booksInSeriesCondition(seriesId)),
            page = page,
            size = size,
            sort = sort,
        )

    suspend fun searchSeries(
        query: String,
        libraryId: String? = null,
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<SeriesDto>> =
        listSeries(
            request = SeriesListRequestDto(condition = searchCondition(libraryId), fullTextSearch = query),
            page = page,
            size = size,
            sort = sort,
        )

    suspend fun searchBooks(
        query: String,
        libraryId: String? = null,
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<BookDto>> =
        listBooks(
            request = BookListRequestDto(condition = searchCondition(libraryId), fullTextSearch = query),
            page = page,
            size = size,
            sort = sort,
        )

    suspend fun listBooksByReadStatus(
        readStatus: String,
        page: Int? = null,
        size: Int? = null,
        sort: List<String>? = null,
    ): KomgaResult<PageDto<BookDto>> =
        listBooks(
            request = BookListRequestDto(condition = booksReadStatusCondition(readStatus)),
            page = page,
            size = size,
            sort = sort,
        )

    suspend fun getBookPage(
        bookId: String,
        pageNumber: Int,
        convert: String? = null,
        ifModifiedSince: String? = null,
    ): KomgaResult<ResponseBody> =
        safeKomgaCall {
            api.getBookPage(bookId, pageNumber, convert = convert, ifModifiedSince = ifModifiedSince)
        }

    /** `GET /api/v1/books/{id}`: Book詳細（ページ数・読書方向含む）。 */
    suspend fun getBook(bookId: String): KomgaResult<BookDto> =
        safeKomgaCall {
            api.getBook(bookId)
        }

    /** `GET /api/v1/books/{id}/pages`: 各ページの番号・寸法。 */
    suspend fun getBookPages(bookId: String): KomgaResult<List<BookPageDto>> =
        safeKomgaCall {
            api.getBookPages(bookId)
        }

    /** `GET /api/v1/series/{id}`: Series詳細（決定階層 ②の読書方向を取得）。 */
    suspend fun getSeries(seriesId: String): KomgaResult<SeriesDto> =
        safeKomgaCall {
            api.getSeries(seriesId)
        }

    suspend fun listReadListBooks(readListId: String): KomgaResult<List<BookDto>> =
        safeKomgaCall {
            api.listReadListBooks(readListId)
        }

    /**
     * `GET /api/v1/readlists/{id}/books` の Paging 対応版（M5 で ReadList 詳細画面の Pager に使用）。
     * 既存 [listReadListBooks] は全件 List を返す（Prefetch用）ため、UI 用途ではこちらを使う。
     */
    suspend fun listReadListBooksPaged(
        readListId: String,
        page: Int = 0,
        size: Int = 20,
    ): KomgaResult<PageDto<BookDto>> =
        safeKomgaCall {
            api.listReadListBooksPaged(readListId, page, size)
        }

    /**
     * `GET /api/v1/collections`: Collection一覧（M5 でドロワー/一覧画面用）。
     * 件数が少ない想定で `size=500` 全件取得して `content` のみ List で返す。
     */
    suspend fun listCollections(
        page: Int = 0,
        size: Int = 500,
        sort: String? = null,
    ): KomgaResult<List<CollectionDto>> =
        safeKomgaCall {
            api.listCollections(page = page, size = size, sort = sort).content
        }

    /** `GET /api/v1/collections/{id}`: Collection詳細（M5 で詳細画面用）。 */
    suspend fun getCollection(collectionId: String): KomgaResult<CollectionDto> =
        safeKomgaCall {
            api.getCollection(collectionId)
        }

    /**
     * `GET /api/v1/collections/{id}/series`: Collection 内の Series Paging 取得。
     * M5 で Collection 詳細画面の Pager に使用。Komga は `condition` DSL ではなくクエリ版 GET。
     */
    suspend fun listCollectionSeries(
        collectionId: String,
        page: Int = 0,
        size: Int = 20,
    ): KomgaResult<PageDto<SeriesDto>> =
        safeKomgaCall {
            api.listCollectionSeries(collectionId, page = page, size = size)
        }

    /**
     * `GET /api/v1/readlists`: Read List一覧（M5 でドロワー/一覧画面用）。
     * 件数が少ない想定で `size=500` 全件取得して `content` のみ List で返す。
     */
    suspend fun listReadLists(
        page: Int = 0,
        size: Int = 500,
        sort: String? = null,
    ): KomgaResult<List<ReadListDto>> =
        safeKomgaCall {
            api.listReadLists(page = page, size = size, sort = sort).content
        }

    /** `GET /api/v1/readlists/{id}`: Read List詳細（M5 で詳細画面用）。 */
    suspend fun getReadList(readListId: String): KomgaResult<ReadListDto> =
        safeKomgaCall {
            api.getReadList(readListId)
        }

    suspend fun updateReadProgress(
        bookId: String,
        request: ReadProgressUpdateDto,
    ): KomgaResult<Unit> =
        safeKomgaCall {
            val response = api.updateReadProgress(bookId, request)
            if (!response.isSuccessful) {
                // 成功時(Unitボディ)はRetrofitが読み切って閉じるため追加クローズ不要。
                response.errorBody()?.close()
                throw HttpException(response)
            }
        }

    suspend fun verifyConnection(): KomgaResult<ConnectionResult> =
        safeKomgaCall {
            ConnectionResult.Authenticated(userId = api.whoami().id)
        }

    suspend fun getSettings(): KomgaResult<SettingsDto> =
        safeKomgaCall {
            api.getSettings()
        }

    suspend fun updateSettings(body: SettingsUpdateDto): KomgaResult<Unit> =
        safeKomgaCall {
            val response = api.updateSettings(body)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw HttpException(response)
            }
        }

    suspend fun getCurrentUser(): KomgaResult<UserDto> =
        safeKomgaCall {
            api.getCurrentUser()
        }

    suspend fun getManifestEpub(bookId: String): KomgaResult<WPPublicationDto> =
        safeKomgaCall {
            api.getManifestEpub(bookId)
        }

    suspend fun getBookEpubResource(
        bookId: String,
        resource: String,
    ): KomgaResult<ResponseBody> =
        safeKomgaCall {
            api.getBookEpubResource(bookId, resource)
        }

    suspend fun getPositions(bookId: String): KomgaResult<R2PositionsDto> =
        safeKomgaCall {
            api.getPositions(bookId)
        }

    suspend fun getProgression(bookId: String): KomgaResult<R2ProgressionDto?> =
        safeKomgaCall {
            val response = api.getProgression(bookId)
            if (response.code() == 204) {
                null
            } else if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw HttpException(response)
            } else {
                response.body()
            }
        }

    suspend fun putProgression(
        bookId: String,
        progression: R2ProgressionDto,
    ): KomgaResult<Unit> =
        safeKomgaCall {
            val response = api.putProgression(bookId, progression)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw HttpException(response)
            }
        }
}

// HttpException/IOException等、Komga通信で発生しうるExceptionを一括でKomgaResult.Failureへ寄せるため
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> safeKomgaCall(call: suspend () -> T): KomgaResult<T> =
    try {
        KomgaResult.Success(call())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        // Error(OOM等)は伝播させる。通常のExceptionのみ KomgaResult.Failure へ変換する。
        KomgaResult.Failure(e.toKomgaError())
    }
