package dev.komrd.core.model

data class ServerSettings(
    val deleteEmptyCollections: Boolean? = null,
    val deleteEmptyReadLists: Boolean? = null,
    val taskPoolSize: Int? = null,
    val rememberMeDurationDays: Long? = null,
    val renewRememberMeKey: Boolean? = null,
    val koboPort: Int? = null,
    val koboProxy: Boolean? = null,
    val thumbnailSize: String? = null,
    val serverPort: Int? = null,
    val serverContextPath: String? = null,
)

data class SettingsUpdate(
    val deleteEmptyCollections: Boolean? = null,
    val deleteEmptyReadLists: Boolean? = null,
    val taskPoolSize: Int? = null,
    val rememberMeDurationDays: Long? = null,
    val renewRememberMeKey: Boolean? = null,
    val koboPort: Int? = null,
    val koboProxy: Boolean? = null,
    val thumbnailSize: String? = null,
    val serverPort: Int? = null,
    val serverContextPath: String? = null,
) {
    companion object {
        /**
         * [original]→[updated]の差分だけをセットした[SettingsUpdate]を生成する。
         * 同値項目はnull（PATCH送信対象外）とし、変更項目のみ残す（差分PATCH要件）。
         */
        fun diff(
            original: ServerSettings,
            updated: ServerSettings,
        ): SettingsUpdate =
            SettingsUpdate(
                deleteEmptyCollections = diffField(original.deleteEmptyCollections, updated.deleteEmptyCollections),
                deleteEmptyReadLists = diffField(original.deleteEmptyReadLists, updated.deleteEmptyReadLists),
                taskPoolSize = diffField(original.taskPoolSize, updated.taskPoolSize),
                rememberMeDurationDays = diffField(original.rememberMeDurationDays, updated.rememberMeDurationDays),
                renewRememberMeKey = diffField(original.renewRememberMeKey, updated.renewRememberMeKey),
                koboPort = diffField(original.koboPort, updated.koboPort),
                koboProxy = diffField(original.koboProxy, updated.koboProxy),
                thumbnailSize = diffField(original.thumbnailSize, updated.thumbnailSize),
                serverPort = diffField(original.serverPort, updated.serverPort),
                serverContextPath = diffField(original.serverContextPath, updated.serverContextPath),
            )

        /** updatedがoriginalと異なる場合のみ其の値を返し、同値ならnull（PATCH送信対象外）。 */
        private fun <T> diffField(
            original: T?,
            updated: T?,
        ): T? = updated?.takeIf { it != original }
    }

    /** 変更分が1件も無いときtrue（PATCH送信不要の判定用）。 */
    val isEmpty: Boolean
        get() =
            deleteEmptyCollections == null &&
                deleteEmptyReadLists == null &&
                taskPoolSize == null &&
                rememberMeDurationDays == null &&
                renewRememberMeKey == null &&
                koboPort == null &&
                koboProxy == null &&
                thumbnailSize == null &&
                serverPort == null &&
                serverContextPath == null
}
