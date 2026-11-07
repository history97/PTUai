package kr.landscapebid.app.data

import kr.landscapebid.engine.BidRecord
import kr.landscapebid.engine.Notice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 사용자가 추가한 낙찰 데이터와 공고 목록을 앱 안에 저장한다 (웹의 localStorage lb_extra / lb_notices). */
class Store(private val dir: File) {
    private fun file(name: String) = File(dir, name)

    fun saveExtra(list: List<BidRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("no", r.no).put("n", r.name).put("o", r.org).put("co", r.company).put("d", r.date)
                .put("b", r.base).put("a", r.aValue).put("e", r.estimate).put("p", r.planned).put("f", r.floorPrice).put("x", r.winBid).put("c", r.netCost))
        }
        file("extra.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadExtra(): List<BidRecord> = runCatching {
        val f = file("extra.json")
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BidRecord(
                no = o.optString("no"), name = o.optString("n"), org = o.optString("o"), company = o.optString("co"), date = o.optString("d"),
                base = o.optDouble("b", 0.0), aValue = o.optDouble("a", 0.0), estimate = o.optDouble("e", 0.0), planned = o.optDouble("p", 0.0),
                floorPrice = o.optDouble("f", 0.0), winBid = o.optDouble("x", 0.0), netCost = o.optDouble("c", 0.0),
            )
        }
    }.getOrDefault(emptyList())

    fun saveNotices(list: List<Notice>) {
        val arr = JSONArray()
        list.forEach { x ->
            val o = JSONObject().put("n", x.name).put("no", x.no).put("o", x.org).put("b", x.base).put("op", x.openDate)
            x.deadline?.let { o.put("dl", it) }
            // NaN 은 JSON 에 담을 수 없어서 키를 빼 둔다
            if (!x.estimate.isNaN()) o.put("e", x.estimate)
            if (!x.aValue.isNaN()) o.put("a", x.aValue)
            if (!x.netCost.isNaN()) o.put("c", x.netCost)
            if (!x.rate.isNaN()) o.put("r", x.rate)
            if (!x.variation.isNaN()) o.put("v", x.variation)
            arr.put(o)
        }
        file("notices.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadNotices(): List<Notice> = runCatching {
        val f = file("notices.json")
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Notice(
                name = o.optString("n"), no = o.optString("no"), org = o.optString("o"), base = o.optDouble("b", 0.0),
                deadline = if (o.has("dl")) o.getString("dl") else null, openDate = o.optString("op"),
                estimate = o.optDouble("e", Double.NaN), aValue = o.optDouble("a", Double.NaN), netCost = o.optDouble("c", Double.NaN),
                rate = o.optDouble("r", Double.NaN), variation = o.optDouble("v", Double.NaN),
            )
        }
    }.getOrDefault(emptyList())

    // ---- 즐겨찾기 / 검색 이력 (형식은 Codec) ----
    fun saveFavorites(list: List<SavedItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(Codec.favoriteToJson(it)) }
        file("favorites.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadFavorites(): List<SavedItem> = runCatching {
        val f = file("favorites.json")
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        (0 until arr.length()).map { Codec.jsonToFavorite(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(Codec.historyToJson(it)) }
        file("history.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadHistory(): List<HistoryItem> = runCatching {
        val f = file("history.json")
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        (0 until arr.length()).map { Codec.jsonToHistory(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
