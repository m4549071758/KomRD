package dev.komrd.core.prefetch

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkPolicyDecisionTest {
    @Test
    fun offline_pausesRegardlessOfMobileSetting() {
        assertEquals(
            NetworkDecision.Pause(NetworkPauseReason.Offline),
            decide(NetworkTransport.Offline, PrefetchNetworkConfig(allowOnMobile = true)),
        )
        assertEquals(
            NetworkDecision.Pause(NetworkPauseReason.Offline),
            decide(NetworkTransport.Offline, PrefetchNetworkConfig(allowOnMobile = false)),
        )
    }

    @Test
    fun wifi_runsRegardlessOfMobileSetting() {
        assertEquals(
            NetworkDecision.Run,
            decide(NetworkTransport.Wifi, PrefetchNetworkConfig(allowOnMobile = true)),
        )
        assertEquals(
            NetworkDecision.Run,
            decide(NetworkTransport.Wifi, PrefetchNetworkConfig(allowOnMobile = false)),
        )
    }

    @Test
    fun other_runsRegardlessOfMobileSetting() {
        assertEquals(
            NetworkDecision.Run,
            decide(NetworkTransport.Other, PrefetchNetworkConfig(allowOnMobile = true)),
        )
        assertEquals(
            NetworkDecision.Run,
            decide(NetworkTransport.Other, PrefetchNetworkConfig(allowOnMobile = false)),
        )
    }

    @Test
    fun mobile_runsWhenAllowed() {
        assertEquals(
            NetworkDecision.Run,
            decide(NetworkTransport.Mobile, PrefetchNetworkConfig(allowOnMobile = true)),
        )
    }

    @Test
    fun mobile_pausedWhenBlocked() {
        assertEquals(
            NetworkDecision.Pause(NetworkPauseReason.MobileBlocked),
            decide(NetworkTransport.Mobile, PrefetchNetworkConfig(allowOnMobile = false)),
        )
    }

    @Test
    fun defaultConfig_allowsMobile() {
        assertEquals(NetworkDecision.Run, decide(NetworkTransport.Mobile, PrefetchNetworkConfig()))
    }
}
