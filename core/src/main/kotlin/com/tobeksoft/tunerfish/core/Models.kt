package com.tobeksoft.tunerfish.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Minimal shapes of the Jellyfin API responses we use. Jellyfin sends PascalCase JSON.

@Serializable
data class AuthResult(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("User") val user: UserDto = UserDto(),
    @SerialName("ServerId") val serverId: String? = null,
)

/** /System/Info/Public: answers without signing in; Id identifies the server whatever address it's reached by. */
@Serializable
data class PublicSystemInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("ServerName") val serverName: String = "",
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<BaseItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

@Serializable
data class BaseItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,             // track number
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null, // disc number
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,          // 1 tick = 100 ns
    @SerialName("Artists") val artists: List<String>? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("AlbumArtists") val albumArtists: List<NameId>? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("Container") val container: String? = null,             // audio file format, e.g. "flac"
    @SerialName("MediaSources") val mediaSources: List<MediaSource>? = null, // asked for with fields=MediaSources
    @SerialName("UserData") val userData: UserItemData? = null,           // the signed-in user's own data
    @SerialName("ChildCount") val childCount: Int? = null,               // an album's tracks, with fields=ChildCount
) {
    val primaryImageTag: String? get() = imageTags?.get("Primary")
}

/** The user's own data on an item: the heart, the thumbs (Likes: true, false or neither) and plays. */
@Serializable
data class UserItemData(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Likes") val likes: Boolean? = null,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

@Serializable
data class NameId(
    @SerialName("Name") val name: String = "",
    @SerialName("Id") val id: String = "",
)

/** A track's file on the server; we only need its size. */
@Serializable
data class MediaSource(
    @SerialName("Size") val size: Long? = null,
)
