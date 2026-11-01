package com.artie.heart.sims.deploy

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A release as the read side lists it. */
data class Release(val app: String, val version: String, val status: String) {
    fun json(): Map<String, Any?> = mapOf("version" to version, "title" to "Release $version", "status" to status)
}

/**
 * The read side: each app's id by name, and every release published, a
 * projection of the apps' events. The releases list and the bookmark read
 * only this; the write side never does.
 */
class AppDirectory {
    private val ids = ConcurrentHashMap<String, Long>()
    private val names = ConcurrentHashMap<Long, String>()
    private val releases = CopyOnWriteArrayList<Release>()

    fun project(id: Long, event: AppEvent) {
        when (event) {
            is AppEvent.Imported -> {
                ids[event.name] = id
                names[id] = event.name
            }
            is AppEvent.ReleasePublished -> releases += Release(names.getValue(id), event.version, event.status)
            else -> Unit
        }
    }

    fun idOf(name: String): Long? = ids[name]

    fun releasesOf(app: String?): List<Release> = releases.filter { app == null || it.app == app }

    fun clear() {
        ids.clear()
        names.clear()
        releases.clear()
    }
}
