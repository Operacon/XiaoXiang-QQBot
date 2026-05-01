package `fun`.imiku.bot.xiaoxiang.service.group

import `fun`.imiku.bot.xiaoxiang.model.XXBot
import `fun`.imiku.bot.xiaoxiang.utils.log
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class GroupScheduleService(
    private val groupStatsService: GroupStatsService,
    private val xxBot: XXBot
) {
    @Scheduled(cron = "1 0 0 * * *", zone = "Asia/Shanghai")
    fun sendStatistics() {
        val count = groupStatsService.sendStats(xxBot)
        log.info("已发送昨日群聊统计，成功 {} 个，失败 {} 个", count.first, count.second)
    }
}
