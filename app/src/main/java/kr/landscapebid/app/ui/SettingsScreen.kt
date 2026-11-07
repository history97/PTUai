package kr.landscapebid.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.landscapebid.app.CalcViewModel
import kr.landscapebid.engine.Dataset

/** 설정: 화면 틀만 만들어 둔 상태. 항목은 앞으로 정해지는 대로 여기에 추가한다. */
@Composable
fun SettingsScreen(vm: CalcViewModel, ds: Dataset) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp)) {
        AccountCard(vm)
        Spacer(Modifier.height(12.dp))
        Panel {
            T("설정", 19f, bold = true)
            Spacer(Modifier.height(8.dp))
            T("설정 항목은 준비 중입니다. 필요한 항목이 정해지면 이 화면에 추가됩니다.", 13.5f, P.muted)
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            T("앱 정보", 15f, bold = true)
            Spacer(Modifier.height(4.dp))
            KvList(listOf("버전" to version, "낙찰 데이터" to "${ds.rows.size}건", "즐겨찾기" to "${vm.favorites.size}건", "검색 이력" to "${vm.history.size}건"))
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** 로그인 상태, 로그인/로그아웃, 지금 동기화 */
@Composable
private fun AccountCard(vm: CalcViewModel) {
    val activity = LocalContext.current.findActivity()
    Panel {
        T("계정", 15f, bold = true)
        Spacer(Modifier.height(6.dp))
        val u = vm.user
        when {
            !vm.cloudAvailable -> {
                T("로그인 기능은 준비 중입니다. 지금은 즐겨찾기와 검색 이력이 이 기기 안에만 저장돼요.", 13.5f, P.muted)
            }
            u == null -> {
                T("Google 계정으로 로그인하면 즐겨찾기와 검색 이력이 계정에 저장되어, 앱을 지웠다 깔거나 다른 기기에서도 이어서 볼 수 있어요.", 13.5f, P.muted)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Google 계정으로 로그인", { if (activity != null) vm.signIn(activity) }, Modifier.fillMaxWidth(), enabled = activity != null)
            }
            else -> {
                T(u.name ?: "로그인됨", 15f, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (u.email != null) T(u.email, 13f, P.muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(if (vm.syncing) "동기화 중..." else "지금 동기화", { vm.syncNow() }, ghost = true)
                    AppButton("로그아웃", { vm.signOut() }, ghost = true)
                }
            }
        }
        vm.authMsg?.let { Spacer(Modifier.height(8.dp)); T(it, 12.5f, P.muted) }
    }
}
