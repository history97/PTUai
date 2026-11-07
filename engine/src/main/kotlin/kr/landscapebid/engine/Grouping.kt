package kr.landscapebid.engine

/** 기관 그룹 / 규모 구간 분류. 원본: groupOf / sizeOf */
object Grouping {
    val GU = listOf(
        "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구", "노원구", "은평구", "서대문구",
        "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구",
    )
    val SIZES = listOf("1억 미만", "1~3억", "3~10억", "10~50억", "50억 이상")
    val OTHER_GROUPS = listOf("서울시 본청·사업소", "공사·공단", "교육청·학교", "국가·기타")

    // JS 의 \s 가 가리키는 공백 문자를 직접 나열한다. (?U) 같은 JVM 전용 플래그는 안드로이드에서 크래시가 나서 쓰지 않는다.
    private const val WS = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    // \z: JS 의 $ (문자열 맨 끝)
    private val guRe = Regex("^서울특별시[$WS]+([^$WS]+구)\\z")
    private val eduRe = Regex("교육|학교|대학|초등|중학|고등")
    private val pubRe = Regex("공사|공단|주택도시")
    private val seoulRe = Regex("^서울특별시|^서울시")

    fun groupOf(org: String?): String {
        val o = org ?: ""
        guRe.find(o)?.let { if (GU.contains(it.groupValues[1])) return it.groupValues[1] }
        if (eduRe.containsMatchIn(o)) return "교육청·학교"
        if (pubRe.containsMatchIn(o)) return "공사·공단"
        if (seoulRe.containsMatchIn(o)) return "서울시 본청·사업소"
        return "국가·기타"
    }

    fun sizeOf(e: Double): String = when {
        e < 1e8 -> "1억 미만"
        e < 3e8 -> "1~3억"
        e < 1e9 -> "3~10억"
        e < 5e9 -> "10~50억"
        else -> "50억 이상"
    }
}
