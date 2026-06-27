package dev.komrd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.datastore.OnboardingStore
import dev.komrd.core.datastore.ReadingDirectionStore
import dev.komrd.core.model.ReadingDirection
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingDirectionOnboardingViewModel
    @Inject
    constructor(
        private val readingDirectionStore: ReadingDirectionStore,
        private val onboardingStore: OnboardingStore,
    ) : ViewModel() {
        /** 選択を確定。directionが非nullならグローバル既定へ保存し、常に初回完了をマークする。 */
        fun confirm(direction: ReadingDirection?) {
            viewModelScope.launch {
                if (direction != null) {
                    readingDirectionStore.set(direction)
                }
                onboardingStore.markReadingDirectionFirstLaunchDone()
            }
        }
    }
