package kr.landscapebid.engine

import kotlin.math.abs
import kotlin.math.max

/** golden.tsv: section \t case \t key \t value  (원본 JS 가 실제로 계산한 결과) */
class Golden(text: String) {
    val data = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>()
    init {
        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            val p = line.split('\t', limit = 4)
            data.getOrPut(p[0]) { LinkedHashMap() }.getOrPut(p[1]) { LinkedHashMap() }[p[2]] = p.getOrElse(3) { "" }
        }
    }
    fun cases(section: String): Map<String, Map<String, String>> = data[section] ?: emptyMap()
    fun case(section: String, id: String): Map<String, String> = data[section]?.get(id) ?: emptyMap()
}

class Report {
    val passed = LinkedHashMap<String, Int>()
    val failedCount = LinkedHashMap<String, Int>()
    val failures = ArrayList<String>()
    val totalFailed get() = failedCount.values.sum()
    val totalPassed get() = passed.values.sum()

    private fun record(section: String, ok: Boolean, msg: () -> String) {
        if (ok) passed[section] = (passed[section] ?: 0) + 1
        else {
            failedCount[section] = (failedCount[section] ?: 0) + 1
            if (failures.size < 60) failures.add("[$section] ${msg()}")
        }
    }
    fun eq(section: String, where: String, expected: String, actual: String) =
        record(section, expected == actual) { "$where: 기대 <$expected> 실제 <$actual>" }

    /** rel = 0 이면 완전 일치, 아니면 상대 오차 허용 */
    fun near(section: String, where: String, expected: String, actual: Double, rel: Double = 1e-11) {
        val e = expected.toDouble()
        val ok = when {
            e.isNaN() -> actual.isNaN()
            e.isInfinite() -> actual == e
            else -> abs(actual - e) <= rel * max(1.0, abs(e))
        }
        record(section, ok) { "$where: 기대 $expected 실제 $actual" }
    }
    fun cond(section: String, where: String, ok: Boolean, detail: () -> String = { "" }) =
        record(section, ok) { "$where ${detail()}" }
}

object GoldenChecker {
    private fun dbl(s: String?): Double = JsMath.num(s)
    private fun nn(s: String?): Double? = dbl(s).let { if (it.isNaN()) null else it }
    private fun optNum(s: String?): Double? = if (s.isNullOrEmpty()) null else s.toDoubleOrNull()

    fun run(golden: Golden, bids: String, extra: String): Report {
        val rep = Report()
        val base = BidTsv.parse(bids)
        val ds = Dataset.build(base)

        // ---- 수식 ----
        for ((id, c) in golden.cases("rates")) {
            val b = c["in_b"]!!.toDouble(); val a = c["in_a"]!!.toDouble(); val s = c["in_s"]!!.toDouble(); val rate = c["in_rate"]!!.toDouble()
            val r = Rates.rateByEst(c["in_e"]!!.toDouble())
            rep.eq("rates", "$id rateByEst", c["out_rateByEst"]!!, r?.let { Format.jsNumber(it) } ?: "null")
            val fl = Rates.floorAt(b, a, rate, s)
            rep.near("rates", "$id floorAt", c["out_floorAt"]!!, fl, 0.0)
            rep.near("rates", "$id bidFor", c["out_bidFor"]!!, Rates.bidFor(b, a, s, rate), 0.0)
            rep.near("rates", "$id sOfBid", c["out_sOfBid"]!!, Rates.sOfBid(fl, b, a, rate), 0.0)
        }
        for ((id, c) in golden.cases("snap")) rep.near("snap", id, c["out"]!!, Rates.snapRate(c["in_v"]!!.toDouble()), 0.0)
        for ((id, c) in golden.cases("group")) rep.eq("group", "$id <${c["in_o"]}>", c["out"]!!, Grouping.groupOf(c["in_o"]))
        // 줄바꿈은 TSV 에 못 담으므로 직접 검사: JS 의 $ 는 문자열 맨 끝에서만 맞으므로 끝에 \n 이 있으면 구 이름으로 인식되지 않는다
        rep.eq("group", "끝 줄바꿈", "서울시 본청·사업소", Grouping.groupOf("서울특별시 강남구\n"))
        for ((id, c) in golden.cases("size")) rep.eq("size", "$id e=${c["in_e"]}", c["out"]!!, Grouping.sizeOf(c["in_e"]!!.toDouble()))

        // ---- 데이터셋: 건수·정렬·enrich ----
        rep.eq("dataset", "count", golden.case("dataset", "all")["count"]!!, ds.rows.size.toString())
        rep.near("dataset", "defaultS", golden.case("dataset", "all")["defaultS"]!!, ds.defaultExpectedS(), 0.0)
        val order = golden.cases("order")
        order.forEach { (i, c) -> rep.eq("order", "#$i", c["no"]!!, ds.rows.getOrNull(i.toInt())?.rec?.no ?: "(없음)") }
        val byNo = ds.rows.associateBy { it.rec.no }
        for ((no, c) in golden.cases("enrich")) {
            val r = byNo[no]
            if (r == null) { rep.cond("enrich", no, false) { "행 없음" }; continue }
            for (k in listOf("e", "rate", "s", "sw", "rA", "gap", "diff")) {
                val actual = when (k) { "e" -> r.e; "rate" -> r.rate; "s" -> r.s; "sw" -> r.sw; "rA" -> r.rA; "gap" -> r.gap; else -> r.diff }
                rep.near("enrich", "$no.$k", c[k]!!, actual, 1e-12)
            }
            rep.eq("enrich", "$no.grp", c["grp"]!!, r.grp); rep.eq("enrich", "$no.size", c["size"]!!, r.size); rep.eq("enrich", "$no.mon", c["mon"]!!, r.mon)
        }
        // 덮어쓰기/결측/제외 처리
        val ds2 = Dataset.build(base, BidTsv.parse(extra))
        rep.eq("dataset2", "count", golden.case("dataset2", "all")["count"]!!, ds2.rows.size.toString())
        golden.cases("order2").forEach { (i, c) ->
            val r = ds2.rows.getOrNull(i.toInt())
            rep.eq("order2", "#$i no", c["no"]!!, r?.let { if (it.rec.no.isEmpty()) "(" + it.rec.name + it.rec.date + ")" else it.rec.no } ?: "(없음)")
            if (r != null) {
                rep.near("order2", "#$i rate", c["rate"]!!, r.rate, 0.0); rep.near("order2", "#$i e", c["e"]!!, r.e, 1e-12); rep.near("order2", "#$i x", c["x"]!!, r.winBid, 0.0)
            }
        }

        // ---- 분포 ----
        for ((id, c) in golden.cases("dist")) {
            val d = ds.dist(c["in_g"])
            rep.eq("dist", "$id n", c["n"]!!, d.n.toString()); rep.near("dist", "$id wS", c["wS"]!!, d.wS, 1e-14)
            rep.eq("dist", "$id hasSub", c["hasSub"]!!, if (d.sub != null) "1" else "0")
            for ((k, v) in c) {
                if (k.startsWith("cdf@")) rep.near("dist", "$id $k (${c["in_g"]})", v, d.cdf(k.substring(4).toDouble()), 1e-12)
                if (k.startsWith("inv@")) rep.near("dist", "$id $k (${c["in_g"]})", v, d.inv(k.substring(4).toDouble()), 1e-12)
            }
        }
        golden.case("gap", "all").let { c ->
            val g = ds.gapStats()
            rep.eq("gap", "cnt", c["cnt"]!!, g.counts.joinToString(";")); rep.eq("gap", "modeIdx", c["modeIdx"]!!, g.modeIdx.toString())
            rep.near("gap", "mode", c["mode"]!!, g.mode, 0.0); rep.eq("gap", "n", c["n"]!!, g.sortedGap.size.toString())
            for ((k, v) in c) if (k.startsWith("q@")) rep.near("gap", k, v, g.q(k.substring(2).toDouble()), 1e-12)
        }

        // ---- 통계 ----
        golden.case("stats", "all").let { c ->
            rep.near("stats", "mean", c["mean"]!!, Stats.mean(ds.sAll), 1e-12); rep.near("stats", "sd", c["sd"]!!, Stats.sd(ds.sAll), 1e-12)
            rep.near("stats", "median", c["median"]!!, Stats.median(ds.sAll), 0.0)
            for ((k, v) in c) if (k.startsWith("quant@")) rep.near("stats", k, v, Stats.quant(ds.sAll, k.substring(6).toDouble()), 1e-12)
        }
        for ((id, c) in golden.cases("gamma")) rep.near("gamma", id, c["out"]!!, Stats.gammaP(c["in_a"]!!.toDouble(), c["in_x"]!!.toDouble()), 1e-10)
        for ((id, c) in golden.cases("lgamma")) rep.near("lgamma", id, c["out"]!!, Stats.lgamma(c["in_z"]!!.toDouble()), 1e-10)
        for ((name, c) in golden.cases("kruskal")) {
            val keyOf: (Enriched) -> String = when (name) { "grp" -> { r -> r.grp }; "size" -> { r -> r.size }; else -> { r -> r.rec.date.take(4) } }
            val groups = c["names"]!!.split('|').map { n -> ds.rows.filter { keyOf(it) == n }.map { it.s }.toDoubleArray() }
            val k = Stats.kruskal(groups)
            rep.near("kruskal", "$name H", c["H"]!!, k.h, 1e-9); rep.eq("kruskal", "$name df", c["df"]!!, k.df.toString()); rep.near("kruskal", "$name p", c["p"]!!, k.p, 1e-9)
        }

        // ---- 백테스트 ----
        for ((id, c) in golden.cases("backtest")) {
            val s = c["in_s"]!!.toDouble(); val off = c["in_off"]!!.toDouble(); val g = c["in_g"]!!
            val all = ds.backtestTS(s, off)
            val sub = ds.backtestTS(s, off, ds.rows.filter { it.grp == g })
            val old = ds.backtest(s)
            fun chk(p: String, n: Int, q: Int, win: Int, low: Int?, high: Int?) {
                rep.eq("backtest", "$id ${p}n", c["${p}n"]!!, n.toString()); rep.eq("backtest", "$id ${p}q", c["${p}q"]!!, q.toString()); rep.eq("backtest", "$id ${p}win", c["${p}win"]!!, win.toString())
                if (low != null) rep.eq("backtest", "$id ${p}low", c["${p}low"]!!, low.toString())
                if (high != null) rep.eq("backtest", "$id ${p}high", c["${p}high"]!!, high.toString())
            }
            chk("all_", all.n, all.q, all.win, all.low, all.high); chk("grp_", sub.n, sub.q, sub.win, sub.low, sub.high); chk("old_", old.n, old.q, old.win, null, null)
        }

        // ---- 시뮬레이션 (시드 고정 PRNG 가 비트 단위로 같아야 한다) ----
        for ((id, c) in golden.cases("sim")) {
            val out = Simulation.simS(id[0], id.substring(1).toDouble())
            rep.eq("sim", "$id len", c["len"]!!, out.size.toString())
            for ((k, v) in c) if (k.startsWith("at@")) rep.near("sim", "$id $k", v, out[k.substring(3).toInt()], 0.0)
            var sum = 0.0; for (v in out) sum += v
            rep.near("sim", "$id sum", c["sum"]!!, sum, 1e-12)
        }

        // ---- num / 포맷 ----
        for ((id, c) in golden.cases("num")) rep.near("num", "$id ${c["in"]}", c["out"]!!, JsMath.num(c["in"]!!.removeSurrounding("\"").let { unescapeJson(it) }), 0.0)
        for ((id, c) in golden.cases("fmt")) {
            val v = c["in"]!!.toDouble()
            for (d in listOf(0, 1, 3, 4)) rep.eq("fmt", "$id toFixed$d (v=$v)", c["toFixed$d"]!!, JsMath.toFixed(v, d))
            rep.eq("fmt", "$id won (v=$v)", c["won"]!!, Format.won(v)); rep.eq("fmt", "$id f3 (v=$v)", c["f3"]!!, Format.f3(v))
            rep.eq("fmt", "$id trunc4 (v=$v)", c["trunc4"]!!, Format.trunc4(v)); rep.eq("fmt", "$id trunc3 (v=$v)", c["trunc3"]!!, Format.trunc3(v))
            rep.eq("fmt", "$id round (v=$v)", c["round"]!!, Format.jsNumber(JsMath.round(v)))
        }


        // ---- 만원 표기 ----
        for ((id, c) in golden.cases("man")) {
            val v = c["in"]!!.toDouble()
            rep.eq("man", "$id man (v=$v)", c["out"]!!, Format.man(v)); rep.eq("man", "$id axis (v=$v)", c["axis"]!!, Format.manAxis(v))
        }

        // ---- 사정율 선택 차트 (drawPick) ----
        for ((id, c) in golden.cases("pick")) {
            val g = c["in_g"]!!; val w = c["in_W"]!!.toDouble(); val pw = c["in_pw"]!!.toDouble()
            val b = c["in_b"]!!.toDouble(); val a = c["in_a"]!!.toDouble(); val rate = c["in_rate"]!!.toDouble()
            val m = PickChart.compute(ds, ds.dist(g), pw, narrow = w < 600)
            rep.eq("pick", "$id nb", c["nb"]!!, m.vol.size.toString())
            rep.near("pick", "$id lo", c["lo"]!!, m.lo, 0.0); rep.near("pick", "$id hi", c["hi"]!!, m.hi, 0.0)
            fun arr(key: String, actual: DoubleArray, rel: Double) {
                val exp = c[key]!!.split(';').filter { it.isNotEmpty() }.map { it.toDouble() }
                rep.cond("pick", "$id $key 길이", exp.size == actual.size) { "기대 ${exp.size} 실제 ${actual.size}" }
                if (exp.size == actual.size) exp.forEachIndexed { i, e -> rep.near("pick", "$id $key[$i]", e.toString(), actual[i], rel) }
            }
            arr("vol", m.vol, 1e-12); arr("kdeX", m.kdeX, 1e-12); arr("kdeY", m.kdeY, 1e-10)
            for ((k, v) in listOf("modeS" to m.modeS, "meanS" to m.mean, "q25" to m.q25, "q75" to m.q75, "vmax" to m.vmax, "med" to m.median)) rep.near("pick", "$id $k", c[k]!!, v, 1e-11)
            fun r4(v: Double) = JsMath.round(v * 10000) / 10000
            rep.eq("pick", "$id pbMode", c["pbMode"]!!, Format.won(Rates.floorAt(b, a, rate, r4(m.modeS))))
            rep.eq("pick", "$id pbMed", c["pbMed"]!!, Format.won(Rates.floorAt(b, a, rate, r4(m.median))))
            rep.eq("pick", "$id pbMean", c["pbMean"]!!, Format.won(Rates.floorAt(b, a, rate, r4(m.mean))))
        }

        // ---- 예정 투찰금액 분포 (drawPre) ----
        for ((id, c) in golden.cases("pre")) {
            val g = c["in_g"]!!
            val pre = PreDistributionCalc.compute(ds, ds.dist(g), c["in_b"]!!.toDouble(), c["in_a"]!!.toDouble(), c["in_rate"]!!.toDouble(), g, JsMath.num(c["in_inV"]).let { if (it.isNaN()) null else it }, c["in_unit"]!!.toInt())
            rep.eq("pre", "$id note", c["note"]!!, pre.note)
            (pre.rows + pre.cPlannedRow).forEachIndexed { j, r ->
                rep.eq("pre", "$id stat$j name", c["stat${j}_c0"]!!, r.name)
                r.quantiles.forEachIndexed { k, v -> rep.eq("pre", "$id stat$j q$k", c["stat${j}_c${k + 1}"]!!, Format.won(v)) }
                rep.eq("pre", "$id stat$j mean", c["stat${j}_c6"]!!, Format.won(r.mean))
            }
            rep.eq("pre", "$id tbl_n", c["tbl_n"]!!, pre.bins.toString())
            for (j in 0 until pre.bins) {
                rep.eq("pre", "$id tbl$j range", c["tbl${j}_c0"] ?: "?", pre.binRange(j))
                rep.eq("pre", "$id tbl$j A", c["tbl${j}_c1"] ?: "?", JsMath.toFixed(pre.hA[j] * 100, 1) + "%")
                rep.eq("pre", "$id tbl$j B", c["tbl${j}_c2"] ?: "?", JsMath.toFixed(pre.hB[j] * 100, 1) + "%")
                rep.eq("pre", "$id tbl$j C", c["tbl${j}_c3"] ?: "?", JsMath.toFixed(pre.hC[j] * 100, 1) + "%")
            }
            rep.eq("pre", "$id labels", c["labels"]!!, (0 until pre.bins).joinToString(";") { pre.binLabel(it) })
            listOf(pre.hA, pre.hB, pre.hC).forEachIndexed { j, h ->
                val exp = c["ds$j"]!!.split(';').map { it.toDouble() }
                rep.cond("pre", "$id ds$j 길이", exp.size == h.size)
                if (exp.size == h.size) exp.forEachIndexed { i, e -> rep.near("pre", "$id ds$j[$i]", e.toString(), JsMath.toFixed(h[i] * 100, 3).toDouble(), 1e-12) }
            }
        }

        // ---- 사정율 빠른 선택 칩 ----
        for ((id, c) in golden.cases("chips")) {
            val g = c["in_g"]!!
            val chips = ds.sChips(ds.dist(g), g)
            chips.forEachIndexed { j, ch ->
                rep.eq("chips", "$id v$j", c["v$j"] ?: "?", JsMath.toFixed(ch.value, 4))
                rep.eq("chips", "$id pressed$j", c["pressed$j"] ?: "?", (Math.abs(ch.value - 0.0123) < 0.00005).toString())
                rep.eq("chips", "$id label$j", c["label$j"] ?: "?", ch.label + (if (ch.label.contains("0%")) "" else " " + Format.f3(ch.value) + "%"))
            }
            rep.cond("chips", "$id 개수", !c.containsKey("v${chips.size}")) { "원본에 칩이 더 있음" }
        }


        // ---- 엑셀 읽기 (낙찰정보리스트 / 공고 목록) ----
        fun decode(c: Map<String, String>): List<List<Any?>> = (0 until c["nrows"]!!.toInt()).map { i ->
            (0 until c["r${i}n"]!!.toInt()).map { j -> val v = c["r${i}c$j"]!!; if (v.startsWith("n|")) v.substring(2).toDouble() else v.substring(2) }
        }
        for ((id, c) in golden.cases("imp_res")) {
            val res = NoticeImport.parseResults(decode(golden.case("imp_in", "res_$id")))
            rep.eq("imp_res", "$id err", c["err"]!!, res.error ?: ""); rep.eq("imp_res", "$id skip", c["skip"]!!, res.skipped.toString()); rep.eq("imp_res", "$id n", c["n"]!!, res.records.size.toString())
            res.records.forEachIndexed { i, r ->
                rep.eq("imp_res", "$id r$i n", c["r${i}_n"]!!, r.name); rep.eq("imp_res", "$id r$i no", c["r${i}_no"]!!, r.no); rep.eq("imp_res", "$id r$i o", c["r${i}_o"]!!, r.org)
                rep.eq("imp_res", "$id r$i d", c["r${i}_d"]!!, r.date); rep.eq("imp_res", "$id r$i co", c["r${i}_co"]!!, r.company)
                for ((k, v) in listOf("b" to r.base, "x" to r.winBid, "a" to r.aValue, "p" to r.planned, "e" to r.estimate, "f" to r.floorPrice)) rep.near("imp_res", "$id r$i $k", c["r${i}_$k"]!!, v, 0.0)
                val cc = c["r${i}_c"]!!
                if (cc == "null") rep.cond("imp_res", "$id r$i c", r.netCost == 0.0) { "기대 null 실제 ${r.netCost}" } else rep.near("imp_res", "$id r$i c", cc, r.netCost, 0.0)
            }
        }
        val noticeGroups = golden.case("imp_notice", "meta")["groups"]!!.split('|').toSet()
        rep.cond("imp_notice", "그룹 목록", noticeGroups == ds.groupCounts().keys) { "기대 $noticeGroups 실제 ${ds.groupCounts().keys}" }
        for ((id, c) in golden.cases("imp_notice")) {
            if (id == "meta") continue
            val list = NoticeImport.parseNotices(decode(golden.case("imp_in", "nt_$id")))
            rep.eq("imp_notice", "$id isNull", c["isNull"]!!, if (list == null) "1" else "0")
            if (list == null) continue
            rep.eq("imp_notice", "$id n", c["n"]!!, list.size.toString())
            list.forEachIndexed { i, x ->
                rep.eq("imp_notice", "$id x$i n", c["x${i}_n"]!!, x.name); rep.eq("imp_notice", "$id x$i no", c["x${i}_no"]!!, x.no); rep.eq("imp_notice", "$id x$i o", c["x${i}_o"]!!, x.org)
                rep.eq("imp_notice", "$id x$i dl", c["x${i}_dl"]!!, x.deadline ?: "null"); rep.eq("imp_notice", "$id x$i op", c["x${i}_op"]!!, x.openDate)
                for ((k, v) in listOf("b" to x.base, "e" to x.estimate, "a" to x.aValue, "c" to x.netCost, "r" to x.rate, "v" to x.variation)) rep.near("imp_notice", "$id x$i $k", c["x${i}_$k"]!!, v, 0.0)
            }
            // 목록 화면 (전체 보기; 마감 여부와 무관한 부분: 순서, 제목, 설명줄, 50/70/90% 금액)
            val lc = golden.case("imp_list", id)
            val items = NoticeList.items(ds, list, true, "2026-11-04T12:00")
            rep.eq("imp_list", "$id n", lc["n"]!!, items.size.toString())
            items.forEachIndexed { i, it ->
                rep.eq("imp_list", "$id i$i idx", lc["i${i}_idx"]!!, it.index.toString()); rep.eq("imp_list", "$id i$i title", lc["i${i}_title"]!!, it.notice.title)
                rep.eq("imp_list", "$id i$i meta", lc["i${i}_meta"]!!, it.meta())
                rep.eq("imp_list", "$id i$i q", lc["i${i}_q"]!!, it.quick?.let { q -> listOf(q.p50, q.p70, q.p90).joinToString(";") { v -> Format.won(v) } } ?: "")
            }
            val fc = golden.case("imp_form", id)
            list.forEachIndexed { i, x -> rep.eq("imp_form", "$id x$i rate", fc["x${i}_rate"]!!, NoticeList.toForm(x, noticeGroups).rate) }
        }
        // 마감 시각 비교는 한국 시간끼리: 마감 후에는 바로 "마감 지남"
        run {
            val n = Notice("a", "", "", 1.0, "2026-11-04T14:00", "", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
            rep.cond("imp_list", "마감 전", NoticeList.openCount(listOf(n), "2026-11-04T13:59") == 1)
            rep.cond("imp_list", "마감 후", NoticeList.openCount(listOf(n), "2026-11-04T14:01") == 0)
        }

        // ---- 계산기 전체 (원본 calc() 와 비교) ----
        for ((id, c) in golden.cases("calc")) checkCalc(rep, ds, id, c)
        return rep
    }

    private fun unescapeJson(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun checkCalc(rep: Report, ds: Dataset, id: String, c: Map<String, String>) {
        val sIn = c["in_s"]!!
        val input = CalcInput(
            base = dbl(c["in_b"]), aValue = dbl(c["in_a"]).let { if (it.isNaN()) 0.0 else it }, estimate = nn(c["in_e"]), netCost = nn(c["in_c"]),
            group = c["in_g"]!!, rateOverride = nn(c["in_r"]), expectedS = if (sIn == "default") null else dbl(sIn),
            plannedPrice = nn(c["in_p"]), myAmount = nn(c["in_x"]),
        )
        val out = Calculator.calculate(ds, input)
        if (c["out_ok"] == "0") {
            rep.cond("calc", "$id 거절 처리", out is CalcOutcome.Rejected) { "원본은 계산 불가인데 Kotlin 은 계산함" }
            if (out is CalcOutcome.Rejected) rep.eq("calc", "$id 거절 문구", c["out_msg"]!!, out.message)
            return
        }
        if (out !is CalcOutcome.Ok) { rep.cond("calc", "$id", false) { "원본은 계산했는데 Kotlin 은 거절: $out" }; return }
        val r = out.result
        fun n(k: String, v: Double, rel: Double = 1e-11) = rep.near("calc", "$id $k", c["out_$k"]!!, v, rel)
        n("rate", r.rate, 0.0); n("e", r.e, 1e-12); n("sExp", r.sExp, 0.0); n("sBid", r.sAtBid, 1e-12); n("B", r.bid, 0.0)
        n("pQ", r.pBelow); n("nearP", r.nearProb); n("pExp", r.plannedPrice, 1e-12); n("floorExp", r.floorExp, 0.0); n("off", 0.0, 0.0)
        for (k in listOf("n", "q", "win", "low", "high")) {
            val a = r.backtestAll
            rep.eq("calc", "$id bt_$k", c["out_bt_$k"]!!, when (k) { "n" -> a.n; "q" -> a.q; "win" -> a.win; "low" -> a.low; else -> a.high }.toString())
        }
        rep.eq("calc", "$id hasBtG", c["out_hasBtG"]!!, if (r.backtestGroup != null) "1" else "0")
        r.backtestGroup?.let { g ->
            for (k in listOf("n", "q", "win", "low", "high"))
                rep.eq("calc", "$id btG_$k", c["out_btG_$k"]!!, when (k) { "n" -> g.n; "q" -> g.q; "win" -> g.win; "low" -> g.low; else -> g.high }.toString())
        }
        // 화면에 찍히던 문자열
        rep.eq("calc", "$id bidNum", c["out_bidNum"]!!, Format.won(r.bid) + "<small>원</small>")
        val given = input.plannedPrice != null && input.plannedPrice > 0
        rep.eq("calc", "$id bidMeta", c["out_bidMeta"]!!,
            "사정율 <b>" + (if (r.sExp >= 0) "+" else "") + JsMath.toFixed(r.sExp, 4) + "%</b> · " + (if (given) "예정가격" else "예상 예정가격") +
                " " + Format.won(r.plannedPrice) + "원 · 투찰/기초 " + Format.trunc3(r.bidRatioToBase) + "%")
        rep.eq("calc", "$id probBig", c["out_probBig"]!!, JsMath.toFixed(r.nearProb * 100, 1) + "%")
        rep.eq("calc", "$id compBig", c["out_compBig"]!!, r.competitorRate?.let { JsMath.toFixed(it * 100, 1) + "%" } ?: "데이터 필요")
        rep.eq("calc", "$id sProb", c["out_sProb"]!!, JsMath.toFixed(r.pExpected * 100, 0) + "%")
        rep.eq("calc", "$id alert_bad", c["out_alert_bad"]!!, if (r.belowNetCostLimit == true) "1" else "0")
        rep.eq("calc", "$id alert_zeroA", c["out_alert_zeroA"]!!, if (r.aValueMissing) "1" else "0")
        rep.eq("calc", "$id alert_c", c["out_alert_c"]!!, r.netCostLimit?.let { Format.won(it) } ?: "")
        // 시나리오 표
        rep.eq("calc", "$id scen_n", c["out_scen_n"]!!, r.scenarios.size.toString())
        r.scenarios.forEachIndexed { j, row ->
            rep.eq("calc", "$id scen$j hl", c["out_scen${j}_hl"] ?: "?", if (row.isExpected) "1" else "0")
            rep.eq("calc", "$id scen$j c0", c["out_scen${j}_c0"] ?: "?", Format.f3(row.s) + "%")
            rep.eq("calc", "$id scen$j c1", c["out_scen${j}_c1"] ?: "?", Format.won(row.plannedPrice))
            rep.eq("calc", "$id scen$j c2", c["out_scen${j}_c2"] ?: "?", Format.won(row.floorPrice))
            rep.eq("calc", "$id scen$j c3", c["out_scen${j}_c3"] ?: "?", (if (row.diff >= 0) "+" else "") + Format.won(row.diff))
            rep.eq("calc", "$id scen$j c4", c["out_scen${j}_c4"] ?: "?", if (row.passes) "하한가 이상" else "미달 탈락")
        }
        // 보조 정보
        val kv = r.summaryItems()
        kv.forEachIndexed { j, (k, v) -> rep.eq("calc", "$id kv$j key", c["out_kv${j}_k"] ?: "?", k); rep.eq("calc", "$id kv$j val", c["out_kv${j}_v"] ?: "?", v) }
        rep.cond("calc", "$id kv 개수", !c.containsKey("out_kv${kv.size}_k")) { "원본에 항목이 더 있음" }
        val kx = r.reviewItems()
        kx.forEachIndexed { j, (k, v) -> rep.eq("calc", "$id kvx$j key", c["out_kvx${j}_k"] ?: "?", k); rep.eq("calc", "$id kvx$j val", c["out_kvx${j}_v"] ?: "?", v) }
        rep.cond("calc", "$id kvx 개수", !c.containsKey("out_kvx${kx.size}_k")) { "원본에 항목이 더 있음 (또는 Kotlin 이 누락)" }
    }
}
