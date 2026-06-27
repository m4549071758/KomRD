package dev.komrd.core.prefetch

data class PrefetchEvictionConfig(
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS: Int = 3
        const val DEFAULT_MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

        /** 1日のミリ秒(aging判定用)。 */
        const val DAY_MS: Long = 24L * 60 * 60 * 1000
    }
}
