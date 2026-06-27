package dev.komrd.core.prefetch

sealed interface PrefetchState {
    /** 取得対象が無いか全件完了。 */
    data object Idle : PrefetchState

    /**
     * 取得進行中。[remaining]=未完了件数・[total]=Window内総件数。
     */
    data class Running(
        val remaining: Int,
        val total: Int,
    ) : PrefetchState
}
