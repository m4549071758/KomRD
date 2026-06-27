package dev.komrd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.OnboardingStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OnboardingGateViewModel
    @Inject
    constructor(
        serverRepository: ServerRepository,
        onboardingStore: OnboardingStore,
    ) : ViewModel() {
        val shouldShowOnboarding: StateFlow<Boolean> =
            combine(
                serverRepository.servers.map { it.isNotEmpty() },
                onboardingStore.readingDirectionFirstLaunchDone,
            ) { hasServers, done -> hasServers && !done }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )
    }
