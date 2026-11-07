package kr.landscapebid.engine

import java.time.LocalDate
import kotlin.math.ceil

/** 입찰 공고 목록 한 줄. 값이 없는 숫자는 NaN. 원본 parseNotices 의 결과와 같다. */
data class Notice(
    val name: String,
    val no: String,
    val org: String,
    val base: Double,           // 기초금액
    val deadline: String?,      // 투찰 마감 "YYYY-MM-DDTHH:MM" (한국 시간 그대로)
    val openDate: String,       // 개찰일(원문)
    val estimate: Double,       // 추정가격
    val aValue: Double,         // A값 (용역·수의 등은 없음)
    val netCost: Double,        // 순공사원가
    val rate: Double,           // 낙찰하한율(%)
    val variation: Double,      // 예가변동폭: 2 또는 3
) {
    /** 공고명 뒤의 [A값][순] 같은 꼬리표를 뗀 이름 */
    val title: String get() = NoticeImport.cleanName(name)
}

class ImportResult(val records: List<BidRecord>, val skipped: Int, val error: String?)

/** 엑셀에서 읽은 표(행 목록, 칸은 String 또는 Double)를 낙찰 데이터 / 공고 목록으로 바꾼다. 원본 parseRows / parseNotices. */
object NoticeImport {
    // ---- 칸 값 다루기 (JS 의 String(), 참/거짓, num() 동작) ----
    private fun cell(r: List<Any?>, i: Int): Any? = if (i >= 0) r.getOrNull(i) else null
    private fun s(c: Any?): String = when (c) { null -> ""; is Double -> Format.jsNumber(c); else -> c.toString() }
    private fun truthy(c: Any?): Boolean = when (c) { null -> false; is String -> c.isNotEmpty(); is Double -> c != 0.0 && !c.isNaN(); else -> true }
    private fun orEmpty(c: Any?): String = if (truthy(c)) s(c) else ""
    private fun n(c: Any?): Double = JsMath.num(s(c))
    private fun jsTrim(x: String): String = x.trim { it.isWhitespace() || it == '\uFEFF' }

    private val dateRe = Regex("(\\d{2,4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})")
    private val deadlineRe = Regex("(\\d{2,4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})\\D*(\\d{1,2}):(\\d{2})?")
    private val variationRe = Regex("(\\d+(?:\\.\\d+)?)\\s*\\z")
    private val tagRe = Regex("\\s*\\[.*\\z")

    fun cleanName(name: String): String = name.replace(tagRe, "")

    /** 날짜 칸 → "YYYY-MM-DD". 엑셀 날짜 숫자(일련번호)도 처리. */
    fun toDate(v: Any?): String? {
        if (v is Double) {
            val ms = JsMath.round((v - 25569) * 864e5)
            return LocalDate.ofEpochDay(Math.floorDiv(ms.toLong(), 86400000L)).toString()
        }
        val m = dateRe.find(orEmpty(v)) ?: return null
        val y = if (m.groupValues[1].length == 2) "20" + m.groupValues[1] else m.groupValues[1]
        return "$y-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')}"
    }

    /** 마감 칸 → "YYYY-MM-DDTHH:MM" */
    fun parseDeadline(v: Any?): String? {
        val m = deadlineRe.find(orEmpty(v)) ?: return null
        val y = if (m.groupValues[1].length == 2) "20" + m.groupValues[1] else m.groupValues[1]
        val min = m.groupValues[5].ifEmpty { "00" }
        return "$y-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')}T${m.groupValues[4].padStart(2, '0')}:$min"
    }

    /** 인포21C 낙찰정보리스트(예정가격·A값 포함) → 낙찰 데이터 */
    fun parseResults(rows: List<List<Any?>>): ImportResult {
        val hi = rows.indexOfFirst { r -> r.any { jsTrim(s(it)) == "공고명" } && r.any { s(it).contains("예정가격") } }
        if (hi < 0) return ImportResult(emptyList(), 0, "\"공고명\"과 \"예정가격\" 열이 있는 표를 찾지 못했습니다. 인포21C 낙찰정보리스트 엑셀(A값·예정가격 포함)을 올려주세요.")
        val h = rows[hi].map { jsTrim(s(it)) }
        fun col(name: String) = h.indexOf(name)
        val cn = col("공고명"); val cno = col("공고번호"); val co = col("발주기관"); val cb = col("기초금액"); val cx = col("1순위투찰금액")
        val cd = col("개찰일"); val ca = col("A값"); val ce = col("추정가격"); val cc = col("순공사원가"); val cf = col("낙찰하한가")
        val cp = col("예정가격"); val cco = col("1순위업체")
        val missing = listOf("b" to cb, "x" to cx, "a" to ca, "p" to cp, "d" to cd).filter { it.second < 0 }
            .map { mapOf("b" to "기초금액", "x" to "1순위투찰금액", "a" to "A값", "p" to "예정가격", "d" to "개찰일")[it.first]!! }
        if (missing.isNotEmpty()) return ImportResult(emptyList(), 0, "필요한 열이 없습니다: " + missing.joinToString(", "))
        val recs = ArrayList<BidRecord>()
        var skip = 0
        for (r in rows.drop(hi + 1)) {
            if (!truthy(cell(r, cn))) continue
            val b = n(cell(r, cb)); val x = n(cell(r, cx)); val a = n(cell(r, ca)); val p = JsMath.round(n(cell(r, cp)))
            val d = toDate(cell(r, cd))
            var e = if (ce >= 0) n(cell(r, ce)) else Double.NaN
            val c = if (cc >= 0) n(cell(r, cc)) else Double.NaN
            var f = if (cf >= 0) n(cell(r, cf)) else Double.NaN
            if (!(b > 0 && x > 0 && p > 0 && a >= 0 && d != null)) { skip++; continue }
            if (!(e > 0)) e = JsMath.round(b / 1.1)
            if (!(f > 0)) { val rt = Rates.rateByEst(e) ?: 0.0; f = ceil((p - a) * rt / 100 + a - 1e-7) }
            recs.add(
                BidRecord(
                    no = if (cno >= 0) orEmpty(cell(r, cno)) else "", name = s(cell(r, cn)), org = if (co >= 0) orEmpty(cell(r, co)) else "",
                    company = if (cco >= 0) orEmpty(cell(r, cco)) else "", date = d, base = b, aValue = a, estimate = e, planned = p,
                    floorPrice = f, winBid = x, netCost = if (c > 0) c else 0.0,
                ),
            )
        }
        return ImportResult(recs, skip, null)
    }

    /** 입찰 공고 목록 엑셀(공고명·기초금액 열 필요) → 공고 목록. 해당 표가 없으면 null. */
    fun parseNotices(rows: List<List<Any?>>): List<Notice>? {
        val hi = rows.indexOfFirst { r -> r.any { jsTrim(s(it)) == "공고명" } && r.any { jsTrim(s(it)) == "기초금액" } }
        if (hi < 0) return null
        val h = rows[hi].map { jsTrim(s(it)) }
        fun col(name: String) = h.indexOf(name)
        val cn = col("공고명"); val cno = col("공고번호"); val co = col("발주기관"); val cb = col("기초금액"); val cdl = col("투찰마감")
        val cop = col("개찰일"); val ce = col("추정가격"); val ca = col("A값"); val cc = col("순공사원가"); val cr = col("낙찰하한율"); val cv = col("예가변동폭")
        val out = ArrayList<Notice>()
        for (r in rows.drop(hi + 1)) {
            if (!truthy(cell(r, cn))) continue
            val b = n(cell(r, cb))
            if (!(b > 0)) continue
            val vr = if (cv >= 0) orEmpty(cell(r, cv)) else ""
            val vm = variationRe.find(vr)
            out.add(
                Notice(
                    name = s(cell(r, cn)), no = if (cno >= 0) orEmpty(cell(r, cno)) else "", org = if (co >= 0) orEmpty(cell(r, co)) else "", base = b,
                    deadline = if (cdl >= 0) parseDeadline(cell(r, cdl)) else null, openDate = if (cop >= 0) orEmpty(cell(r, cop)) else "",
                    estimate = if (ce >= 0) n(cell(r, ce)) else Double.NaN, aValue = if (ca >= 0) n(cell(r, ca)) else Double.NaN,
                    netCost = if (cc >= 0) n(cell(r, cc)) else Double.NaN, rate = if (cr >= 0) n(cell(r, cr)) else Double.NaN,
                    variation = vm?.groupValues?.get(1)?.toDouble() ?: Double.NaN,
                ),
            )
        }
        return out
    }
}
