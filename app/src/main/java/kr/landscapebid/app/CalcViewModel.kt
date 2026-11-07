package kr.landscapebid.app

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.landscapebid.app.cloud.AuthUser
import kr.landscapebid.app.cloud.CloudProvider
import kr.landscapebid.app.cloud.CloudService
import kr.landscapebid.app.data.FormSnapshot
import kr.landscapebid.app.data.HistoryItem
import kr.landscapebid.app.data.Merge
import kr.landscapebid.app.data.RowsReader
import kr.landscapebid.app.data.SavedItem
import kr.landscapebid.app.data.Store
import kr.landscapebid.engine.BidRecord
import kr.landscapebid.engine.BidTsv
import kr.landscapebid.engine.Dataset
import kr.landscapebid.engine.Format
import kr.landscapebid.engine.JsMath
import kr.landscapebid.engine.Notice
import kr.landscapebid.engine.NoticeImport
import kr.landscapebid.engine.NoticeList
import kr.landscapebid.engine.Simulation
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class UploadMsg(val text: String, val bad: Boolean)

/** 원본 화면의 입력칸(inB, inA, ...)과 같은 이름의 상태를 들고 있는 곳. 계산은 :engine 이 한다. */
class CalcViewModel(application: Application) : AndroidViewModel(application) {
    var dataset by mutableStateOf<Dataset?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    var inName by mutableStateOf("")               // 공고명 (선택)
    var inB by mutableStateOf("148,870,940")       // 기초금액
    var inA by mutableStateOf("12,056,532")        // A값
    var inE by mutableStateOf("")                  // 추정가격
    var inC by mutableStateOf("")                  // 순공사원가
    var inX by mutableStateOf("")                  // 투찰금액(금액으로 확인)
    var inP by mutableStateOf("")                  // 예정가격 직접 입력(개찰 후 검증)
    var group by mutableStateOf("전체")             // 발주기관
    var rateSel by mutableStateOf("")              // 낙찰하한율 ("" = 자동)
    var rangeSel by mutableStateOf("")             // 예가 범위 ("" = 자동, "3", "2")
    var inS by mutableStateOf("")                  // 예상 사정율(%)
    var probW by mutableStateOf(0.05)              // 근접 확률 폭 ±%p
    var unit by mutableStateOf(100000)             // 예정 투찰금액 분포 구간 단위
    var previewS by mutableStateOf<Double?>(null)  // 차트를 끄는 동안의 미리보기 사정율

    var extra by mutableStateOf<List<BidRecord>>(emptyList())      // 사용자가 엑셀로 추가한 낙찰 데이터
        private set
    var notices by mutableStateOf<List<Notice>>(emptyList())       // 불러온 공고 목록
        private set
    var showAllNotices by mutableStateOf(false)                    // "마감 지난 것도 보기"
    var curNoticeNo by mutableStateOf("")                          // 지금 계산기에 채워 둔 공고번호
        private set
    var uploadMsg by mutableStateOf<UploadMsg?>(null)              // 엑셀 불러오기 결과 안내
        private set
    var applyTick by mutableIntStateOf(0)                          // 공고를 누를 때마다 올라감(화면 스크롤용)
        private set

    var curOrg by mutableStateOf("")                               // 지금 채워 둔 공고의 발주기관
        private set

    // ---- 화면 이동 ----
    var route by mutableStateOf(Route(Tab.RECENT))
        private set
    private val backStack = mutableStateListOf<Route>()
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    /** 홈 = 최근 공고 화면. 이미 홈이고 돌아갈 곳도 없으면 true */
    val isHome: Boolean get() = route == Route(Tab.RECENT) && backStack.isEmpty()

    // ---- 최근 공고 화면의 검색·필터 ----
    var recentQuery by mutableStateOf("")
    var recentCategory by mutableStateOf("전체")
    var recentOpenOnly by mutableStateOf(true)

    // ---- 즐겨찾기 / 검색 이력 ----
    var favorites by mutableStateOf<List<SavedItem>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    private var baseRecs: List<BidRecord> = emptyList()
    private val store = Store(application.filesDir)            // 기기 공통: 추가한 엑셀 데이터, 공고 목록, 로그인 안 했을 때의 즐겨찾기·이력
    private val cloud: CloudService = CloudProvider.create(application)
    private var userStore: Store = store                        // 지금 계정의 즐겨찾기·이력이 저장되는 곳

    // ---- 계정 ----
    var user by mutableStateOf<AuthUser?>(null)
        private set
    var authMsg by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    val cloudAvailable: Boolean get() = cloud.isAvailable

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val ds = withContext(Dispatchers.Default) {
                    val text = getApplication<Application>().assets.open("bids.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    baseRecs = BidTsv.parse(text)
                    val ex = store.loadExtra()
                    val nt = store.loadNotices()
                    val built = Dataset.build(baseRecs, ex)
                    // 예정가격 분포 시뮬레이션(10만 번)을 미리 돌려 둔다 — 화면에서 처음 열 때 멈칫하지 않게
                    for (r in listOf(3.0, 2.0)) { Simulation.simS('B', r); Simulation.simS('C', r) }
                    val cu = cloud.currentUser()
                    val us = if (cu != null) accountStore(cu.uid) else store
                    Loaded(built, ex, nt, us.loadFavorites(), us.loadHistory(), cu, us)
                }
                extra = ds.extra
                notices = ds.notices
                favorites = ds.favorites
                history = ds.history
                user = ds.user
                userStore = ds.userStore
                dataset = ds.dataset
                if (ds.user != null) syncNow()
                if (inS.isEmpty()) inS = fmtS(ds.dataset.defaultExpectedS())
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
            }
        }
    }

    private class Loaded(
        val dataset: Dataset, val extra: List<BidRecord>, val notices: List<Notice>,
        val favorites: List<SavedItem>, val history: List<HistoryItem>, val user: AuthUser?, val userStore: Store,
    )

    private fun keyOf(r: BidRecord) = if (r.no.isNotEmpty()) r.no else r.name + r.date

    private fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "파일")

    private fun readAll(uri: Uri): ByteArray =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("파일을 열 수 없습니다")

    private class ImportOutcome(val extra: List<BidRecord>, val ds: Dataset, val text: String, val bad: Boolean)

    /** "인포21C 엑셀 추가": 낙찰정보리스트를 읽어 기본 데이터에 더한다. 이미 있는 공고는 건너뛴다. */
    fun importResults(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uploadMsg = UploadMsg("읽는 중...", false)
            try {
                val out = withContext(Dispatchers.Default) {
                    val add = ArrayList<BidRecord>()
                    val notes = ArrayList<String>()
                    for (u in uris) {
                        val name = displayName(u)
                        try {
                            val res = NoticeImport.parseResults(RowsReader.read(readAll(u)))
                            if (res.error != null) notes.add("$name: ${res.error}")
                            else { add.addAll(res.records); notes.add("$name: ${res.records.size}건 읽음" + (if (res.skipped > 0) ", 값 빠진 ${res.skipped}행 제외" else "")) }
                        } catch (e: Exception) {
                            notes.add("$name: 파일을 읽지 못했습니다 (${e.message})")
                        }
                    }
                    val before = dataset?.rows?.size ?: 0
                    val baseKeys = baseRecs.map { keyOf(it) }.toHashSet()
                    val merged = LinkedHashMap<String, BidRecord>()
                    extra.forEach { merged[keyOf(it)] = it }
                    var fresh = 0
                    for (r in add) { val k = keyOf(r); if (k in baseKeys) continue; if (!merged.containsKey(k)) fresh++; merged[k] = r }
                    val newExtra = merged.values.toList()
                    store.saveExtra(newExtra)
                    val built = Dataset.build(baseRecs, newExtra)
                    ImportOutcome(
                        newExtra, built,
                        notes.joinToString("\n") + "\n이미 있는 공고는 건너뛰고 새 공고 ${fresh}건 추가. 전체 ${before}건 → ${built.rows.size}건. 서울·조경 업종으로 필터한 파일인지 확인하세요.",
                        add.isEmpty(),
                    )
                }
                extra = out.extra
                dataset = out.ds
                uploadMsg = UploadMsg(out.text, out.bad)
            } catch (e: Exception) {
                uploadMsg = UploadMsg("엑셀을 불러오지 못했습니다: ${e.message}", true)
            }
        }
    }

    /** "기본 데이터로": 추가한 엑셀을 지운다 */
    fun resetExtra() {
        viewModelScope.launch {
            val built = withContext(Dispatchers.Default) { store.saveExtra(emptyList()); Dataset.build(baseRecs) }
            extra = emptyList()
            dataset = built
            uploadMsg = UploadMsg("기본 데이터로 되돌렸습니다.", false)
        }
    }

    /** "공고 목록 불러오기": 입찰리스트 엑셀 */
    fun importNotices(uri: Uri) {
        viewModelScope.launch {
            uploadMsg = UploadMsg("읽는 중...", false)
            val name = displayName(uri)
            try {
                val list = withContext(Dispatchers.Default) {
                    val l = NoticeImport.parseNotices(RowsReader.read(readAll(uri)))
                    if (!l.isNullOrEmpty()) store.saveNotices(l)
                    l
                }
                if (list.isNullOrEmpty()) {
                    uploadMsg = UploadMsg("\"공고명\"과 \"기초금액\" 열이 있는 입찰리스트 엑셀이 아닙니다.", true)
                } else {
                    notices = list
                    uploadMsg = UploadMsg("$name: 공고 ${list.size}건 불러옴. 목록에서 공고를 누르면 값이 채워집니다.", false)
                }
            } catch (e: Exception) {
                uploadMsg = UploadMsg("파일을 읽지 못했습니다: ${e.message}", true)
            }
        }
    }

    /** 공고를 눌렀을 때: 입력칸을 그 공고 값으로 채운다 (원본 applyNotice) */
    fun applyNotice(x: Notice) {
        val ds = dataset ?: return
        val f = NoticeList.toForm(x, ds.groupCounts().keys)
        curNoticeNo = x.no
        curOrg = x.org
        inName = f.name
        inB = Format.won(f.base)
        inA = f.aValue?.let { Format.won(it) } ?: ""
        inE = f.estimate?.let { Format.won(it) } ?: ""
        inC = f.netCost?.let { Format.won(it) } ?: ""
        rateSel = f.rate
        rangeSel = f.range
        group = f.group
        inP = ""
        inX = ""
        previewS = null
        uploadMsg = if (!x.aValue.isFinite()) UploadMsg("이 공고는 목록에 A값이 없습니다(용역·수의 등). A값 없이 계산되니 공고문을 확인하세요.", true) else null
        applyTick++
    }

    // ================= 화면 이동 =================

    fun navigate(to: Route) {
        if (to == route) return
        backStack.add(route)
        if (backStack.size > 40) backStack.removeAt(0)
        route = to
    }

    fun back() { if (backStack.isNotEmpty()) route = backStack.removeAt(backStack.lastIndex) }

    /** 홈(최근 공고)으로. 이동 기록은 비운다. */
    fun home() { backStack.clear(); route = Route(Tab.RECENT) }

    /** 위쪽 탭 누르기. 하한가 분석 탭은 항상 1단계(입력)로 연다. */
    fun selectTab(tab: Tab) = navigate(Route(tab, 1))

    // ================= 현재 입력 → 저장 가능한 값 =================

    private fun nowKst(): String = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    /** 지금 입력칸의 값. 기초금액이 없으면 null. */
    fun snapshot(): FormSnapshot? {
        val b = JsMath.num(inB)
        if (!(b > 0)) return null
        fun nn(s: String): Double? = JsMath.num(s).let { if (it.isNaN()) null else it }
        return FormSnapshot(
            name = inName.ifBlank { "이름 없는 계산" }, no = curNoticeNo, org = curOrg, base = b,
            aValue = nn(inA), estimate = nn(inE)?.takeIf { it > 0 }, netCost = nn(inC)?.takeIf { it > 0 },
            group = group, rate = rateSel, range = rangeSel,
        )
    }

    /** 저장해 둔 값으로 입력칸을 채운다. sExp 가 있으면 그 사정율로, 없으면 기본 사정율로. */
    fun loadSnapshot(f: FormSnapshot, sExp: Double?) {
        inName = f.name
        inB = Format.won(f.base)
        inA = f.aValue?.let { Format.won(it) } ?: ""
        inE = f.estimate?.let { Format.won(it) } ?: ""
        inC = f.netCost?.let { Format.won(it) } ?: ""
        group = f.group
        rateSel = f.rate
        rangeSel = f.range
        curNoticeNo = f.no
        curOrg = f.org
        inP = ""
        inX = ""
        previewS = null
        if (sExp != null) setS(sExp) else dataset?.let { inS = fmtS(it.defaultExpectedS()) }
    }

    private fun formOf(x: Notice, ds: Dataset): FormSnapshot {
        val f = NoticeList.toForm(x, ds.groupCounts().keys)
        return FormSnapshot(f.name, x.no, x.org, x.base, f.aValue, f.estimate, f.netCost, f.group, f.rate, f.range)
    }

    // ================= 저장 + 계정별 클라우드 동기화 =================

    private fun accountStore(uid: String) = Store(File(getApplication<Application>().filesDir, "users/$uid").also { it.mkdirs() })

    /** 즐겨찾기를 바꾼다: 화면 갱신 → 이 계정의 기기 저장소에 저장 → 로그인 상태면 바뀐 것만 클라우드로 */
    private fun updateFavorites(new: List<SavedItem>) {
        val old = favorites
        favorites = new
        val us = userStore
        val u = user
        viewModelScope.launch(Dispatchers.IO) {
            us.saveFavorites(new)
            if (u != null) runCatching {
                val before = old.associateBy { it.id }
                cloud.upsertFavorites(u.uid, new.filter { before[it.id] != it })
                cloud.deleteFavorites(u.uid, old.map { it.id }.filter { id -> new.none { it.id == id } })
            }
        }
    }

    private fun updateHistory(new: List<HistoryItem>) {
        val old = history
        history = new
        val us = userStore
        val u = user
        viewModelScope.launch(Dispatchers.IO) {
            us.saveHistory(new)
            if (u != null) runCatching {
                val before = old.associateBy { it.id }
                cloud.upsertHistory(u.uid, new.filter { before[it.id] != it })
                cloud.deleteHistory(u.uid, old.map { it.id }.filter { id -> new.none { it.id == id } })
            }
        }
    }

    // ---- 로그인 / 로그아웃 / 동기화 ----

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            authMsg = "로그인 중..."
            val result = cloud.signInWithGoogle(activity)
            val u = result.getOrNull()
            if (u == null) {
                authMsg = "로그인하지 못했습니다: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                return@launch
            }
            user = u
            mergeInto(u, includeGuest = true)
        }
    }

    fun signOut() {
        cloud.signOut()
        user = null
        userStore = store
        favorites = store.loadFavorites()
        history = store.loadHistory()
        authMsg = "로그아웃했습니다."
    }

    /** "지금 동기화" / 앱을 켤 때 */
    fun syncNow() {
        val u = user ?: return
        viewModelScope.launch { mergeInto(u, includeGuest = false) }
    }

    /**
     * 게스트로 쓰던 기기 데이터(첫 로그인일 때만) + 이 계정의 기기 데이터 + 클라우드를 합쳐 이 계정의 데이터로 만든다.
     * 클라우드에서 가져오지 못하면 아무것도 지우지 않는다 (게스트 데이터도 그대로 둔다).
     */
    private suspend fun mergeInto(u: AuthUser, includeGuest: Boolean) {
        syncing = true
        try {
            val acct = accountStore(u.uid)
            val remoteFav = runCatching { cloud.fetchFavorites(u.uid) }
            val remoteHist = runCatching { cloud.fetchHistory(u.uid) }
            val fetched = remoteFav.isSuccess && remoteHist.isSuccess
            val guestFav = if (includeGuest) store.loadFavorites() else emptyList()
            val guestHist = if (includeGuest) store.loadHistory() else emptyList()
            val mergedFav = Merge.favorites(listOf(guestFav, acct.loadFavorites(), remoteFav.getOrDefault(emptyList())))
            val mergedHist = Merge.history(listOf(guestHist, acct.loadHistory(), remoteHist.getOrDefault(emptyList())))
            withContext(Dispatchers.IO) {
                acct.saveFavorites(mergedFav)
                acct.saveHistory(mergedHist)
                if (fetched) {
                    cloud.upsertFavorites(u.uid, mergedFav)
                    cloud.upsertHistory(u.uid, mergedHist)
                    // 같은 공고가 다른 id 로 클라우드에 남아 있으면 정리
                    cloud.deleteFavorites(u.uid, remoteFav.getOrThrow().map { it.id }.filter { id -> mergedFav.none { it.id == id } })
                    cloud.deleteHistory(u.uid, remoteHist.getOrThrow().map { it.id }.filter { id -> mergedHist.none { it.id == id } })
                    if (includeGuest) { store.saveFavorites(emptyList()); store.saveHistory(emptyList()) }
                }
            }
            userStore = acct
            favorites = mergedFav
            history = mergedHist
            authMsg = if (fetched) "${u.email ?: u.name ?: "계정"} · 즐겨찾기 ${mergedFav.size}건, 검색 이력 ${mergedHist.size}건 동기화됨"
            else "클라우드에서 데이터를 가져오지 못했습니다. 인터넷 연결을 확인하고 \"지금 동기화\"를 눌러 주세요."
        } finally {
            syncing = false
        }
    }

    // ================= 검색 이력 =================

    /** 지금 보고 있는 공고를 검색 이력 맨 위에 남긴다 (같은 공고는 하나로, 100건까지) */
    fun recordHistory() {
        val f = snapshot() ?: return
        updateHistory(Merge.history(listOf(listOf(HistoryItem(UUID.randomUUID().toString(), f, nowKst())), history)))
    }

    fun removeHistory(id: String) = updateHistory(history.filter { it.id != id })

    fun clearHistory() = updateHistory(emptyList())

    fun openHistory(item: HistoryItem) {
        loadSnapshot(item.form, null)
        recordHistory()
        navigate(Route(Tab.ANALYSIS, 2))
    }

    // ================= 즐겨찾기 / 내가 정한 금액 =================

    private fun noticeKey(x: Notice): String = if (x.no.isNotEmpty()) "no:${x.no}" else "nm:${x.title}|${x.base.toLong()}"

    fun isFavorite(x: Notice): Boolean = favorites.any { it.form.key == noticeKey(x) }

    /** 공고 목록의 별표: 즐겨찾기에 넣고 빼기 (금액은 아직 정하지 않은 상태로 들어간다) */
    fun toggleFavorite(x: Notice) {
        val ds = dataset ?: return
        val k = noticeKey(x)
        updateFavorites(
            if (favorites.any { it.form.key == k }) favorites.filter { it.form.key != k }
            else listOf(SavedItem(UUID.randomUUID().toString(), formOf(x, ds), null, null, nowKst())) + favorites,
        )
    }

    /** 분석 2단계에서 "이 금액 저장": 지금 공고를 즐겨찾기에 넣고 내가 정한 사정율·투찰금액을 함께 저장 */
    fun saveMyAmount(sExp: Double, bid: Double) {
        val f = snapshot() ?: return
        val old = favorites.firstOrNull { it.form.key == f.key }
        val item = SavedItem(old?.id ?: UUID.randomUUID().toString(), f, sExp, bid, nowKst())
        updateFavorites(listOf(item) + favorites.filter { it.form.key != f.key })
    }

    fun removeFavorite(id: String) = updateFavorites(favorites.filter { it.id != id })

    /** my페이지에서 즐겨찾기를 누르면: 저장한 값과 사정율로 분석 2단계를 연다 */
    fun openFavorite(item: SavedItem) {
        loadSnapshot(item.form, item.sExp)
        recordHistory()
        navigate(Route(Tab.ANALYSIS, 2))
    }

    /** 최근 공고에서 공고를 누르면: 값을 채우고 바로 분석 2단계로 */
    fun openNoticeInAnalysis(x: Notice) {
        applyNotice(x)
        dataset?.let { inS = fmtS(it.defaultExpectedS()) }
        recordHistory()
        navigate(Route(Tab.ANALYSIS, 2))
    }

    /** 기초금액·A값·추정가격·순공사원가를 손으로 고치면 더는 그 공고 값이 아니므로 선택 표시를 푼다 */
    fun clearCurrentNotice() { if (curNoticeNo.isNotEmpty()) curNoticeNo = ""; if (curOrg.isNotEmpty()) curOrg = "" }

    /** 사정율을 정한다 (차트 선택, 칩, 슬라이더). 예정가격 직접 입력은 지운다. */
    fun setS(v: Double) {
        if (!v.isFinite()) return
        inS = fmtS(JsMath.round(v * 10000) / 10000)
        inP = ""
        previewS = null
    }

    companion object {
        /** 원본 fmtS: 소수 4자리로 맞추되 불필요한 0 은 뗀다 */
        fun fmtS(v: Double): String = Format.jsNumber(JsMath.toFixed(v, 4).toDouble())

        /** 원본 fmtInput: 숫자를 입력하면 천 단위 콤마를 붙인다 */
        fun money(text: String): String {
            val v = JsMath.num(text)
            return if (v.isFinite() && v > 0) Format.won(v) else text
        }
    }
}
