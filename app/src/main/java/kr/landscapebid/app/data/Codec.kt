package kr.landscapebid.app.data

import org.json.JSONObject

/** 즐겨찾기·검색 이력·공고 값을 JSON 으로 바꾸고 되돌린다. 파일 저장과 클라우드 저장이 같은 형식을 쓴다. */
object Codec {
    fun formToJson(f: FormSnapshot): JSONObject {
        val o = JSONObject().put("n", f.name).put("no", f.no).put("o", f.org).put("b", f.base).put("g", f.group).put("r", f.rate).put("v", f.range)
        f.aValue?.let { o.put("a", it) }
        f.estimate?.let { o.put("e", it) }
        f.netCost?.let { o.put("c", it) }
        return o
    }

    fun jsonToForm(o: JSONObject) = FormSnapshot(
        name = o.optString("n"), no = o.optString("no"), org = o.optString("o"), base = o.optDouble("b", 0.0),
        aValue = if (o.has("a")) o.getDouble("a") else null, estimate = if (o.has("e")) o.getDouble("e") else null,
        netCost = if (o.has("c")) o.getDouble("c") else null, group = o.optString("g", "전체"), rate = o.optString("r"), range = o.optString("v"),
    )

    fun favoriteToJson(s: SavedItem): JSONObject {
        val o = JSONObject().put("id", s.id).put("form", formToJson(s.form)).put("at", s.savedAt)
        s.sExp?.let { o.put("s", it) }
        s.bid?.let { o.put("bid", it) }
        return o
    }

    fun jsonToFavorite(o: JSONObject) = SavedItem(
        id = o.getString("id"), form = jsonToForm(o.getJSONObject("form")),
        sExp = if (o.has("s")) o.getDouble("s") else null, bid = if (o.has("bid")) o.getDouble("bid") else null, savedAt = o.optString("at"),
    )

    fun historyToJson(h: HistoryItem): JSONObject = JSONObject().put("id", h.id).put("form", formToJson(h.form)).put("at", h.at)

    fun jsonToHistory(o: JSONObject) = HistoryItem(id = o.getString("id"), form = jsonToForm(o.getJSONObject("form")), at = o.optString("at"))
}
