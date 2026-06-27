package dev.komrd.core.network

import kotlinx.serialization.json.Json

val KomgaJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
