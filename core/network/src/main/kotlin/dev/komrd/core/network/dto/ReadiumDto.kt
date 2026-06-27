package dev.komrd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WPPublicationDto(
    @SerialName("@context") val context: String? = null,
    val metadata: WPMetadataDto = WPMetadataDto(),
    val links: List<WPLinkDto> = emptyList(),
    val images: List<WPLinkDto> = emptyList(),
    val readingOrder: List<WPLinkDto> = emptyList(),
    val resources: List<WPLinkDto> = emptyList(),
    val toc: List<WPLinkDto> = emptyList(),
    val landmarks: List<WPLinkDto> = emptyList(),
    val pageList: List<WPLinkDto> = emptyList(),
)

@Serializable
data class WPMetadataDto(
    val title: String? = null,
    val identifier: String? = null,
    val language: String? = null,
    val readingProgression: String? = null,
    val numberOfPages: Int? = null,
)

@Serializable
data class WPLinkDto(
    val href: String,
    val type: String? = null,
    val title: String? = null,
    val rel: String? = null,
    val templated: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val children: List<WPLinkDto> = emptyList(),
    val properties: Map<String, JsonElement>? = null,
)

@Serializable
data class R2LocatorDto(
    val href: String,
    val type: String,
    val title: String? = null,
    val locations: R2LocationDto? = null,
    val text: R2TextDto? = null,
)

@Serializable
data class R2LocationDto(
    val fragments: List<String> = emptyList(),
    val progression: Float? = null,
    val position: Int? = null,
    val totalProgression: Float? = null,
)

@Serializable
data class R2TextDto(
    val after: String? = null,
    val before: String? = null,
    val highlight: String? = null,
)

@Serializable
data class R2DeviceDto(
    val id: String,
    val name: String,
)

@Serializable
data class R2ProgressionDto(
    val modified: String,
    val device: R2DeviceDto,
    val locator: R2LocatorDto,
)

@Serializable
data class R2PositionsDto(
    val total: Int,
    val positions: List<R2LocatorDto> = emptyList(),
)
