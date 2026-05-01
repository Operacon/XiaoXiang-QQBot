package `fun`.imiku.bot.xiaoxiang.listener

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.notice.GroupPokeNoticeEvent
import `fun`.imiku.bot.xiaoxiang.config.ExternalProperties
import `fun`.imiku.bot.xiaoxiang.model.XXBot
import `fun`.imiku.napcat4j.annotation.notice.GroupPokeNoticeListener
import `fun`.imiku.napcat4j.listener.NoticeListener
import kotlin.random.Random

@GroupPokeNoticeListener
class GroupPokeListener(private val xxBot: XXBot, private val externalProperties: ExternalProperties) :
    NoticeListener<GroupPokeNoticeEvent> {
    override fun process(bot: Bot, event: GroupPokeNoticeEvent) {
        val xxBot = xxBot.bind(bot)
        if (event.targetId == bot.selfId) {
            if (Random.nextFloat() < externalProperties.group.pokeBackProbability) {
                // 回复戳一戳
                if (Random.nextFloat() <= 0.5) {
                    // 有一半的可能只是戳回去
                    bot.sendGroupMsg(event.groupId, MsgUtils.builder().poke(event.senderId).build(), false)
                } else {
                    val msg = listOf("拍人不拍脸", "不准拍 (｡･∀･)ﾉﾞ", "ヾ(・ω・*) 扇你", "(つ≧▽≦)つ 锤锤")
                    xxBot.sendGroupMsgWithCount(event.groupId, msg.random())
                }
            }
        } else if (Random.nextFloat() < externalProperties.group.pokeWithProbability) {
            // 跟随戳一戳，此时戳的对象不是自己
            bot.sendGroupMsg(event.groupId, MsgUtils.builder().poke(event.targetId).build(), false)
        }
    }
}