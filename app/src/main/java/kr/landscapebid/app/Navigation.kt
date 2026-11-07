package kr.landscapebid.app

/** 화면 묶음. 위쪽 탭 4개 + 톱니바퀴(설정) */
enum class Tab(val label: String) {
    RECENT("최근 공고"),
    ANALYSIS("하한가 분석"),
    MY("my페이지"),
    HISTORY("검색 이력"),
    SETTINGS("설정"),
}

/** 지금 보고 있는 화면. 하한가 분석만 step(1 입력 / 2 금액 분포)이 있다. */
data class Route(val tab: Tab, val step: Int = 1)
