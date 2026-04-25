package `fun`.imiku.bot.xiaoxiang.model

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.action.common.ActionData
import com.mikuac.shiro.dto.action.common.MsgId
import com.mikuac.shiro.dto.event.message.MessageEvent
import com.mikuac.shiro.enums.MsgTypeEnum
import com.mikuac.shiro.model.ArrayMsg
import `fun`.imiku.bot.xiaoxiang.service.group.GroupStatsService
import org.springframework.stereotype.Component

/**
 * 单例的 Bot 伴生类，包装了一些 Bot 的功能
 * <p>
 * 使用 xxBot.bot 操作原始 Bot 对象
 */
@Component
class XXBot(
    private val groupStatsService: GroupStatsService
) {
    @Volatile
    lateinit var bot: Bot

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
        val result = bot.sendGroupMsg(groupId, msg, autoEscape)
        groupStatsService.countBot(groupId)
        return result
    }

    fun sendGroupMsgWithCount(groupId: Long, msg: List<ArrayMsg>, autoEscape: Boolean = false): ActionData<MsgId> {
        val result = bot.sendGroupMsg(groupId, msg, autoEscape)
        groupStatsService.countBot(groupId)
        return result
    }

    fun isAtMe(messageEvent: MessageEvent): Boolean =
        messageEvent.arrayMsg.any {
            it.type == MsgTypeEnum.at && it.getLongData("qq") == bot.selfId
        }

}