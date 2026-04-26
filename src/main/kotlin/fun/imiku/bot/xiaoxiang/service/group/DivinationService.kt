package `fun`.imiku.bot.xiaoxiang.service.group

import com.mikuac.shiro.common.utils.ArrayMsgUtils
import com.mikuac.shiro.enums.MsgTypeEnum
import com.mikuac.shiro.model.ArrayMsg
import `fun`.imiku.bot.xiaoxiang.model.DivinationLot
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import `fun`.imiku.bot.xiaoxiang.utils.MessageUtil
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import tools.jackson.databind.node.ObjectNode
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

/**
 * TODO: 目前遗留过多旧代码，并且使用 ArrayMsg 进行硬富文本适配。如果时间充足最好重构
 */
@Order(20)
@Service
class DivinationService : GroupEventProcessor {
    // value list 不需要是线程安全的。一个用户不可能做到真正并发提交请求
    private val historyLotMap: ConcurrentHashMap<Long, MutableList<DivinationLot>> = ConcurrentHashMap()

    override fun process(context: GroupEventContext): ProcessOption {
        // 保留 CQ 码的结果，使用 message 而非 plainText
        val split = MessageUtil.splitMessage(context.event.message)
        // 对富文本的支持是有限的：即使图片等完全相同，其链接也可能不同，因此无法判断是否是同一个消息。只有转发的情况链接一定会相同
        val arrayMsg = context.event.arrayMsg

        if (split[0] == "求签") {
            if (split.size == 1) {
                val msgBuilder = ArrayMsgUtils.builder()
                    .reply(context.event.messageId)
                    .at(context.userId)
                    .text(" 所以想求什么呢")
                context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
                return ProcessOption.STOP
            }
            val obj = split.drop(1).joinToString(" ")
            if (arrayMsg.size == 1) {
                // 是纯文本消息
                if (obj.isNotBlank())
                    draw(obj, context)
            } else {
                // 是富文本消息，替换第一个中的触发指令。此时第一个必然是 text 类型
                val newText = arrayMsg[0].toCQCode().replace("求签", "").trim()
                if (newText.isBlank()) {
                    // 如果为空，删除第一个
                    arrayMsg.removeAt(0)
                } else {
                    // 不为空就要替换内容
                    (arrayMsg[0].data as ObjectNode).put("text", newText)
                }
                if (arrayMsg.isNotEmpty()) {
                    draw(obj, context, arrayMsg)
                }
            }

            return ProcessOption.STOP
        }
        return ProcessOption.STOP
    }

    /**
     * 求签
     */
    private fun draw(obj: String, context: GroupEventContext, arrayMsg: MutableList<ArrayMsg> = mutableListOf()) {
        // 如果缓存不存在，增加缓存
        val initList = mutableListOf(DivinationLot(obj, (0..6).random() - 3))
        val existingList = historyLotMap.putIfAbsent(context.userId, initList)
        if (existingList == null) {
            val msgBuilder = ArrayMsgUtils.builder().reply(context.event.messageId).text("所求事项：")
            if (arrayMsg.isEmpty()) {
                msgBuilder.text(
                    """
                        ${obj.replaceFirst("我", "你")}
                        求签结果：${initList.first().sentRet(1)}
                    """.trimIndent()
                )
                context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
            } else {
                val currArrayMsg = msgBuilder.build()
                arrayMsg.stream().filter { it.type == MsgTypeEnum.text }.forEach {
                    var newText = it.toCQCode()
                    newText = newText.replaceFirst("我", "你")
                    (arrayMsg[0].data as ObjectNode).put("text", newText)
                }
                currArrayMsg.addAll(arrayMsg)
                currArrayMsg.addAll(
                    ArrayMsgUtils.builder()
                        .text("\n求签结果：").text(initList.first().sentRet(1))
                        .build()
                )
                context.xxBot.sendGroupMsgWithCount(context.groupId, currArrayMsg)
            }
            return
        }
        // 存在则读取并检查
        val list = historyLotMap[context.userId]
        if (list != null) {
            var flag = false
            var ret: Int
            // 删除其中的过期项
            list.removeIf(object : Predicate<DivinationLot?> {
                override fun test(i: DivinationLot?): Boolean {
                    if (i != null)
                        return i.isExpiredWith(30)
                    return false
                }
            })
            // 对剩下的进行比较，看能否复用
            for (i in list) {
                ret = i.checkSim(obj)
                if (ret >= 0) {
                    val msgBuilder = ArrayMsgUtils.builder()
                        .reply(context.event.messageId).text("所求事项：")
                    if (arrayMsg.isEmpty()) {
                        msgBuilder.text(
                            """
                                ${obj.replaceFirst("我", "你")}
                                求签结果：${
                                i.sentRet(
                                    when (ret % 2) {
                                        1 -> -1
                                        else -> 1
                                    }
                                )
                            }
                            """.trimIndent()
                        )
                        context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
                    } else {
                        val currArrayMsg = msgBuilder.build()
                        arrayMsg.stream().filter { it.type == MsgTypeEnum.text }.forEach {
                            var newText = it.toCQCode()
                            newText = newText.replaceFirst("我", "你")
                            (arrayMsg[0].data as ObjectNode).put("text", newText)
                        }
                        currArrayMsg.addAll(arrayMsg)
                        currArrayMsg.addAll(
                            ArrayMsgUtils.builder()
                                .text("\n求签结果：").text(
                                    i.sentRet(
                                        when (ret % 2) {
                                            1 -> -1
                                            else -> 1
                                        }
                                    )
                                )
                                .build()
                        )
                        context.xxBot.sendGroupMsgWithCount(context.groupId, currArrayMsg)
                    }
                    flag = true
                    break
                }
            }
            // 未命中，添加并发送
            if (!flag) {
                list.add(DivinationLot(obj, (0..6).random() - 3))
                val msgBuilder = ArrayMsgUtils.builder()
                    .reply(context.event.messageId).text("所求事项：")
                if (arrayMsg.isEmpty()) {
                    msgBuilder.text(
                        """
                            ${obj.replaceFirst("我", "你")}
                            求签结果：${list.last().sentRet(1)}
                        """.trimIndent()
                    )
                    context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
                } else {
                    val currArrayMsg = msgBuilder.build()
                    arrayMsg.stream().filter { it.type == MsgTypeEnum.text }.forEach {
                        var newText = it.toCQCode()
                        newText = newText.replaceFirst("我", "你")
                        (arrayMsg[0].data as ObjectNode).put("text", newText)
                    }
                    currArrayMsg.addAll(arrayMsg)
                    currArrayMsg.addAll(
                        ArrayMsgUtils.builder()
                            .text("\n求签结果：").text(list.last().sentRet(1))
                            .build()
                    )
                    context.xxBot.sendGroupMsgWithCount(context.groupId, currArrayMsg)
                }
                return
            }
        }
    }
}
