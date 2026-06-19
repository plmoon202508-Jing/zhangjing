package com.asiainfo.satellite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asiainfo.satellite.ui.navigation.AppNavGraph
import com.asiainfo.satellite.ui.theme.AsiaInfoSatelliteTheme
import com.asiainfo.satellite.ui.theme.Bg0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    AsiaInfoSatelliteTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg0) {
            AppNavGraph()
        }
    }
}
