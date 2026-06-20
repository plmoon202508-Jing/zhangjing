package com.asiainfo.satellite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.ui.components.WebEarth

/**
 * 星座全景：内嵌已开发的 Web 3D 地球
 * - 真实 TLE + SGP4 实时轨道
 * - 地球上的卫星可点击查看 / 改名（Web 内置详情面板）
 * - 内置星座筛选与在轨数量统计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("星座全景") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹", fontSize = 24.sp) }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF03040A))
        ) {
            WebEarth(
                modifier = Modifier.fillMaxSize(),
                hash = "constellation"
            )
        }
    }
}
