package kr.landscapebid.engine

/**
 * 낙찰 데이터 한 건. 원본 BASE_RECS 의 필드와 1:1 대응한다.
 * 금액은 JS 와 똑같이 Double(원 단위 정수는 2^53 미만이라 정확하게 표현됨)로 둔다.
 */
data class BidRecord(
    val no: String,          // no  공고번호
    val name: String,        // n   공고명
    val org: String,         // o   수요기관
    val company: String,     // co  낙찰업체
    val date: String,        // d   개찰일 (YYYY-MM-DD)
    val base: Double,        // b   기초금액
    val aValue: Double,      // a   A값
    val estimate: Double,    // e   추정가격 (0 이면 base/1.1 로 대체)
    val planned: Double,     // p   예정가격
    val floorPrice: Double,  // f   낙찰하한가 (0 이면 결측)
    val winBid: Double,      // x   1순위 투찰금액
    val netCost: Double,     // c   순공사원가
)

/** BidRecord 에 계산값을 붙인 것 (원본 enrich() 의 반환값). */
data class Enriched(
    val rec: BidRecord,
    val e: Double,      // 추정가격
    val rate: Double,   // 적용 하한율(%) — 낙찰하한가에서 역산한 값 또는 규모별 기본값
    val s: Double,      // 사정율(%) = (예정가격/기초금액 - 1) * 100
    val sw: Double,     // 1순위 투찰금액이 하한가가 되는 사정율(%)
    val rA: Double,     // 1순위 투찰율(%) = (x-a)/(p-a)*100
    val gap: Double,    // rA - rate
    val diff: Double,   // 1순위 투찰금액 - 낙찰하한가
    val grp: String,    // 기관 그룹
    val size: String,   // 규모 구간
    val mon: String,    // YYYY-MM
) {
    val base get() = rec.base
    val aValue get() = rec.aValue
    val floorPrice get() = rec.floorPrice
    val winBid get() = rec.winBid
}
