package dev.komrd.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.NavigationBar
import dev.komrd.core.designsystem.components.NavigationBarItem
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.feature.home.HomeRoute
import dev.komrd.feature.library.BookDetailRoute
import dev.komrd.feature.library.CollectionDetailRoute
import dev.komrd.feature.library.LibraryRoute
import dev.komrd.feature.library.ReadListDetailRoute
import dev.komrd.feature.library.SeriesDetailRoute
import dev.komrd.feature.reader.ReaderRoute
import dev.komrd.feature.readerepub.EpubReaderRoute
import dev.komrd.feature.server.ServerRoute
import dev.komrd.feature.server.ServerSettingsRoute
import dev.komrd.feature.settings.OnboardingGateViewModel
import dev.komrd.feature.settings.ReadingDirectionOnboardingRoute
import dev.komrd.feature.settings.SettingsRoute
import dev.komrd.search.SearchRoute

@Composable
fun KomrdApp() {
    val navController = rememberNavController()
    val gateViewModel: OnboardingGateViewModel = hiltViewModel()
    val shouldShowOnboarding by gateViewModel.shouldShowOnboarding.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onOnboarding = currentDestination?.hasRoute<ReadingDirectionOnboardingDestination>() == true
    LaunchedEffect(shouldShowOnboarding) {
        if (shouldShowOnboarding && !onOnboarding) {
            navController.navigate(ReadingDirectionOnboardingDestination) { launchSingleTop = true }
        }
    }
    // フォーム入力中（ServersDestination等）にタブ切替できないようにする。
    val isTopLevel =
        TopLevelDestination.entries.any {
            currentDestination?.hasRoute(it.route::class) == true
        }
    Scaffold(
        bottomBar = { if (isTopLevel) KomrdBottomBar(navController) },
    ) { padding ->
        KomrdNavHost(
            navController = navController,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun KomrdNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier,
    ) {
        composable<HomeDestination> { homeScreen(navController) }
        composable<LibraryDestination> { libraryScreen(navController) }
        composable<SearchDestination> {
            SearchRoute(onAddServer = { navController.navigate(ServersDestination) })
        }
        composable<SettingsDestination> {
            SettingsRoute(onOpenServers = { navController.navigate(ServersDestination) })
        }
        composable<ServersDestination> {
            ServerRoute(
                onBack = { navController.popBackStack() },
                onOpenSettings = { server ->
                    navController.navigate(ServerSettingsDestination(serverId = server.id))
                },
            )
        }
        composable<ServerSettingsDestination> { backStackEntry ->
            val dest = backStackEntry.toRoute<ServerSettingsDestination>()
            ServerSettingsRoute(
                serverId = dest.serverId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ReadingDirectionOnboardingDestination> {
            ReadingDirectionOnboardingRoute(
                onDone = {
                    navController.navigate(HomeDestination) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        seriesDetailScreen(navController)
        collectionDetailScreen(navController)
        readListDetailScreen(navController)
        bookDetailScreen(navController)
        readerScreen(navController)
        epubReaderScreen(navController)
    }
}

@Composable
private fun homeScreen(navController: NavHostController) {
    HomeRoute(
        onAddServer = { navController.navigate(ServersDestination) },
        onOpenBook = { serverId, bookId ->
            navController.navigate(BookDetailDestination(serverId = serverId, bookId = bookId))
        },
        onOpenLibrary = { serverId, libraryId ->
            navController.navigate(LibraryDestination(serverId = serverId, libraryId = libraryId))
        },
        onOpenCollection = { collection ->
            navController.navigate(
                CollectionDetailDestination(
                    serverId = collection.serverId,
                    collectionId = collection.id,
                    collectionName = collection.name,
                ),
            )
        },
        onOpenReadList = { readList ->
            navController.navigate(
                ReadListDetailDestination(
                    serverId = readList.serverId,
                    readListId = readList.id,
                    readListName = readList.name,
                ),
            )
        },
    )
}

@Composable
private fun libraryScreen(navController: NavHostController) {
    LibraryRoute(
        onAddServer = { navController.navigate(ServersDestination) },
        onOpenSeries = { series ->
            navController.navigate(
                SeriesDetailDestination(
                    serverId = series.serverId,
                    seriesId = series.id,
                    seriesName = series.name,
                ),
            )
        },
        onOpenCollection = { collection ->
            navController.navigate(
                CollectionDetailDestination(
                    serverId = collection.serverId,
                    collectionId = collection.id,
                    collectionName = collection.name,
                ),
            )
        },
        onOpenReadList = { readList ->
            navController.navigate(
                ReadListDetailDestination(
                    serverId = readList.serverId,
                    readListId = readList.id,
                    readListName = readList.name,
                ),
            )
        },
    )
}

private fun NavGraphBuilder.seriesDetailScreen(navController: NavHostController) {
    composable<SeriesDetailDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<SeriesDetailDestination>()
        SeriesDetailRoute(
            serverId = dest.serverId,
            seriesId = dest.seriesId,
            seriesName = dest.seriesName,
            onBack = { navController.popBackStack() },
            onOpenBook = { serverId, bookId ->
                navController.navigate(BookDetailDestination(serverId = serverId, bookId = bookId))
            },
        )
    }
}

private fun NavGraphBuilder.collectionDetailScreen(navController: NavHostController) {
    composable<CollectionDetailDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<CollectionDetailDestination>()
        CollectionDetailRoute(
            serverId = dest.serverId,
            collectionId = dest.collectionId,
            collectionName = dest.collectionName,
            onBack = { navController.popBackStack() },
            onOpenSeries = { series ->
                navController.navigate(
                    SeriesDetailDestination(
                        serverId = series.serverId,
                        seriesId = series.id,
                        seriesName = series.name,
                    ),
                )
            },
        )
    }
}

private fun NavGraphBuilder.readListDetailScreen(navController: NavHostController) {
    composable<ReadListDetailDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<ReadListDetailDestination>()
        ReadListDetailRoute(
            serverId = dest.serverId,
            readListId = dest.readListId,
            readListName = dest.readListName,
            onBack = { navController.popBackStack() },
            onOpenBook = { serverId, bookId, readListId ->
                navController.navigate(
                    BookDetailDestination(serverId = serverId, bookId = bookId, readListId = readListId),
                )
            },
        )
    }
}

private fun NavGraphBuilder.bookDetailScreen(navController: NavHostController) {
    composable<BookDetailDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<BookDetailDestination>()
        BookDetailRoute(
            serverId = dest.serverId,
            bookId = dest.bookId,
            onBack = { navController.popBackStack() },
            onRead = { serverId, bookId, isEpub ->
                val route =
                    if (isEpub) {
                        EpubReaderDestination(serverId = serverId, bookId = bookId, readListId = dest.readListId)
                    } else {
                        ReaderDestination(serverId = serverId, bookId = bookId, readListId = dest.readListId)
                    }
                navController.navigate(route)
            },
        )
    }
}

private fun NavGraphBuilder.readerScreen(navController: NavHostController) {
    composable<ReaderDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<ReaderDestination>()
        ReaderRoute(
            serverId = dest.serverId,
            bookId = dest.bookId,
            readListId = dest.readListId,
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.epubReaderScreen(navController: NavHostController) {
    composable<EpubReaderDestination> { backStackEntry ->
        val dest = backStackEntry.toRoute<EpubReaderDestination>()
        EpubReaderRoute(
            serverId = dest.serverId,
            bookId = dest.bookId,
            readListId = dest.readListId,
            onBack = { navController.popBackStack() },
        )
    }
}

@Composable
private fun KomrdBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
            val label = stringResource(dest.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
