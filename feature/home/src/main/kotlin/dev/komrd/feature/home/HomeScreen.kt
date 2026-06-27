package dev.komrd.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.DrawerValue
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.ModalDrawerSheet
import dev.komrd.core.designsystem.components.ModalNavigationDrawer
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Surface
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.rememberDrawerState
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.Book
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.SeriesSort
import dev.komrd.core.model.Server
import dev.komrd.feature.library.LibraryBrowserDrawer
import dev.komrd.feature.library.LibraryUiState
import kotlinx.coroutines.launch

private val CardWidth = 120.dp
private const val THUMBNAIL_ASPECT = 0.7f

@Composable
@Suppress("LongParameterList")
fun HomeScreen(
    state: HomeUiState,
    inProgress: LazyPagingItems<Book>,
    read: LazyPagingItems<Book>,
    imageLoaderFor: (Server) -> coil3.ImageLoader,
    onSelectServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
    onOpenLibrary: (serverId: String, libraryId: String) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerUiState = state.toLibraryDrawerState()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                LibraryBrowserDrawer(
                    state = drawerUiState,
                    onSelectLibrary = { serverId, libraryId ->
                        onOpenLibrary(serverId, libraryId)
                        scope.launch { drawerState.close() }
                    },
                    onOpenCollection = { collection ->
                        onOpenCollection(collection)
                        scope.launch { drawerState.close() }
                    },
                    onOpenReadList = { readList ->
                        onOpenReadList(readList)
                        scope.launch { drawerState.close() }
                    },
                    onAddServer = {
                        scope.launch { drawerState.close() }
                        onAddServer()
                    },
                    onSelectServer = { serverId ->
                        onSelectServer(serverId)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        HomeScaffoldContent(
            state = state,
            inProgress = inProgress,
            read = read,
            imageLoaderFor = imageLoaderFor,
            onAddServer = onAddServer,
            onOpenBook = onOpenBook,
            onOpenLibrary = onOpenLibrary,
            onMenuClick = { scope.launch { drawerState.open() } },
        )
    }
}

/** Homeの[HomeUiState]をドロワー表示用[LibraryUiState]に詰め替える。Library選択ハイライトはHomeでは持たない。 */
private fun HomeUiState.toLibraryDrawerState(): LibraryUiState =
    LibraryUiState(
        loading = loading,
        noServer = noServer,
        serverGroups = serverGroups,
        selectedServer = selectedServer,
        selectedLibrary = null,
        // Homeではソート/フィルタは適用外（既定値）。
        currentSort = SeriesSort.TITLE_ASC,
        readStatusFilter = ReadStatusFilter.ALL,
    )

@Composable
@Suppress("LongParameterList")
private fun HomeScaffoldContent(
    state: HomeUiState,
    inProgress: LazyPagingItems<Book>,
    read: LazyPagingItems<Book>,
    imageLoaderFor: (Server) -> coil3.ImageLoader,
    onAddServer: () -> Unit,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
    onOpenLibrary: (serverId: String, libraryId: String) -> Unit,
    onMenuClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar {
                HomeTopBarContent(
                    state = state,
                    onOpenLibrary = onOpenLibrary,
                    onMenuClick = onMenuClick,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit
                state.noServer -> NoServer(onAddServer = onAddServer)
                state.selectedServer != null -> {
                    val imageLoader = imageLoaderFor(state.selectedServer)
                    HomeSections(
                        inProgress = inProgress,
                        read = read,
                        imageLoader = imageLoader,
                        onOpenBook = onOpenBook,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBarContent(
    state: HomeUiState,
    onOpenLibrary: (serverId: String, libraryId: String) -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.home_menu_content_description))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.home_title),
            style = KomrdTheme.typography.h3,
            modifier = Modifier.weight(1f),
        )
        Button(
            text = stringResource(R.string.home_library_button),
            variant = ButtonVariant.Ghost,
            // サーバ未選択時は空serverIdで遷移→Library側がactiveId/先頭にフォールバック(既存の引数なし遷移と同等)。
            onClick = { onOpenLibrary(state.selectedServer?.id.orEmpty(), "") },
        )
    }
}

@Composable
private fun NoServer(onAddServer: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        dev.komrd.core.designsystem.components.Button(
            text = stringResource(R.string.home_add_server_button),
            variant = dev.komrd.core.designsystem.components.ButtonVariant.Primary,
            onClick = onAddServer,
        )
    }
}

@Composable
private fun HomeSections(
    inProgress: LazyPagingItems<Book>,
    read: LazyPagingItems<Book>,
    imageLoader: coil3.ImageLoader,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "header-in-progress") { SectionHeader(stringResource(R.string.home_continue_reading_header)) }
        item(key = "row-in-progress") {
            BookRow(
                items = inProgress,
                imageLoader = imageLoader,
                onOpenBook = onOpenBook,
            )
        }
        item(key = "header-read") { SectionHeader(stringResource(R.string.home_recently_read_header)) }
        item(key = "row-read") {
            BookRow(
                items = read,
                imageLoader = imageLoader,
                onOpenBook = onOpenBook,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = KomrdTheme.typography.h3,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun BookRow(
    items: LazyPagingItems<Book>,
    imageLoader: coil3.ImageLoader,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items.itemCount, key = { idx -> items.peek(idx)?.id ?: idx }) { idx ->
            val book = items[idx] ?: return@items
            BookCard(book = book, imageLoader = imageLoader, onClick = { onOpenBook(book.serverId, book.id) })
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    imageLoader: coil3.ImageLoader,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(CardWidth)) {
        Surface(
            onClick = onClick,
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
        ) {
            AsyncImage(
                model = book.thumbnailUrl,
                contentDescription = book.name,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(THUMBNAIL_ASPECT)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(12.dp),
                        ),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.name,
            style = KomrdTheme.typography.body3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
