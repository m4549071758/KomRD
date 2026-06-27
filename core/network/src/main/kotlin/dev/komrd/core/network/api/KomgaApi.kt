package dev.komrd.core.network.api

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
import dev.komrd.core.network.dto.WhoAmIDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Suppress("TooManyFunctions")
interface KomgaApi {
    @GET("api/v1/libraries")
    suspend fun listLibraries(): List<LibraryDto>

    @POST("api/v1/series/list")
    suspend fun listSeries(
        @Body request: SeriesListRequestDto = SeriesListRequestDto(),
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
        @Query("sort") sort: List<String>? = null,
    ): PageDto<SeriesDto>

    @POST("api/v1/books/list")
    suspend fun listBooks(
        @Body request: BookListRequestDto = BookListRequestDto(),
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
        @Query("sort") sort: List<String>? = null,
    ): PageDto<BookDto>

    /** `GET /api/v1/series/{id}`: Series詳細（metadata.readingDirection 含む。決定階層 ②）。 */
    @GET("api/v1/series/{seriesId}")
    suspend fun getSeries(
        @Path("seriesId") seriesId: String,
    ): SeriesDto

    @GET("api/v1/books/{bookId}/pages/{pageNumber}")
    suspend fun getBookPage(
        @Path("bookId") bookId: String,
        @Path("pageNumber") pageNumber: Int,
        @Query("convert") convert: String? = null,
        @Query("zero_based") zeroBased: Boolean? = null,
        @Header("If-Modified-Since") ifModifiedSince: String? = null,
    ): ResponseBody

    /** `GET /api/v1/books/{id}`: Book詳細（media.pagesCount / metadata.readingDirection 含む）。 */
    @GET("api/v1/books/{bookId}")
    suspend fun getBook(
        @Path("bookId") bookId: String,
    ): BookDto

    /** `GET /api/v1/books/{id}/pages`: 各ページの番号・寸法。 */
    @GET("api/v1/books/{bookId}/pages")
    suspend fun getBookPages(
        @Path("bookId") bookId: String,
    ): List<BookPageDto>

    @GET("api/v1/readlists/{readListId}/books")
    suspend fun listReadListBooks(
        @Path("readListId") readListId: String,
    ): List<BookDto>

    /**
     * `GET /api/v1/readlists/{id}/books` の Paging 対応版（M5 で ReadList 詳細画面の Pager に使用）。
     * Komga は page/size クエリで `PageBookDto` を返す。`ordered` のときは readList.number ソート、
     * それ以外は metadata.releaseDate,asc 固定。
     */
    @GET("api/v1/readlists/{readListId}/books")
    suspend fun listReadListBooksPaged(
        @Path("readListId") readListId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<BookDto>

    /**
     * `GET /api/v1/collections`: Collection一覧（M5 でドロワー・一覧画面用）。
     * 件数少ない想定で大きめ size で全件取得する使い方と、Paging する使い方の両方に対応。
     */
    @GET("api/v1/collections")
    suspend fun listCollections(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String? = null,
    ): PageDto<CollectionDto>

    /** `GET /api/v1/collections/{id}`: Collection詳細（M5 で詳細画面用）。 */
    @GET("api/v1/collections/{collectionId}")
    suspend fun getCollection(
        @Path("collectionId") collectionId: String,
    ): CollectionDto

    /**
     * `GET /api/v1/collections/{id}/series`: Collection 内の Series 一覧（M5 で詳細画面の Pager 用）。
     * Komga 仕様上、Series 用のクエリ版 GET（`condition` DSL ではなく search/library_id クエリ）。
     */
    @GET("api/v1/collections/{collectionId}/series")
    suspend fun listCollectionSeries(
        @Path("collectionId") collectionId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<SeriesDto>

    /** `GET /api/v1/readlists`: Read List一覧（M5 でドロワー・一覧画面用）。 */
    @GET("api/v1/readlists")
    suspend fun listReadLists(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String? = null,
    ): PageDto<ReadListDto>

    /** `GET /api/v1/readlists/{id}`: Read List詳細（M5 で詳細画面用）。 */
    @GET("api/v1/readlists/{readListId}")
    suspend fun getReadList(
        @Path("readListId") readListId: String,
    ): ReadListDto

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body request: ReadProgressUpdateDto,
    ): Response<Unit>

    @GET("api/v1/users/me")
    suspend fun whoami(): WhoAmIDto

    @GET("api/v1/settings")
    suspend fun getSettings(): SettingsDto

    @PATCH("api/v1/settings")
    suspend fun updateSettings(
        @Body body: SettingsUpdateDto,
    ): Response<Unit>

    @GET("api/v2/users/me")
    suspend fun getCurrentUser(): UserDto

    @GET("api/v1/books/{bookId}/manifest/epub")
    suspend fun getManifestEpub(
        @Path("bookId") bookId: String,
    ): WPPublicationDto

    @GET("api/v1/books/{bookId}/resource/{resource}")
    suspend fun getBookEpubResource(
        @Path("bookId") bookId: String,
        @Path("resource", encoded = true) resource: String,
    ): ResponseBody

    @GET("api/v1/books/{bookId}/positions")
    suspend fun getPositions(
        @Path("bookId") bookId: String,
    ): R2PositionsDto

    @GET("api/v1/books/{bookId}/progression")
    suspend fun getProgression(
        @Path("bookId") bookId: String,
    ): Response<R2ProgressionDto>

    @PUT("api/v1/books/{bookId}/progression")
    suspend fun putProgression(
        @Path("bookId") bookId: String,
        @Body progression: R2ProgressionDto,
    ): Response<Unit>
}
