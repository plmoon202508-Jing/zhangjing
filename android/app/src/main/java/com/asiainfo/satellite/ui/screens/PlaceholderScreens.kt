package com.asiainfo.satellite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.ui.components.Starfield
import com.asiainfo.satellite.ui.theme.Bg0
import com.asiainfo.satellite.ui.theme.Bg1
import com.asiainfo.satellite.ui.theme.Cyan
import com.asiainfo.satellite.ui.theme.GlassBorder
import com.asiainfo.satellite.ui.theme.TextDim
import com.asiainfo.satellite.ui.theme.TextMain
import com.asiainfo.satellite.ui.theme.Violet

/** 通用全息脚手架：星空背景 + 顶栏 */
@Composable
fun HoloScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg1, Bg0)))
    ) {
        Starfield()
        Column(Modifier.fillMaxSize()) {
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 8.dp, bottom = 8.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconBtn(onClick = onBack)
                Text(title, color = TextMain, fontSize = 15.sp, letterSpacing = 2.sp)
                Spacer(Modifier.size(40.dp))
            }
            content(PaddingValues(0.dp))
        }
    }
}

@Composable
private fun IconBtn(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color(0x8C121C36), RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextMain, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ComingSoon(label: String, accent: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text("将在后续阶段实现", color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
fun ConstellationScreen(onBack: () -> Unit) =
    HoloScaffold("星座全景", onBack) { ComingSoon("3D 地球 + 轨道卫星", Cyan) }

@Composable
fun ARScreen(onBack: () -> Unit) =
    HoloScaffold("AR 卫星", onBack) { ComingSoon("相机 + 卫星方位叠加", Violet) }
