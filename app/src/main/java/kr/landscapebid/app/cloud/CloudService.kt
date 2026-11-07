package kr.landscapebid.app.cloud

import android.app.Activity
import android.content.Context
import kr.landscapebid.app.data.HistoryItem
import kr.landscapebid.app.data.SavedItem

/** 로그인한 사용자 */
data class AuthUser(val uid: String, val email: String?, val name: String?)

/**
 * 로그인과 계정별 클라우드 저장소. 화면·ViewModel 은 이 인터페이스만 안다.
 * Firebase 설정 파일(google-services.json)이 있으면 실제 구현이, 없으면 "준비 중" 구현이 들어간다. (app/build.gradle.kts 참고)
 *
 * 쓰기(upsert/delete)는 기다리지 않고 던져 두는 방식이다. Firestore 가 기기에 쌓아 두었다가 인터넷이 되면 올린다.
 */
interface CloudService {
    /** 이 빌드에 로그인/클라우드가 켜져 있는지 */
    val isAvailable: Boolean

    fun currentUser(): AuthUser?
    suspend fun signInWithGoogle(activity: Activity): Result<AuthUser>
    fun signOut()

    suspend fun fetchFavorites(uid: String): List<SavedItem>
    suspend fun fetchHistory(uid: String): List<HistoryItem>
    fun upsertFavorites(uid: String, items: List<SavedItem>)
    fun deleteFavorites(uid: String, ids: List<String>)
    fun upsertHistory(uid: String, items: List<HistoryItem>)
    fun deleteHistory(uid: String, ids: List<String>)
}

