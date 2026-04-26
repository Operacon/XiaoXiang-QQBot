package `fun`.imiku.bot.xiaoxiang.utils

class MessageUtil {
    companion object {
        @JvmStatic
        private val splitPatten = Regex("""\s+""")

        @JvmStatic
        private val zeroWidthPatten = Regex("""[\u200B-\u200D\uFEFF]""")

        @JvmStatic
        private val controlCharPatten = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]""")

        @JvmStatic
        private val urlPatten = Regex(
            pattern = """(?i)\b((https?://|www\.)[^\s<>"']+|[a-z0-9][a-z0-9-]*(\.[a-z0-9][a-z0-9-]*)+\.(com|cn|net|org|io|ai|top|xyz|me|tv|cc|edu|gov|dev)(/[^\s<>"']*)?)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )


        /**
         * 过滤群聊消息中的“无效”成分
         */
        @JvmStatic
        fun getEffectiveMessage(rawMessage: String?): String? {
            if (rawMessage.isNullOrBlank()) {
                return null
            }
            var message = rawMessage
                .replace(zeroWidthPatten, "")
                .replace(controlCharPatten, "")
                .trim()
            if (message.isBlank()) {
                return null
            }
            // 去掉 URL。如果去掉 URL 后为空，说明是纯 URL 消息
            message = message.replace(urlPatten, " ")
            // 归一化空白
            message = message
                .replace(splitPatten, " ")
                .trim()
            if (message.isBlank()) {
                return null
            }
            return message
        }

        /**
         * plainText 按空格切分
         */
        @JvmStatic
        fun splitContent(content: String): List<String> = content.split(splitPatten)

        /**
         * message 按空格和 CQ 码切分
         */
        @JvmStatic
        fun splitMessage(content: String): List<String> {
            val parts = mutableListOf<String>()
            val plain = StringBuilder()
            var index = 0

            while (index < content.length) {
                if (content[index].isWhitespace()) {
                    if (plain.isNotEmpty()) {
                        parts.add(plain.toString())
                        plain.setLength(0)
                    }
                    while (index < content.length && content[index].isWhitespace()) {
                        index++
                    }
                    continue
                }

                if (content.startsWith("[CQ:", index)) {
                    if (plain.isNotEmpty()) {
                        parts.add(plain.toString())
                        plain.setLength(0)
                    }
                    val end = content.indexOf(']', startIndex = index)
                    if (end >= 0) {
                        parts.add(content.substring(index, end + 1))
                        index = end + 1
                    } else {
                        parts.add(content.substring(index))
                        break
                    }
                    continue
                }

                plain.append(content[index])
                index++
            }

            if (plain.isNotEmpty()) {
                parts.add(plain.toString())
            }
            return parts
        }
    }
}
