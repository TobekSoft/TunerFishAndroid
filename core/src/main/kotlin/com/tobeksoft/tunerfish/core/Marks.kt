package com.tobeksoft.tunerfish.core

/**
 * The user's own marks on an artist or album, kept in Jellyfin's user data (never the library):
 * the heart is a perennial favourite; thumbs up is a like, what you're into now; thumbs down
 * hides it from browsing. Thumbs up and down are one field (Likes), so a liked thing can't be
 * hidden and hiding clears the like.
 */
data class Marks(val favourite: Boolean = false, val likes: Boolean? = null) {
    val liked: Boolean get() = likes == true
    val hidden: Boolean get() = likes == false

    fun withFavourite(on: Boolean) = copy(favourite = on)
    fun withLiked(on: Boolean) = copy(likes = if (on) true else if (liked) null else likes)
    fun withHidden(on: Boolean) = copy(likes = if (on) false else if (hidden) null else likes)

    companion object {
        val None = Marks()

        fun from(item: BaseItem) = item.userData?.let { Marks(it.isFavorite, it.likes) } ?: None

        /** What to show: a change made here and not yet sent beats what the server last said. */
        fun current(fromServer: Marks, pending: Marks?) = pending ?: fromServer
    }
}
