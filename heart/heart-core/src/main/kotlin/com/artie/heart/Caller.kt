package com.artie.heart

/**
 * Who asks: the credential the request being served carried, as its
 * Authorization header value, for a resource that acts on its caller's
 * behalf, as a gateway calls GitHub as each caller. A binding sets it
 * around every read, act and search ([during]); a resource reads it
 * ([authorization]). It is held per thread, and a binding whose handlers
 * hop threads (coroutines) carries [holder] with them.
 */
object Caller {
    /** The current request's credential, per thread; bindings that hop threads carry it along. */
    val holder: ThreadLocal<String?> = ThreadLocal()

    /** The credential the request being served carried, or null when it carried none. */
    fun authorization(): String? = holder.get()

    /** Runs [block] as the caller whose credential is [authorization], then restores the one before. */
    fun <T> during(authorization: String?, block: () -> T): T {
        val before = holder.get()
        holder.set(authorization)
        try {
            return block()
        } finally {
            holder.set(before)
        }
    }
}
