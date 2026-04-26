package `fun`.imiku.bot.xiaoxiang.model

import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class DivinationLot(private val plainText: String, private val fortuneLevel: Int) {
    private val englishNegationRegex = Regex("\\bnot\\b", RegexOption.IGNORE_CASE)
    private val cqSegmentRegex = Regex("\\[CQ:[^\\]]*]")
    private val baseProbabilityBasisPoints: Int = buildBaseProbabilityBasisPoints(fortuneLevel)
    private val createdAt: LocalDateTime = LocalDateTime.now()
    private val semanticFlipTransforms: List<(String) -> String> = listOf(
        ::swapBigSmall,
        ::swapInsideOutside,
        ::swapGoodBad,
        ::swapColdHot
    )

    fun sentRet(direction: Int): String = fortuneText(direction * fortuneLevel)

    fun sentProbRet(direction: Int): String {
        val describeAsOccurrence = Random.nextBoolean()
        val statementPolarity = if (describeAsOccurrence) 1 else -1

        // 方向不一致时直接取补概率，避免整数和小数分开取反时的错位问题。
        val probabilityBasisPoints = if (direction == statementPolarity) {
            baseProbabilityBasisPoints
        } else {
            10_000 - baseProbabilityBasisPoints
        }

        val suffix = if (describeAsOccurrence) "的概率发生" else "的概率不发生"
        return "${formatProbability(probabilityBasisPoints)}% $suffix"
    }

    fun checkSim(candidateText: String): Int {
        if (candidateText == plainText) return 0

        val normalizedOriginal = normalizeSemanticText(removeNegation(plainText))
        val normalizedCandidate = normalizeSemanticText(removeNegation(candidateText))

        // 文本主体的反转距离：允许多维反转组合，取可匹配时的最小反转次数。
        // 注意：反转替换会跳过所有 [CQ: ... ] 段，不触及消息码内容。
        val semanticFlipCost = minSemanticFlipCost(normalizedCandidate, normalizedOriginal) ?: return -1

        // “不/not”的数量差作为否定成本，最终距离 = 语义反转成本 + 否定成本。
        // 统计时同样忽略 [CQ: ... ] 段中的内容。
        val negationCountDiff = abs(countNegation(candidateText) - countNegation(plainText))
        return semanticFlipCost + negationCountDiff
    }

    fun isExpiredWith(minutes: Long): Boolean {
        return LocalDateTime.now().isAfter(createdAt.plusMinutes(minutes))
    }

    private fun buildBaseProbabilityBasisPoints(level: Int): Int {
        // 概率统一用基点计算（1% = 100 基点），最后再格式化成 xx.xx%。
        val integerPart = ((level + 3.0) * 14.285714).toInt() + Random.nextInt(0, 15)
        val decimalPart = Random.nextInt(0, 100)
        return integerPart * 100 + decimalPart
    }

    private fun formatProbability(basisPoints: Int): String {
        val bounded = basisPoints.coerceIn(0, 10_000)
        val integerPart = bounded / 100
        val decimalPart = bounded % 100
        return String.format(Locale.US, "%d.%02d", integerPart, decimalPart)
    }

    private fun fortuneText(score: Int): String {
        return when (score) {
            -3 -> "大凶"
            -2 -> "凶"
            -1 -> "小凶"
            0 -> "平"
            1 -> "小吉"
            2 -> "吉"
            3 -> "大吉"
            else -> "算不出来？？"
        }
    }

    private fun removeNegation(text: String): String {
        // “not”与“不”等价，都作为否定词去除；仅处理非 CQ 段。
        return transformOutsideCqSegments(text) { plainSegment ->
            englishNegationRegex.replace(plainSegment.replace("不", ""), "")
        }.trim()
    }

    private fun countNegation(text: String): Int {
        val plainPart = collectOutsideCqText(text)
        val chineseCount = plainPart.count { it == '不' }
        val englishCount = englishNegationRegex.findAll(plainPart).count()
        return chineseCount + englishCount
    }

    private fun normalizeSemanticText(text: String): String {
        // “里/内”归一化，只在非 CQ 段处理。
        return transformOutsideCqSegments(text) { plainSegment ->
            plainSegment.replace("里", "内")
        }
    }

    private fun minSemanticFlipCost(candidate: String, target: String): Int? {
        if (candidate == target) return 0

        val transformCount = semanticFlipTransforms.size
        var minCost: Int? = null

        for (mask in 1 until (1 shl transformCount)) {
            var transformed = candidate
            var cost = 0
            for (index in 0 until transformCount) {
                if ((mask and (1 shl index)) != 0) {
                    transformed = semanticFlipTransforms[index](transformed)
                    cost++
                }
            }
            if (transformed == target && (minCost == null || cost < minCost)) {
                minCost = cost
            }
        }
        return minCost
    }

    private fun swapBigSmall(text: String): String = swapCharactersOutsideCq(text, '大', '小')

    private fun swapInsideOutside(text: String): String = swapCharactersOutsideCq(text, '内', '外')

    private fun swapGoodBad(text: String): String = swapCharactersOutsideCq(text, '好', '坏')

    private fun swapColdHot(text: String): String = swapCharactersOutsideCq(text, '冷', '热')

    private fun swapCharactersOutsideCq(text: String, left: Char, right: Char): String {
        return transformOutsideCqSegments(text) { plainSegment ->
            buildString(plainSegment.length) {
                for (ch in plainSegment) {
                    append(
                        when (ch) {
                            left -> right
                            right -> left
                            else -> ch
                        }
                    )
                }
            }
        }
    }

    private fun transformOutsideCqSegments(text: String, transform: (String) -> String): String {
        val result = StringBuilder(text.length)
        var currentIndex = 0

        for (match in cqSegmentRegex.findAll(text)) {
            val start = match.range.first
            val endExclusive = match.range.last + 1

            if (currentIndex < start) {
                result.append(transform(text.substring(currentIndex, start)))
            }
            result.append(match.value)
            currentIndex = endExclusive
        }

        if (currentIndex < text.length) {
            result.append(transform(text.substring(currentIndex)))
        }

        return result.toString()
    }

    private fun collectOutsideCqText(text: String): String {
        val result = StringBuilder(text.length)
        var currentIndex = 0

        for (match in cqSegmentRegex.findAll(text)) {
            val start = match.range.first
            if (currentIndex < start) {
                result.append(text.substring(currentIndex, start))
            }
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            result.append(text.substring(currentIndex))
        }

        return result.toString()
    }
}
