package dev.komrd.core.prefetch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNetworkPolicy(
    initial: NetworkDecision = NetworkDecision.Run,
) : NetworkPolicy {
    private val _decisions = MutableStateFlow(initial)
    override val decisions: Flow<NetworkDecision> = _decisions.asStateFlow()

    /** 決定を更新（Controllerがcollectしてpause/resumeへ反映）。 */
    fun emit(decision: NetworkDecision) {
        _decisions.value = decision
    }
}
