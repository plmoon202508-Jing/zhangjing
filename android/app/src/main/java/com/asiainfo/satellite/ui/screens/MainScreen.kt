package com.asiainfo.satellite.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.share.SatelliteShare
import com.asiainfo.satellite.share.CloudUploader
import kotlinx.coroutines.launch
import com.asiainfo.satellite.R
import com.asiainfo.satellite.ui.components.Starfield
import com.asiainfo.satellite.ui.theme.Bg0
import com.asiainfo.satellite.ui.theme.Bg1
import com.asiainfo.satellite.ui.theme.Cyan
import com.asiainfo.satellite.ui.theme.GlassBorder
import com.asiainfo.satellite.ui.theme.TextDim
import com.asiainfo.satellite.ui.theme.TextMain
import com.asiainfo.satellite.ui.theme.Violet

@Composable
fun MainScreen(
    onConstellation: () -> Unit,
    onAR: () -> Unit,
    onShare: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    
    val downloadQrBitmap = remember { 
        // 生成APP下载二维码
        SatelliteShare.qrBitmap(SatelliteShare.QR_URL, 200)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg1, Bg0)))
    ) {
        Starfield()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandLogo()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "亚信卫星时刻",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = TextMain
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ASIAINFO · SATELLITE MOMENT",
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                color = TextDim
            )

            Spacer(Modifier.height(54.dp))

            HoloButton(
                title = "星座全景",
                subtitle = "地球卫星星座可视化",
                icon = Icons.Default.Public,
                accent = Cyan,
                onClick = onConstellation
            )
            Spacer(Modifier.height(18.dp))
            HoloButton(
                title = "AR 卫星",
                subtitle = "实景增强卫星观测",
                icon = Icons.Default.CameraAlt,
                accent = Violet,
                onClick = onAR
            )

            Spacer(Modifier.height(28.dp))
            
            // APP下载二维码卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 点击上传APK到云端
                        if (!isUploading) {
                            isUploading = true
                            scope.launch {
                                try {
                                    // 获取当前APK文件
                                    val apkFile = java.io.File(context.packageCodePath)
                                    val apkBytes = apkFile.readBytes()
                                    
                                    // 上传到云端
                                    val result = CloudUploader.uploadApk(apkBytes)
                                    
                                    // 更新二维码指向云端下载页面
                                    val newQrBitmap = SatelliteShare.qrBitmap(result.page, 200)
                                    // 这里可以更新状态来刷新二维码显示
                                    
                                    isUploading = false
                                } catch (e: Exception) {
                                    isUploading = false
                                    // 处理上传失败
                                }
                            }
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x8C121C36)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, null, tint = TextDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (isUploading) "正在上传APK..." else "APP下载二维码", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(if (isUploading) "请稍候" else "扫码下载亚信卫星时刻", color = TextDim, fontSize = 11.sp)
                    }
                    
                    // 显示二维码
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Cyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Image(
                                bitmap = downloadQrBitmap.asImageBitmap(),
                                contentDescription = "APP下载二维码",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "v1.0.0 · 数据来源 CelesTrak",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
        )
    }
}

@Composable
private fun BrandLogo() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width = 200.dp, height = 96.dp)
    ) {
        // 背景辉光
        Box(
            Modifier
                .size(160.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.22f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Image(
            painter = painterResource(id = R.drawable.asiainfo_logo),
            contentDescription = "亚信 logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HoloButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color(0x8C121C36), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color(0x14FFFFFF), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Text("→", color = TextDim, fontSize = 20.sp)
    }
}
