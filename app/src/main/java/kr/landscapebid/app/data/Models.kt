package kr.landscapebid.app.data

import kr.landscapebid.engine.Format

/** 계산기 입력칸에 채울 수 있는 공고 한 건의 값 (즐겨찾기·검색 이력이 이걸 들고 있다) */
data class FormSnapshot(
    val name: String,
    val no: String,          // 공고번호 (직접 입력한 계산이면 빈 문자열)
    val org: String,         // 발주기관 (직접 입력이면 빈 문자열)
    val base: Double,
    val aValue: Double?,
    val estimate: Double?,
    val netCost: Double?,
    val group: String,
    val rate: String,        // 낙찰하한율 ("" = 자동)
    val range: String,       // 예가 범위 ("" = 자동)
) {
    /** 같은 공고인지 가리는 키: 공고번호가 있으면 그걸로, 없으면 이름+기초금액 */
    val key: String get() = if (no.isNotEmpty()) "no:$no" else "nm:$name|${base.toLong()}"

    /** "강남구 · 기초 191,980,000" */
    fun meta(): String = (if (org.isNotEmpty()) org.replaceFirst("서울특별시 ", "") else group) + " · 기초 " + Format.won(base)
}

/** 즐겨찾기 공고. 내가 정한 금액(sExp, bid)은 아직 안 정했으면 null. */
data class SavedItem(val id: String, val form: FormSnapshot, val sExp: Double?, val bid: Double?, val savedAt: String)

/** 검색 이력 한 줄 */
data class HistoryItem(val id: String, val form: FormSnapshot, val at: String)
