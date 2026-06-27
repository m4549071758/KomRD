package dev.komrd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.navigation.KomrdApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 本プロジェクトは activity-compose 1.12.4 を使用するためこの順序で問題ない。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KomrdTheme {
                KomrdApp()
            }
        }
    }
}
