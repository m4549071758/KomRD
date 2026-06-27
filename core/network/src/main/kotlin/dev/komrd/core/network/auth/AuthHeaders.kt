package dev.komrd.core.network.auth

internal const val X_AUTH_TOKEN = "X-Auth-Token"
internal const val X_API_KEY = "X-API-Key"
private const val AUTHORIZATION = "Authorization"
private const val SESSION_REQUEST_VALUE = "true"

internal fun okhttp3.Request.Builder.basicSessionLogin(
    username: String,
    password: String,
) = header(AUTHORIZATION, okhttp3.Credentials.basic(username, password))
    .header(X_AUTH_TOKEN, SESSION_REQUEST_VALUE)

internal fun okhttp3.Request.isBasicSessionLoginAttempt(): Boolean = header(X_AUTH_TOKEN) == SESSION_REQUEST_VALUE

internal fun okhttp3.Request.Builder.sessionToken(token: String) = header(X_AUTH_TOKEN, token)

internal fun okhttp3.Request.Builder.apiKey(key: String) = header(X_API_KEY, key)
