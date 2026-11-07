package kr.landscapebid.app.data

/**
 * 여러 곳(게스트로 쓰던 기기 데이터, 이 계정의 기기 데이터, 클라우드)의 즐겨찾기·검색 이력을 하나로 합친다.
 * 같은 공고(form.key)는 더 최근 것을 남긴다.
 * 한계: 삭제 기록은 따로 없어서, 한 기기에서 지운 항목이 오프라인으로 남아 있던 다른 기기와 합쳐지면 되살아날 수 있다.
 */
object Merge {
    /** 같은 공고는 저장 시각이 더 늦은 것, 같으면 금액을 정해 둔 것을 남긴다. 최근 저장 순. */
    fun favorites(sources: List<List<SavedItem>>): List<SavedItem> =
        sources.flatten().groupBy { it.form.key }.values
            .map { group -> group.maxWith(compareBy<SavedItem>({ it.savedAt }, { if (it.bid != null) 1 else 0 })) }
            .sortedByDescending { it.savedAt }

    /** 같은 공고는 더 최근에 연 기록만. 최근 순으로 최대 limit 건. */
    fun history(sources: List<List<HistoryItem>>, limit: Int = 100): List<HistoryItem> =
        sources.flatten().groupBy { it.form.key }.values
            .map { group -> group.maxBy { it.at } }
            .sortedByDescending { it.at }
            .take(limit)
}
