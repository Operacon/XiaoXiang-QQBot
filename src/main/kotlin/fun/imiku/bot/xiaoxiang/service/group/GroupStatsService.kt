package `fun`.imiku.bot.xiaoxiang.service.group

import com.mikuac.shiro.common.utils.ArrayMsgUtils
import com.mikuac.shiro.enums.MsgTypeEnum
import `fun`.imiku.bot.xiaoxiang.config.XiaoXiangProperties
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Order(10)
@Service
class GroupStatsService(
    private val properties: XiaoXiangProperties
) : GroupEventProcessor {
    /**
     * 具体的聊天历史
     */
    private val chatHistory = ConcurrentHashMap<Long, StringBuilder>()

    /**
     * 所有 GroupMessageEvent 数量
     */
    private val chatAllCounter = ConcurrentHashMap<Long, Long>()

    /**
     * “有效”纯文本消息数量
     */
    private val chatEffectiveCounter = ConcurrentHashMap<Long, Long>()

    /**
     * 所有 GroupMessageEvent 中图片数量
     */
    private val chatImageCounter = ConcurrentHashMap<Long, Long>()

    /**
     * bot 发言数量，由 XXBot 的发言方法维护
     */
    private val chatBotCounter = ConcurrentHashMap<Long, Long>()

    override fun process(context: GroupEventContext): ProcessOption {
        if (context.split[0] == "小湘") {
            val msgBuilder = ArrayMsgUtils.builder()
                .reply(context.event.messageId)
                .at(context.event.userId)
                .text(" ")
            val msg = listOf<() -> Unit>(
                { msgBuilder.text("潇小湘在线上~") },
                { msgBuilder.text("小湘在的！") },
                { msgBuilder.text("干嘛") },
                { msgBuilder.text("潇小湘在线上~") },
            )
            msg.random().invoke()
            context.xxBot.sendGroupMsgWithCount(context.event.groupId, msgBuilder.build())
            return ProcessOption.STOP
        }
        if (context.split[0] in setOf("功能", "介绍", "介绍一下", "自我介绍", "help") && context.split.size == 1) {
            val msgBuilder = ArrayMsgUtils.builder()
                .reply(context.event.messageId)
                .at(context.event.userId)
                .text(" ")
                .text(
                    "潇小湘是开源项目，请参照 https://github.com/Operacon/XiaoXiang-QQBot ！" +
                            "bug 反馈或功能需求请联系 bot 主人或开 issue~"
                )
            context.xxBot.sendGroupMsgWithCount(context.event.groupId, msgBuilder.build())
            return ProcessOption.STOP
        }
        return ProcessOption.CONTINUE
    }

    /**
     * GroupMessageEvent 统计数量自增
     */
    fun countAll(context: GroupEventContext) {
        chatAllCounter.merge(context.event.groupId, 1) { old, one -> old + one }
        context.event.arrayMsg.stream()
            .filter { it.type == MsgTypeEnum.image }
            .forEach { chatImageCounter.merge(context.event.groupId, 1) { old, one -> old + one } }
        if (context.fContent.isNullOrBlank()) {
            val count = chatEffectiveCounter.merge(context.event.groupId, 1) { old, one -> old + one }
            if (count!! < properties.stats.maxHistoryCount && context.fContent.length < properties.stats.maxMessageLength) {
                val sb = chatHistory.computeIfAbsent(context.event.groupId) { StringBuilder() }
                synchronized(sb) {
                    sb.append(context.fContent).append(' ')
                }
            }
        }
    }

    /**
     * 群聊 bot 发言数量自增
     */
    fun countBot(groupId: Long) {
        chatBotCounter.merge(groupId, 1) { old, one -> old + one }
    }
}