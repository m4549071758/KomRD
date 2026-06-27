package dev.komrd.core.data.server

import dev.komrd.core.model.AgeRestriction
import dev.komrd.core.model.ServerSettings
import dev.komrd.core.model.SettingsUpdate
import dev.komrd.core.model.UserAccount
import dev.komrd.core.network.dto.SettingsDto
import dev.komrd.core.network.dto.SettingsUpdateDto
import dev.komrd.core.network.dto.UserDto

internal fun SettingsDto.toDomain(): ServerSettings =
    ServerSettings(
        deleteEmptyCollections = deleteEmptyCollections,
        deleteEmptyReadLists = deleteEmptyReadLists,
        taskPoolSize = taskPoolSize,
        rememberMeDurationDays = rememberMeDurationDays,
        renewRememberMeKey = renewRememberMeKey,
        koboPort = koboPort,
        koboProxy = koboProxy,
        thumbnailSize = thumbnailSize,
        serverPort = serverPort?.effectiveValue,
        serverContextPath = serverContextPath?.effectiveValue,
    )

internal fun SettingsUpdate.toDto(): SettingsUpdateDto =
    SettingsUpdateDto(
        deleteEmptyCollections = deleteEmptyCollections,
        deleteEmptyReadLists = deleteEmptyReadLists,
        taskPoolSize = taskPoolSize,
        rememberMeDurationDays = rememberMeDurationDays,
        renewRememberMeKey = renewRememberMeKey,
        koboPort = koboPort,
        koboProxy = koboProxy,
        thumbnailSize = thumbnailSize,
        serverPort = serverPort,
        serverContextPath = serverContextPath,
    )

internal fun UserDto.toDomain(): UserAccount =
    UserAccount(
        id = id,
        email = email,
        roles = roles.toSet(),
        sharedAllLibraries = sharedAllLibraries ?: false,
        sharedLibrariesIds = sharedLibrariesIds,
        ageRestriction = ageRestriction?.toDomain(),
    )

internal fun dev.komrd.core.network.dto.AgeRestrictionDto.toDomain(): AgeRestriction? =
    age?.let { a -> restriction?.let { r -> AgeRestriction(age = a, restriction = r) } }
