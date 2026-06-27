@file:Suppress("TooManyFunctions")

package dev.komrd.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.AlertDialog
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.HorizontalDivider
import dev.komrd.core.designsystem.components.ListItem
import dev.komrd.core.designsystem.components.RadioButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Switch
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.card.Card
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.PrefetchCacheSummary
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.SpreadMode

@Suppress("LongMethod")
@Composable
fun SettingsRoute(
    onOpenServers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val readingDirection by viewModel.readingDirection.collectAsStateWithLifecycle()
    val spreadMode by viewModel.spreadMode.collectAsStateWithLifecycle()
    val currentLocale by viewModel.currentLocale.collectAsStateWithLifecycle()
    var showDirectionDialog by remember { mutableStateOf(false) }
    var showSpreadDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar() },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsTopItems(
                readingDirection = readingDirection,
                spreadMode = spreadMode,
                onOpenServers = onOpenServers,
                onShowDirectionDialog = { showDirectionDialog = true },
                onShowSpreadDialog = { showSpreadDialog = true },
                onShowLanguageDialog = { showLanguageDialog = true },
                viewModel = viewModel,
            )
        }
    }

    if (showDirectionDialog) {
        ReadingDirectionDialog(
            current = readingDirection,
            onDismiss = { showDirectionDialog = false },
            onSelect = { direction ->
                viewModel.setReadingDirection(direction)
                showDirectionDialog = false
            },
        )
    }

    if (showLanguageDialog) {
        val activity = LocalContext.current as? android.app.Activity
        LanguageDialog(
            current = currentLocale,
            onDismiss = { showLanguageDialog = false },
            onSelect = { tag ->
                viewModel.setAppLocale(tag)
                showLanguageDialog = false
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    activity?.recreate()
                }
            },
        )
    }

    if (showSpreadDialog) {
        SpreadModeDialog(
            current = spreadMode,
            onDismiss = { showSpreadDialog = false },
            onSelect = { mode ->
                viewModel.setSpreadMode(mode)
                showSpreadDialog = false
            },
        )
    }
}

@Composable
private fun SettingsTopBar() {
    TopBar {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = KomrdTheme.typography.h3,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsTopItems(
    readingDirection: ReadingDirection,
    spreadMode: SpreadMode,
    onOpenServers: () -> Unit,
    onShowDirectionDialog: () -> Unit,
    onShowSpreadDialog: () -> Unit,
    onShowLanguageDialog: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val currentLocale by viewModel.currentLocale.collectAsStateWithLifecycle()
    SettingsSectionHeader(stringResource(R.string.settings_section_general))
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_server_management_title)) },
            supportingContent = { Text(stringResource(R.string.settings_server_management_supporting)) },
            modifier = Modifier.clickable(onClick = onOpenServers),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_reading_direction_title)) },
            supportingContent = { Text(readingDirectionLabel(readingDirection)) },
            modifier = Modifier.clickable(onClick = onShowDirectionDialog),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_spread_mode_title)) },
            supportingContent = { Text(spreadModeLabel(spreadMode)) },
            modifier = Modifier.clickable(onClick = onShowSpreadDialog),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language_title)) },
            supportingContent = { Text(localeLabel(currentLocale)) },
            modifier = Modifier.clickable(onClick = onShowLanguageDialog),
        )
    }
    PrefetchSection(viewModel = viewModel)
    CacheSection(viewModel = viewModel)
    BackgroundSection(viewModel = viewModel)
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = KomrdTheme.typography.label2,
        color = KomrdTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ReadingDirectionDialog(
    current: ReadingDirection,
    onDismiss: () -> Unit,
    onSelect: (ReadingDirection) -> Unit,
) {
    SelectionDialog(
        title = stringResource(R.string.settings_reading_direction_dialog_title),
        options = ReadingDirection.entries.toList(),
        current = current,
        labelOf = { readingDirectionLabel(it) },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
internal fun readingDirectionLabel(direction: ReadingDirection): String =
    stringResource(
        when (direction) {
            ReadingDirection.LEFT_TO_RIGHT -> R.string.reading_direction_left_to_right
            ReadingDirection.RIGHT_TO_LEFT -> R.string.reading_direction_right_to_left
            ReadingDirection.VERTICAL -> R.string.reading_direction_vertical
            ReadingDirection.WEBTOON -> R.string.reading_direction_webtoon
        },
    )

@Composable
private fun SpreadModeDialog(
    current: SpreadMode,
    onDismiss: () -> Unit,
    onSelect: (SpreadMode) -> Unit,
) {
    SelectionDialog(
        title = stringResource(R.string.settings_spread_mode_dialog_title),
        options = SpreadMode.entries.toList(),
        current = current,
        labelOf = { spreadModeLabel(it) },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    current: T,
    labelOf: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(labelOf(option)) },
                        leadingContent = {
                            RadioButton(
                                selected = option == current,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable { onSelect(option) },
                        contentPadding = SelectionDialogItemPadding,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

private val SelectionDialogItemPadding =
    dev.komrd.core.designsystem.components.ListItemPadding(
        horizontal = 0.dp,
        vertical = 4.dp,
    )

@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    SelectionDialog(
        title = stringResource(R.string.settings_language_dialog_title),
        options = listOf("system", "en", "ja"),
        current = current,
        labelOf = { localeLabel(it) },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun localeLabel(tag: String): String =
    stringResource(
        when (tag) {
            "en" -> R.string.settings_language_en
            "ja" -> R.string.settings_language_ja
            else -> R.string.settings_language_system
        },
    )

@Composable
internal fun spreadModeLabel(mode: SpreadMode): String =
    stringResource(
        when (mode) {
            SpreadMode.ALWAYS -> R.string.spread_mode_always
            SpreadMode.LANDSCAPE_ONLY -> R.string.spread_mode_landscape_only
            SpreadMode.OFF -> R.string.spread_mode_off
        },
    )

@Composable
private fun nextBooksLabel(count: Int): String =
    if (count == 0) {
        stringResource(R.string.prefetch_next_books_current_only)
    } else {
        stringResource(R.string.prefetch_next_books_count, count)
    }

@Composable
private fun retentionDaysLabel(days: Int): String = stringResource(R.string.prefetch_retention_days_count, days)

private fun maxBytesLabel(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "${bytes / 1_073_741_824L}GB"
        else -> "${bytes / 1_048_576L}MB"
    }

@Suppress("LongMethod")
@Composable
private fun PrefetchSection(viewModel: SettingsViewModel) {
    val enabled by viewModel.prefetchEnabled.collectAsStateWithLifecycle()
    val nextBooks by viewModel.prefetchNextBooks.collectAsStateWithLifecycle()
    val parallelism by viewModel.prefetchParallelism.collectAsStateWithLifecycle()
    val retentionDays by viewModel.prefetchRetentionDays.collectAsStateWithLifecycle()
    val maxBytes by viewModel.prefetchMaxBytes.collectAsStateWithLifecycle()
    val allowOnMobile by viewModel.prefetchAllowOnMobile.collectAsStateWithLifecycle()
    var showNextBooksDialog by remember { mutableStateOf(false) }
    var showParallelismDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showMaxBytesDialog by remember { mutableStateOf(false) }

    SettingsSectionHeader(stringResource(R.string.settings_section_prefetch))
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_title)) },
            supportingContent = { Text(stringResource(R.string.prefetch_supporting)) },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = { viewModel.setPrefetchEnabled(it) },
                )
            },
            modifier = Modifier.clickable(onClick = { viewModel.setPrefetchEnabled(!enabled) }),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        val subItemModifier = if (enabled) Modifier else Modifier.alpha(0.5f)

        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_next_books_title)) },
            supportingContent = { Text(nextBooksLabel(nextBooks)) },
            modifier = subItemModifier.clickable(enabled = enabled) { showNextBooksDialog = true },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_parallelism_title)) },
            supportingContent = { Text("$parallelism") },
            modifier = subItemModifier.clickable(enabled = enabled) { showParallelismDialog = true },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_retention_days_title)) },
            supportingContent = { Text(retentionDaysLabel(retentionDays)) },
            modifier = subItemModifier.clickable(enabled = enabled) { showRetentionDialog = true },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_cache_size_title)) },
            supportingContent = { Text(maxBytesLabel(maxBytes)) },
            modifier = subItemModifier.clickable(enabled = enabled) { showMaxBytesDialog = true },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.prefetch_mobile_data_title)) },
            supportingContent = { Text(stringResource(R.string.prefetch_mobile_data_supporting)) },
            trailingContent = {
                Switch(
                    checked = allowOnMobile,
                    onCheckedChange = { viewModel.setPrefetchAllowOnMobile(it) },
                    enabled = enabled,
                )
            },
            modifier =
                subItemModifier.clickable(enabled = enabled) {
                    viewModel.setPrefetchAllowOnMobile(!allowOnMobile)
                },
        )
    }

    if (showNextBooksDialog) {
        SelectionDialog(
            title = stringResource(R.string.prefetch_next_books_dialog_title),
            options = listOf(0, 1, 2, 3),
            current = nextBooks,
            labelOf = { nextBooksLabel(it) },
            onDismiss = { showNextBooksDialog = false },
            onSelect = { value ->
                viewModel.setPrefetchNextBooks(value)
                showNextBooksDialog = false
            },
        )
    }
    if (showParallelismDialog) {
        SelectionDialog(
            title = stringResource(R.string.prefetch_parallelism_dialog_title),
            options = listOf(1, 2, 3, 4),
            current = parallelism,
            labelOf = { "$it" },
            onDismiss = { showParallelismDialog = false },
            onSelect = { value ->
                viewModel.setPrefetchParallelism(value)
                showParallelismDialog = false
            },
        )
    }
    if (showRetentionDialog) {
        SelectionDialog(
            title = stringResource(R.string.prefetch_retention_days_dialog_title),
            options = listOf(1, 3, 7, 14),
            current = retentionDays,
            labelOf = { retentionDaysLabel(it) },
            onDismiss = { showRetentionDialog = false },
            onSelect = { value ->
                viewModel.setPrefetchRetentionDays(value)
                showRetentionDialog = false
            },
        )
    }
    if (showMaxBytesDialog) {
        SelectionDialog(
            title = stringResource(R.string.prefetch_cache_size_dialog_title),
            options = listOf(536_870_912L, 1_073_741_824L, 2_147_483_648L, 4_294_967_296L, 8_589_934_592L),
            current = maxBytes,
            labelOf = { maxBytesLabel(it) },
            onDismiss = { showMaxBytesDialog = false },
            onSelect = { value ->
                viewModel.setPrefetchMaxBytes(value)
                showMaxBytesDialog = false
            },
        )
    }
}

@Composable
private fun CacheSection(viewModel: SettingsViewModel) {
    val summaries by viewModel.cacheSummaries.collectAsStateWithLifecycle()

    SettingsSectionHeader(stringResource(R.string.settings_section_cache))
    Card(modifier = Modifier.fillMaxWidth()) {
        if (summaries.isEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.cache_empty_title)) },
                supportingContent = { Text(stringResource(R.string.cache_empty_supporting)) },
            )
        } else {
            summaries.forEachIndexed { index, summary ->
                CacheSummaryItem(
                    summary = summary,
                    onPurge = { viewModel.purgeCache(summary.serverId, summary.bookId) },
                )
                if (index < summaries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun CacheSummaryItem(
    summary: PrefetchCacheSummary,
    onPurge: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(summary.bookId) },
        supportingContent = {
            val pageText = summary.pageRangesText()
            val entriesSuffix =
                stringResource(
                    R.string.cache_item_entries_suffix,
                    summary.entryCount,
                    cacheBytesLabel(summary.totalBytes),
                )
            val detail =
                buildString {
                    if (pageText.isNotEmpty()) {
                        append(stringResource(R.string.cache_item_pages_prefix, pageText))
                        append(" / ")
                    }
                    append(entriesSuffix)
                }
            Text(detail)
        },
        trailingContent = {
            Button(
                text = stringResource(R.string.cache_delete_button),
                variant = ButtonVariant.Ghost,
                onClick = onPurge,
            )
        },
    )
}

private fun cacheBytesLabel(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "${bytes / 1_073_741_824L}GB"
        bytes >= 1_048_576L -> "${bytes / 1_048_576L}MB"
        else -> "${bytes / 1_024L}kB"
    }

@Composable
private fun BackgroundSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val notificationsGranted by viewModel.notificationsGranted.collectAsStateWithLifecycle()
    val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshSystemState() }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshSystemState()
        }
    val batteryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshSystemState()
        }
    var showBatteryRationale by remember { mutableStateOf(false) }

    SettingsSectionHeader(stringResource(R.string.settings_section_background))
    Card(modifier = Modifier.fillMaxWidth()) {
        BackgroundListItems(
            notificationsGranted = notificationsGranted,
            batteryIgnored = batteryIgnored,
            onLaunchNotificationsPermission = {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onShowBatteryRationale = { showBatteryRationale = true },
        )
    }

    if (showBatteryRationale) {
        BatteryRationaleDialog(
            onDismiss = { showBatteryRationale = false },
            onContinue = {
                showBatteryRationale = false
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                runCatching { batteryLauncher.launch(intent) }.onFailure { e ->
                    // 主にマニフェスト欠落(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS未宣言)によるSecurityException。
                    // 無声で握り潰すと「適用を押しても何も起きない」状態になるため、ログに残す。
                    Log.e("SettingsRoute", "Failed to request ignore battery optimizations", e)
                }
            },
        )
    }
}

@Composable
private fun BackgroundListItems(
    notificationsGranted: Boolean,
    batteryIgnored: Boolean,
    onLaunchNotificationsPermission: () -> Unit,
    onShowBatteryRationale: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications_title)) },
            supportingContent = { Text(stringResource(R.string.settings_notifications_supporting)) },
            trailingContent = {
                Text(
                    stringResource(
                        if (notificationsGranted) {
                            R.string.settings_notifications_granted
                        } else {
                            R.string.settings_notifications_not_granted
                        },
                    ),
                )
            },
            modifier = Modifier.clickable(onClick = onLaunchNotificationsPermission),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_battery_optimization_title)) },
        supportingContent = { Text(stringResource(R.string.settings_battery_optimization_supporting)) },
        trailingContent = {
            Text(
                stringResource(
                    if (batteryIgnored) {
                        R.string.settings_battery_optimization_granted
                    } else {
                        R.string.settings_battery_optimization_not_granted
                    },
                ),
            )
        },
        modifier = Modifier.clickable(onClick = onShowBatteryRationale),
    )
}

@Composable
private fun BatteryRationaleDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_battery_optimization_title)) },
        text = { Text(stringResource(R.string.settings_battery_optimization_rationale)) },
        confirmButton = {
            Button(
                text = stringResource(R.string.common_continue),
                variant = ButtonVariant.Ghost,
                onClick = onContinue,
            )
        },
        dismissButton = {
            Button(
                text = stringResource(R.string.common_cancel),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
            )
        },
    )
}
