package dev.komrd.core.network

internal fun String.trimmedBaseUrl(): String = trimEnd('/')

/** Retrofit の `baseUrl` 用に末尾スラッシュを保証した形。 */
internal fun String.retrofitBaseUrl(): String = trimmedBaseUrl() + "/"
