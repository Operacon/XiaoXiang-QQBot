package `fun`.imiku.bot.xiaoxiang.model

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.action.common.ActionData
import com.mikuac.shiro.dto.action.common.MsgId
import com.mikuac.shiro.dto.event.message.MessageEvent
import com.mikuac.shiro.enums.MsgTypeEnum
import com.mikuac.shiro.model.ArrayMsg
import `fun`.imiku.bot.xiaoxiang.config.XiaoXiangProperties
import `fun`.imiku.bot.xiaoxiang.service.group.GroupStatsService
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 单例的 Bot 伴生类，包装了一些 Bot 的功能
 * <p>
 * 使用 xxBot.bot 操作原始 Bot 对象
 */
@Component
class XXBot(
    private val groupStatsService: GroupStatsService,
    private val properties: XiaoXiangProperties
) {
    @Volatile
    lateinit var bot: Bot

    /**
     * 使用此方法获取 XXBot 实例。除非明白在做什么，禁止从 Spring 容器直接获取或手动创建，否则易有异常
     */
    fun bind(bot: Bot): XXBot {
        if (!::bot.isInitialized || this.bot !== bot) {
            synchronized(this) {
                if (!::bot.isInitialized || this.bot !== bot) {
                    this.bot = bot
                }
            }
        }
        return this
    }

    fun sendGroupMsgWithCount(groupId: Long, msg: String, autoEscape: Boolean = false): ActionData<MsgId> {
        doRandomAwait()
        val result = bot.sendGroupMsg(groupId, msg, autoEscape)
        groupStatsService.countBot(groupId)
        return result
    }

    fun sendGroupMsgWithCount(groupId: Long, msg: List<ArrayMsg>, autoEscape: Boolean = false): ActionData<MsgId> {
        doRandomAwait()
        val result = bot.sendGroupMsg(groupId, msg, autoEscape)
        groupStatsService.countBot(groupId)
        return result
    }

    fun isAtMe(messageEvent: MessageEvent): Boolean =
        messageEvent.arrayMsg.any {
            it.type == MsgTypeEnum.at && it.getLongData("qq") == bot.selfId
        }

    private fun doRandomAwait() {
        if (properties.common.sendRandomAwaitMax == 0L) {
            return
        }
        Thread.sleep(
            Random.nextLong(
                properties.common.sendRandomAwaitMin,
                properties.common.sendRandomAwaitMax + 1
            )
        )
    }

    @PostConstruct
    private fun checkRandomAwaitTime() {
        if (properties.common.sendRandomAwaitMin < 0) properties.common.sendRandomAwaitMin = 0
        if (properties.common.sendRandomAwaitMax < 0) properties.common.sendRandomAwaitMax = 0
        if (properties.common.sendRandomAwaitMax < properties.common.sendRandomAwaitMin)
            properties.common.sendRandomAwaitMax = properties.common.sendRandomAwaitMin
    }

}