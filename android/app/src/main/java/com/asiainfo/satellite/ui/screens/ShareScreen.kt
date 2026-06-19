package com.asiainfo.satellite.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.ui.theme.Bg0
import com.asiainfo.satellite.ui.theme.Cyan
import com.asiainfo.satellite.ui.theme.GlassBorder
import com.asiainfo.satellite.ui.theme.TextDim
import com.asiainfo.satellite.ui.theme.TextMain
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.random.Random

@Composable
fun ShareScreen(onBack: () -> Unit, onScan: () -> Unit) {
    val inviteCode = remember { "AISM-" + (100000 + Random.nextInt(900000)) }
    val payload = remember { "{\"app\":\"AsiaInfo Satellite Moment\",\"action\":\"invite\",\"code\":\"$inviteCode\"}" }
    val qr = remember { generateQr(payload, 220) }

    HoloScaffold("分享体验", onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x8C121C36), RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(vertical = 30.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(220.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qr?.let { Image(it.asImageBitmap(), "二维码", Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.height(22.dp))
                Text("扫码体验亚信卫星时刻", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("扫描后需先登记体验者信息", color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text("邀请码: $inviteCode", color = Cyan, fontSize = 13.sp, letterSpacing = 2.sp)
            }

            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0x8C121C36), RoundedCornerShape(18.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onScan),
                contentAlignment = Alignment.Center
            ) {
                Text("模拟扫码进入登记  →", color = TextMain, fontSize = 16.sp)
            }
        }
    }
}

/** ZXing 生成真实可扫描二维码 */
private fun generateQr(text: String, sizePx: Int): Bitmap? = try {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) for (y in 0 until sizePx) {
        bmp.setPixel(x, y, if (matrix[x, y]) 0xFF06122A.toInt() else 0xFFFFFFFF.toInt())
    }
    bmp
} catch (e: Exception) {
    null
}
