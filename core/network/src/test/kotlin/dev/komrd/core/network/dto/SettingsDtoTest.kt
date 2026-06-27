package dev.komrd.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

    @Test
    fun settingsDto_deserializesScalarsAndMultiSource() {
        val payload =
            """
            {
              "deleteEmptyCollections": true,
              "deleteEmptyReadLists": false,
              "taskPoolSize": 8,
              "rememberMeDurationDays": 30,
              "renewRememberMeKey": true,
              "koboPort": 8083,
              "koboProxy": false,
              "thumbnailSize": "LARGE",
              "serverPort": {
                "configurationSource": 8080,
                "databaseSource": null,
                "effectiveValue": 8080
              },
              "serverContextPath": {
                "configurationSource": "/komga",
                "databaseSource": null,
                "effectiveValue": "/komga"
              }
            }
            """.trimIndent()

        val dto = json.decodeFromString<SettingsDto>(payload)

        assertEquals(true, dto.deleteEmptyCollections)
        assertEquals(false, dto.deleteEmptyReadLists)
        assertEquals(8, dto.taskPoolSize)
        assertEquals(30L, dto.rememberMeDurationDays)
        assertEquals(true, dto.renewRememberMeKey)
        assertEquals(8083, dto.koboPort)
        assertEquals(false, dto.koboProxy)
        assertEquals("LARGE", dto.thumbnailSize)
        assertEquals(8080, dto.serverPort?.effectiveValue)
        assertEquals(8080, dto.serverPort?.configurationSource)
        assertNull(dto.serverPort?.databaseSource)
        assertEquals("/komga", dto.serverContextPath?.effectiveValue)
        assertEquals("/komga", dto.serverContextPath?.configurationSource)
    }

    @Test
    fun settingsDto_deserializesWhenMultiSourceAndScalarsOmitted() {
        val payload = """{"deleteEmptyCollections": false}"""
        val dto = json.decodeFromString<SettingsDto>(payload)

        assertEquals(false, dto.deleteEmptyCollections)
        assertNull(dto.deleteEmptyReadLists)
        assertNull(dto.taskPoolSize)
        assertNull(dto.serverPort)
        assertNull(dto.serverContextPath)
    }

    @Test
    fun settingsDto_ignoresUnknownFields() {
        val payload = """{"deleteEmptyCollections": true, "unknownFuture": "x"}"""
        val dto = json.decodeFromString<SettingsDto>(payload)
        assertEquals(true, dto.deleteEmptyCollections)
    }

    @Test
    fun settingsUpdateDto_serializesOnlyNonNullDiffFields() {
        val update =
            SettingsUpdateDto(
                deleteEmptyCollections = true,
                taskPoolSize = 16,
                serverPort = 8081,
            )
        val encoded = json.encodeToString(SettingsUpdateDto.serializer(), update)
        // null項目は encodeDefaults=false により出力されない（差分PATCH要件）
        assertTrue("encoded: $encoded", encoded.contains("deleteEmptyCollections"))
        assertTrue("encoded: $encoded", encoded.contains("taskPoolSize"))
        assertTrue("encoded: $encoded", encoded.contains("serverPort"))
        assertTrue("encoded: $encoded", encoded.contains("8081"))
        assertTrue("encoded: $encoded", !encoded.contains("deleteEmptyReadLists"))
        assertTrue("encoded: $encoded", !encoded.contains("rememberMeDurationDays"))
        assertTrue("encoded: $encoded", !encoded.contains("serverContextPath"))
    }

    @Test
    fun settingsUpdateDto_emptyInstanceSerializesToEmptyObject() {
        val encoded = json.encodeToString(SettingsUpdateDto.serializer(), SettingsUpdateDto())
        assertEquals("{}", encoded)
    }

    @Test
    fun settingMultiSourceInteger_deserializesAllNull() {
        val payload = """{"effectiveValue": null}"""
        val dto = json.decodeFromString<SettingMultiSourceInteger>(payload)
        assertNull(dto.effectiveValue)
        assertNull(dto.configurationSource)
        assertNull(dto.databaseSource)
    }

    @Test
    fun userDto_deserializesAdminRolesAndAgeRestriction() {
        val payload =
            """
            {
              "id": "u1",
              "email": "admin@example.com",
              "roles": ["ADMIN", "USER"],
              "sharedAllLibraries": true,
              "sharedLibrariesIds": [],
              "labelsAllow": [],
              "labelsExclude": [],
              "ageRestriction": {"age": 18, "restriction": "ALLOW_ONLY"}
            }
            """.trimIndent()

        val dto = json.decodeFromString<UserDto>(payload)

        assertEquals("u1", dto.id)
        assertEquals("admin@example.com", dto.email)
        assertEquals(listOf("ADMIN", "USER"), dto.roles)
        assertEquals(true, dto.sharedAllLibraries)
        assertEquals(emptyList<String>(), dto.sharedLibrariesIds)
        assertEquals(18, dto.ageRestriction?.age)
        assertEquals("ALLOW_ONLY", dto.ageRestriction?.restriction)
    }

    @Test
    fun userDto_deserializesNonAdminWithSharedLibraryIdsAndNoAgeRestriction() {
        val payload =
            """
            {
              "id": "u2",
              "email": "user@example.com",
              "roles": ["USER"],
              "sharedAllLibraries": false,
              "sharedLibrariesIds": ["lib-1", "lib-2"]
            }
            """.trimIndent()

        val dto = json.decodeFromString<UserDto>(payload)

        assertEquals(listOf("USER"), dto.roles)
        assertEquals(false, dto.sharedAllLibraries)
        assertEquals(listOf("lib-1", "lib-2"), dto.sharedLibrariesIds)
        assertNull(dto.ageRestriction)
    }

    @Test
    fun userDto_deserializesEmptyRolesArray() {
        val payload = """{"id":"u3","roles":[]}"""
        val dto = json.decodeFromString<UserDto>(payload)
        assertEquals(emptyList<String>(), dto.roles)
        assertEquals("u3", dto.id)
    }

    @Test
    fun userDto_defaultsRolesAndSharedLibrariesWhenAbsent() {
        val payload = """{"id":"u4"}"""
        val dto = json.decodeFromString<UserDto>(payload)
        assertEquals(emptyList<String>(), dto.roles)
        assertEquals(emptyList<String>(), dto.sharedLibrariesIds)
        assertEquals(emptyList<String>(), dto.labelsAllow)
        assertEquals(emptyList<String>(), dto.labelsExclude)
        assertNull(dto.ageRestriction)
    }
}
