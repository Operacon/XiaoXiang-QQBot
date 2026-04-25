package `fun`.imiku.bot.xiaoxiang.model

interface GroupEventProcessor {
    fun process(context: GroupEventContext): ProcessOption
}

enum class ProcessOption {
    CONTINUE,
    STOP
}