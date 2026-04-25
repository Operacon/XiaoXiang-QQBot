package `fun`.imiku.bot.xiaoxiang.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "xiaoxiang")
class XiaoXiangProperties {
    var stats: Stats = Stats()

    class Stats {
        /**
         * 词云使用，每日群聊历史最大条数（受过长消息影响，实际最大条数会略小于配置值）
         */
        var maxHistoryCount: Long = 20000

        /**
         * 词云使用，超过此长度的消息不会被放进历史
         */
        var maxMessageLength: Long = 140
    }
}
