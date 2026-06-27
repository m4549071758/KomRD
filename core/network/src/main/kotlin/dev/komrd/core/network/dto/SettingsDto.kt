package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(
    val deleteEmptyCollections: Boolean? = null,
    val deleteEmptyReadLists: Boolean? = null,
    val taskPoolSize: Int? = null,
    val rememberMeDurationDays: Long? = null,
    val renewRememberMeKey: Boolean? = null,
    val koboPort: Int? = null,
    val koboProxy: Boolean? = null,
    val thumbnailSize: String? = null,
    val serverPort: SettingMultiSourceInteger? = null,
    val serverContextPath: SettingMultiSourceString? = null,
)

@Serializable
data class SettingMultiSourceInteger(
    val configurationSource: Int? = null,
    val databaseSource: Int? = null,
    val effectiveValue: Int? = null,
)

@Serializable
data class SettingMultiSourceString(
    val configurationSource: String? = null,
    val databaseSource: String? = null,
    val effectiveValue: String? = null,
)

@Serializable
data class SettingsUpdateDto(
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
