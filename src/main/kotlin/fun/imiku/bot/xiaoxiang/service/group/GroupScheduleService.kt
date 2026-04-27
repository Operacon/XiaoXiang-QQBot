package `fun`.imiku.bot.xiaoxiang.service.group

import `fun`.imiku.bot.xiaoxiang.model.XXBot
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class GroupScheduleService(
    private val groupStatsService: GroupStatsService,
    private val xxBot: XXBot
) {
    @Scheduled(cron = "1 0 0 * * *", zone = "Asia/Shanghai")
    fun sendStatistics() {
        groupStatsService.sendStats(xxBot)
    }
}
