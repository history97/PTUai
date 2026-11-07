package kr.landscapebid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kr.landscapebid.app.ui.AppScaffold
import kr.landscapebid.app.ui.LandscapeTheme

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LandscapeTheme {
                AppScaffold(vm)
            }
        }
    }
}
