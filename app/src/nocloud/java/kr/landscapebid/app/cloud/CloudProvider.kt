package kr.landscapebid.app.cloud

import android.app.Activity
import android.content.Context
import kr.landscapebid.app.data.HistoryItem
import kr.landscapebid.app.data.SavedItem

/** google-services.json 이 없을 때 쓰는 "준비 중" 구현. 앱은 로그인 없이 기기 안에만 저장하며 정상 동작한다. */
object CloudProvider {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): CloudService = NoCloudService
}

private object NoCloudService : CloudService {
    override val isAvailable: Boolean = false
    override fun currentUser(): AuthUser? = null
    override suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> =
        Result.failure(IllegalStateException("로그인 기능이 아직 설정되지 않았습니다 (Firebase 설정 파일 필요)"))
    override fun signOut() {}
    override suspend fun fetchFavorites(uid: String): List<SavedItem> = emptyList()
    override suspend fun fetchHistory(uid: String): List<HistoryItem> = emptyList()
    override fun upsertFavorites(uid: String, items: List<SavedItem>) {}
    override fun deleteFavorites(uid: String, ids: List<String>) {}
    override fun upsertHistory(uid: String, items: List<HistoryItem>) {}
    override fun deleteHistory(uid: String, ids: List<String>) {}
}
