package dev.komrd.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.ReadingDirection

@Suppress("LongMethod")
@Composable
fun ReadingDirectionOnboardingRoute(
    onDone: () -> Unit,
    viewModel: ReadingDirectionOnboardingViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopBar {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reading_direction_onboarding_title),
                        style = KomrdTheme.typography.h3,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.reading_direction_onboarding_description))
            Spacer(Modifier.height(16.dp))
            ReadingDirection.entries.forEach { direction ->
                Button(
                    text = readingDirectionLabel(direction),
                    variant = ButtonVariant.PrimaryOutlined,
                    onClick = {
                        viewModel.confirm(direction)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                text = stringResource(R.string.reading_direction_onboarding_skip_left_to_right),
                variant = ButtonVariant.Primary,
                onClick = {
                    viewModel.confirm(null)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
