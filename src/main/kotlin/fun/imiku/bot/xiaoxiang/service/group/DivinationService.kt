package `fun`.imiku.bot.xiaoxiang.service.group

import com.mikuac.shiro.common.utils.ArrayMsgUtils
import `fun`.imiku.bot.xiaoxiang.model.DivinationLot
import `fun`.imiku.bot.xiaoxiang.model.GroupEventContext
import `fun`.imiku.bot.xiaoxiang.model.GroupEventProcessor
import `fun`.imiku.bot.xiaoxiang.model.ProcessOption
import `fun`.imiku.bot.xiaoxiang.utils.MessageUtil
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@Order(20)
@Service
class DivinationService : GroupEventProcessor {
    // value list 不需要是线程安全的。一个用户不可能做到真正并发提交请求
    private val historyLotMap: ConcurrentHashMap<Long, MutableList<DivinationLot>> = ConcurrentHashMap()

    override fun process(context: GroupEventContext): ProcessOption {
        // 保留 CQ 码的结果，使用 message 而非 plainText
        val split = MessageUtil.splitMessage(context.event.message)

        if (split[0] == "求签") {
            if (split.size == 1) {
                val msgBuilder = ArrayMsgUtils.builder()
                    .reply(context.event.messageId)
                    .at(context.userId)
                    .text(" 所以想求什么呢")
                context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
                return ProcessOption.STOP
            }
            val obj = split.drop(1).joinToString("")
            if (obj.isNotBlank())
                draw(obj, context)
            return ProcessOption.STOP
        }
        return ProcessOption.STOP
    }

    /**
     * 求签
     */
    private fun draw(obj: String, context: GroupEventContext) {
        // 如果缓存不存在，增加缓存
        val initList = mutableListOf(DivinationLot(obj, (0..6).random() - 3))
        val existingList = historyLotMap.putIfAbsent(context.userId, initList)
        if (existingList == null) {
            val msgBuilder = ArrayMsgUtils.builder()
                .reply(context.event.messageId)
                .text(
                    """
                        所求事项：${obj.replaceFirst("我", "你")}
                        求签结果：${initList.first().sentRet(1)}
                    """.trimIndent()
                )
            context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
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
                        .reply(context.event.messageId)
                        .text(
                            """
                                所求事项：${obj.replaceFirst("我", "你")}
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
                    flag = true
                    break
                }
            }
            // 未命中，添加并发送
            if (!flag) {
                list.add(DivinationLot(obj, (0..6).random() - 3))
                val msgBuilder = ArrayMsgUtils.builder()
                    .reply(context.event.messageId)
                    .text(
                        """
                            所求事项：${obj.replaceFirst("我", "你")}
                            求签结果：${list.last().sentRet(1)}
                        """.trimIndent()
                    )
                context.xxBot.sendGroupMsgWithCount(context.groupId, msgBuilder.build())
                return
            }
        }
    }
}
