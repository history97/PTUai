package kr.landscapebid.engine

/**
 * 탭으로 구분된 낙찰 데이터(첫 줄 헤더)를 읽는다.
 * 열 순서: no, n(공고명), o(기관), co(업체), d(개찰일), b, a, e, p, f, x, c
 * (안드로이드 앱에서는 assets 의 JSON 등으로 바꿔 읽어도 된다. 엔진은 BidRecord 리스트만 받는다.)
 */
object BidTsv {
    fun parse(text: String): List<BidRecord> =
        text.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split('\t')
            fun d(i: Int) = c.getOrNull(i)?.trim()?.toDoubleOrNull() ?: 0.0
            BidRecord(
                no = c[0], name = c[1], org = c[2], company = c[3], date = c[4],
                base = d(5), aValue = d(6), estimate = d(7), planned = d(8), floorPrice = d(9), winBid = d(10), netCost = d(11),
            )
        }.toList()
}
