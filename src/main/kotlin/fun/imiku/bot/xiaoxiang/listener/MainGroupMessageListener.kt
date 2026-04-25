package `fun`.imiku.bot.xiaoxiang.listener

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import `fun`.imiku.bot.xiaoxiang.model.XXBot
import `fun`.imiku.bot.xiaoxiang.service.group.GroupStatsService
import `fun`.imiku.napcat4j.annotation.message.GroupMessageListener
import `fun`.imiku.napcat4j.listener.MessageListener

/**
 * 群聊主监听器
 */
@GroupMessageListener
class MainGroupMessageListener(
    // 按 @Order 注解顺序注入 service.group
    private val processors: List<GroupEventProcessor>,
    // 注入 XXBot 包装类
    private val xxBot: XXBot,
    // 单独注入统计服务
    private val groupStatsService: GroupStatsService
) : MessageListener<GroupMessageEvent> {
    override fun process(bot: Bot, event: GroupMessageEvent) {
        // 组装 context
        val context = GroupEventContext(xxBot.bind(bot), event, event.plainText)

        // 全局统计
        groupStatsService.countAll(context)

        // 每个 service 各自处理
        for (processor in processors) {
            when (processor.process(context)) {
                ProcessOption.CONTINUE -> continue
                ProcessOption.STOP -> break
            }
        }
    }
}