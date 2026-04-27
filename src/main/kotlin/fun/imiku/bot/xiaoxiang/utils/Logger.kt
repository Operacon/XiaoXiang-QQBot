package `fun`.imiku.bot.xiaoxiang.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


val Any.log: Logger
    get() = LoggerCache.get(this::class.java)

object LoggerCache {
    private val cache = ConcurrentHashMap<Class<*>, Logger>()

    fun get(clazz: Class<*>): Logger =
        cache.computeIfAbsent(clazz) { LoggerFactory.getLogger(it) }
}