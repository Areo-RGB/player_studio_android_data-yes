package com.playwe.playlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.playwe.playlist.ui.PlayerMainScreen
import com.playwe.playlist.ui.theme.PlaylistPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlaylistPlayerTheme {
                PlayerMainScreen()
            }
        }
    }
}
