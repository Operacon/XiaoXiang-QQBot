package `fun`.imiku.bot.xiaoxiang.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "xiaoxiang")
class XiaoXiangProperties {
    var config: Config = Config()

    class Config {
        /**
         * 文件配置所在位置。需要保证此文件有权限读写
         */
        var configFile: String = "~/.xxBotProperties.json"
    }
}
