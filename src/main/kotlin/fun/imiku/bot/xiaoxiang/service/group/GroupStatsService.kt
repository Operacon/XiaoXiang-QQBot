package `fun`.imiku.bot.xiaoxiang.service.group

import com.mikuac.shiro.common.utils.ArrayMsgUtils
import com.mikuac.shiro.enums.MsgTypeEnum
import `fun`.imiku.bot.xiaoxiang.config.XiaoXiangProperties
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import `fun`.imiku.bot.xiaoxiang.model.XXBot
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Order(10)
@Service
class GroupStatsService(
    private val properties: XiaoXiangProperties
) : GroupEventProcessor {
    /**
     * 统计开始时间
     */
    private var startedAt = LocalDateTime.now()

    /**
     * 具体的聊天历史
     */
    private var chatHistory = ConcurrentHashMap<Long, StringBuilder>()

    /**
     * 所有 GroupMessageEvent 数量
     */
    private var chatAllCounter = ConcurrentHashMap<Long, Long>()

    /**
     * “有效”纯文本消息数量
     */
    private var chatEffectiveCounter = ConcurrentHashMap<Long, Long>()

    /**
     * 所有 GroupMessageEvent 中图片数量
     */
    private var chatImageCounter = ConcurrentHashMap<Long, Long>()

    /**
     * bot 发言数量，由 XXBot 的发言方法维护
     */
    private var chatBotCounter = ConcurrentHashMap<Long, Long>()

    override fun process(context: GroupEventContext): ProcessOption {
        if (context.split[0] == "小湘") {
            val msgBuilder = ArrayMsgUtils.builder()
                .reply(context.event.messageId)
                .at(context.userId)
                .text(" ")
            val msg = listOf<() -> Unit>(
                { msgBuilder.text("潇小湘在线上~") },
                { msgBuilder.text("小湘在的！") },
                { msgBuilder.text("干嘛") },
                { msgBuilder.text("潇小湘在线上~") },
            )
            msg.random().invoke()
            context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
            return ProcessOption.STOP
        }
        if (context.split[0] in setOf("功能", "介绍", "介绍一下", "自我介绍", "help") && context.split.size == 1) {
            val msgBuilder = ArrayMsgUtils.builder()
                .reply(context.event.messageId)
                .at(context.userId)
                .text(" ")
                .text(
                    "潇小湘是开源项目，请参照 https://github.com/Operacon/XiaoXiang-QQBot ！" +
                            "bug 反馈或功能需求请联系 bot 主人或开 issue~"
                )
            context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
            sendStats(context.xxBot)
            return ProcessOption.STOP
        }
        return ProcessOption.CONTINUE
    }

    /**
     * GroupMessageEvent 统计数量自增
     */
    fun countAll(context: GroupEventContext) {
        chatAllCounter.merge(context.groupId, 1) { old, one -> old + one }
        context.event.arrayMsg.stream()
            .filter { it.type == MsgTypeEnum.image }
            .forEach { chatImageCounter.merge(context.groupId, 1) { old, one -> old + one } }
        if (!context.fContent.isNullOrBlank()) {
            val count = chatEffectiveCounter.merge(context.groupId, 1) { old, one -> old + one }
            if (count!! < properties.stats.maxHistoryCount && context.fContent.length < properties.stats.maxMessageLength) {
                val sb = chatHistory.computeIfAbsent(context.groupId) { StringBuilder() }
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

    /**
     * 发送每日统计
     */
    fun sendStats(xxBot: XXBot) {
        // 备份与清空。在这里同步地生成、发送，可能有助于缓解风控
        val oldStartedAt = startedAt
        startedAt = LocalDateTime.now()
        val oldChatHistory = chatHistory
        chatHistory = ConcurrentHashMap<Long, StringBuilder>()
        val oldAllCounter = chatAllCounter
        chatAllCounter = ConcurrentHashMap<Long, Long>()
        val oldEffectiveCounter = chatEffectiveCounter
        chatEffectiveCounter = ConcurrentHashMap<Long, Long>()
        val oldChatImageCounter = chatImageCounter
        chatImageCounter = ConcurrentHashMap<Long, Long>()
        val oldChatBotCounter = chatBotCounter
        chatBotCounter = ConcurrentHashMap<Long, Long>()

        val sb = StringBuilder()
        if (oldStartedAt.hour != 0 || oldStartedAt.minute != 0 || oldStartedAt.second >= 15) {
            // 说明服务是当前启动的
            sb.append("你群从昨天 ")
                .append(oldStartedAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .append(" 共发言 ")
        } else {
            sb.append("你群昨日共发言 ")
        }
        sb.append("#TOTAL 条，其中图片 #IMAGE 张\n废话率为 #RATIO% ~")

        val fmt = sb.toString()
        oldAllCounter.forEach { (groupId, cnt) ->
            try {
                var msg = fmt.replace("#TOTAL", cnt.toString())
                    .replace("#IMAGE", oldChatImageCounter.getOrDefault(groupId, 0).toString())
                    .replace(
                        "#RATIO", "%.2f".format(
                            (cnt - oldEffectiveCounter.getOrDefault(groupId, 0)).toDouble() / cnt
                        )
                    )
                xxBot.bot.sendGroupMsg(groupId, msg, false)
                Thread.sleep(Random.nextLong(800))
                msg = "小湘发言 " + oldChatBotCounter.getOrDefault(
                    groupId,
                    0
                ) + " 条~[CQ:face,id=" + Random.nextLong(222) + "]"
                xxBot.bot.sendGroupMsg(groupId, msg, false)
                // TODO: 词云实现
            } catch (_: Exception) {
            }
        }
    }
}