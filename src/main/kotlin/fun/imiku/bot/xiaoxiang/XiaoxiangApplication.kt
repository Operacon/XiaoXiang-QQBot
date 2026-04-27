package `fun`.imiku.bot.xiaoxiang

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class XiaoxiangApplication

fun main(args: Array<String>) {
    runApplication<XiaoxiangApplication>(*args)
}
