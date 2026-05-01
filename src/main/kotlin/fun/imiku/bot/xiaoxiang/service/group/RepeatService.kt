package `fun`.imiku.bot.xiaoxiang.service.group

import `fun`.imiku.bot.xiaoxiang.config.ExternalProperties
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import `fun`.imiku.bot.xiaoxiang.utils.MessageUtil
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 复读的优先级应当最低，防止自激
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Service
class RepeatService(private val externalProperties: ExternalProperties) : GroupEventProcessor {
    private val repeatMap = ConcurrentHashMap<Long, RepeatInfo>()

    override fun process(context: GroupEventContext): ProcessOption {
        var option = ProcessOption.CONTINUE
        var needSend = false
        val newMessage = MessageUtil.removeCqUrl(context.event.message)

        repeatMap.compute(context.groupId) { _, old ->
            when {
                // 第一条消息，初始化 map
                old == null -> {
                    option = ProcessOption.CONTINUE
                    RepeatInfo(newMessage, repeated = false)
                }

                // 出现复读
                old.message == newMessage -> {
                    if (old.repeated) {
                        // 已复读过，群里还在复读
                        option = ProcessOption.STOP
                        old
                    } else {
                        // 可以复读
                        option = ProcessOption.STOP
                        needSend = true
                        old.copy(repeated = true)
                    }
                }

                // 正常添加新的消息
                else -> {
                    option = ProcessOption.CONTINUE
                    RepeatInfo(newMessage, repeated = false)
                }
            }
        }

        // 此时才真正发送复读
        if (needSend && Random.nextFloat() < externalProperties.group.repeatProbability)
            context.xxBot.sendGroupMsgWithCount(
                context.groupId,
                context.event.message,
                autoEscape = false,
                // 复读无需随机等待
                randomWait = false
            )

        return option
    }

    data class RepeatInfo(
        val message: String,
        val repeated: Boolean
    )
}
