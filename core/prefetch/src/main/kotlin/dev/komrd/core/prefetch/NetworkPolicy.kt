package dev.komrd.core.prefetch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

sealed interface NetworkDecision {
    /** 実行可。回線がWi-Fi/Other、または設定で許可されたモバイル。 */
    data object Run : NetworkDecision

    /** 一時停止。オフライン、またはモバイルが設定で抑制されている。 */
    data class Pause(
        val reason: NetworkPauseReason,
    ) : NetworkDecision
}

enum class NetworkPauseReason {
    /** 回線なし。復帰で自動再開。 */
    Offline,

    /** モバイル回線だが設定(`PrefetchNetworkConfig.allowOnMobile`)で抑制中。 */
    MobileBlocked,
}

/**
 * 現在の回線種別。本番実装が`NetworkCapabilities`から導出し、[decide]で実行可否へ変換する。
 */
enum class NetworkTransport {
    /** 回線なし。 */
    Offline,

    /** Wi-Fi。 */
    Wifi,

    /** モバイル(CELLULAR)。 */
    Mobile,

    /** その他(Ethernet等)。実行可扱い。 */
    Other,
}

data class PrefetchNetworkConfig(
    val allowOnMobile: Boolean = true,
)

/**
 * [NetworkTransport] + [PrefetchNetworkConfig] → [NetworkDecision]の純粋関数（単体テスト対象）。
 *
 * Offline→Pause(Offline) / Wifi・Other→Run / Mobile→[PrefetchNetworkConfig.allowOnMobile]で分岐。
 */
fun decide(
    transport: NetworkTransport,
    config: PrefetchNetworkConfig,
): NetworkDecision =
    when (transport) {
        NetworkTransport.Offline -> NetworkDecision.Pause(NetworkPauseReason.Offline)
        NetworkTransport.Wifi, NetworkTransport.Other -> NetworkDecision.Run
        NetworkTransport.Mobile ->
            if (config.allowOnMobile) {
                NetworkDecision.Run
            } else {
                NetworkDecision.Pause(NetworkPauseReason.MobileBlocked)
            }
    }

interface NetworkPolicy {
    val decisions: Flow<NetworkDecision>
}

/**
 * 「方針なし」を表す既定実装。何もemitしない（[PrefetchControllerImpl]の`pausedState`初期値=false=実行が維持される）。
 * [PrefetchControllerImpl]のデフォルト引数として用い、明示的な[pause][PrefetchController.pause]/
 * [resume][PrefetchController.resume]を上書きしない。policyを意識しない既存テストを維持するためのもの。
 */
object NoOpNetworkPolicy : NetworkPolicy {
    override val decisions: Flow<NetworkDecision> = emptyFlow()
}
