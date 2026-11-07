package kr.landscapebid.app.cloud

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kr.landscapebid.app.data.Codec
import kr.landscapebid.app.data.HistoryItem
import kr.landscapebid.app.data.SavedItem
import org.json.JSONObject

/** google-services.json 이 있을 때 쓰는 Firebase 구현 (Google 로그인 + 계정별 Firestore). */
object CloudProvider {
    fun create(context: Context): CloudService = FirebaseCloudService(context.applicationContext)
}

/**
 * 저장 위치: users/{uid}/favorites/{id}, users/{uid}/history/{id}  — 문서 하나에 항목 JSON 을 문자열로 담는다.
 * 보안 규칙은 "본인(uid)만 읽고 쓰기" 여야 한다. docs/FIREBASE_SETUP.md 참고.
 */
private class FirebaseCloudService(private val context: Context) : CloudService {
    private val TAG = "CloudLogin"

    override val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private fun col(uid: String, name: String): CollectionReference = db.collection("users").document(uid).collection(name)

    override fun currentUser(): AuthUser? =
        if (!isAvailable) null else auth.currentUser?.let { AuthUser(it.uid, it.email, it.displayName) }

    /**
     * "Google로 로그인" 표준 창을 띄운다. 기기에 계정이 없으면 그 창에서 계정을 추가할 수 있다.
     * (GetGoogleIdOption 은 기기에 이미 쓴 적 있는 계정만 찾아서 "No credentials available" 이 자주 났다.)
     */
    override suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> {
        return try {
            check(isAvailable) { "Firebase 설정이 없습니다" }
            val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            check(resId != 0) { "default_web_client_id 가 없습니다. SHA-1 등록과 Google 로그인 설정을 끝낸 뒤 google-services.json 을 다시 받아 교체하세요" }
            val option = GetSignInWithGoogleOption.Builder(activity.getString(resId)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "지원하지 않는 로그인 방식입니다" }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
                ?: error("로그인 결과가 비어 있습니다")
            Result.success(AuthUser(user.uid, user.email, user.displayName))
        } catch (e: NoCredentialException) {
            Log.e(TAG, "계정을 찾지 못함", e)
            Result.failure(
                IllegalStateException(
                    "사용할 수 있는 Google 계정을 찾지 못했습니다. ① 기기(에뮬레이터) 설정 > 계정에 Google 계정이 있는지, " +
                        "② Play 스토어가 포함된 기기인지, ③ google-services.json 이 SHA-1 등록·Google 로그인 설정 뒤에 받은 파일인지 확인하세요.",
                ),
            )
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "사용자가 취소", e)
            Result.failure(IllegalStateException("로그인을 취소했습니다"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 어떤 오류인지 화면과 Logcat(태그 CloudLogin)에 그대로 남긴다
            Log.e(TAG, "로그인 실패", e)
            val detail = if (e is GetCredentialException) "${e.javaClass.simpleName} [${e.type}] ${e.errorMessage ?: ""}" else "${e.javaClass.simpleName}: ${e.message ?: ""}"
            Result.failure(IllegalStateException(detail.trim()))
        }
    }

    override fun signOut() { auth.signOut() }

    override suspend fun fetchFavorites(uid: String): List<SavedItem> =
        col(uid, "favorites").get().await().documents.mapNotNull { d ->
            d.getString("json")?.let { runCatching { Codec.jsonToFavorite(JSONObject(it)) }.getOrNull() }
        }

    override suspend fun fetchHistory(uid: String): List<HistoryItem> =
        col(uid, "history").get().await().documents.mapNotNull { d ->
            d.getString("json")?.let { runCatching { Codec.jsonToHistory(JSONObject(it)) }.getOrNull() }
        }

    // 쓰기는 기다리지 않는다: 오프라인이면 Firestore 가 기기에 쌓아 두었다가 인터넷이 되면 올린다.
    override fun upsertFavorites(uid: String, items: List<SavedItem>) = upsert(col(uid, "favorites"), items.map { it.id to Codec.favoriteToJson(it).toString() })
    override fun deleteFavorites(uid: String, ids: List<String>) = delete(col(uid, "favorites"), ids)
    override fun upsertHistory(uid: String, items: List<HistoryItem>) = upsert(col(uid, "history"), items.map { it.id to Codec.historyToJson(it).toString() })
    override fun deleteHistory(uid: String, ids: List<String>) = delete(col(uid, "history"), ids)

    private fun upsert(c: CollectionReference, items: List<Pair<String, String>>) {
        items.chunked(400).forEach { part ->
            val batch = db.batch()
            part.forEach { (id, json) -> batch.set(c.document(id), mapOf("json" to json)) }
            batch.commit()
        }
    }

    private fun delete(c: CollectionReference, ids: List<String>) {
        ids.chunked(400).forEach { part ->
            val batch = db.batch()
            part.forEach { batch.delete(c.document(it)) }
            batch.commit()
        }
    }
}
