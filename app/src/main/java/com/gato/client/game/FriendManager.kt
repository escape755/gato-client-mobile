package com.gato.client.game

import androidx.compose.runtime.mutableStateListOf

/**
 * Minimal friend list (the PC client's FriendManager). Managed via the
 * ".friend add|remove|list" chat command; consulted by modules that
 * distinguish friends from enemies (e.g. PopCounter).
 */
object FriendManager {

    val friends = mutableStateListOf<String>()

    fun isInList(username: String): Boolean =
        friends.any { it.equals(username, ignoreCase = true) }

    fun add(username: String): Boolean {
        if (isInList(username)) return false
        friends.add(username)
        return true
    }

    fun remove(username: String): Boolean =
        friends.removeAll { it.equals(username, ignoreCase = true) }
}
