package com.asiainfo.satellite.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.asiainfo.satellite.data.SatelliteConstellation
import com.asiainfo.satellite.data.SatelliteLook
import com.asiainfo.satellite.data.SatelliteRepository
import com.asiainfo.satellite.location.ObserverLocation
import com.asiainfo.satellite.location.fetchObserverLocation
import com.asiainfo.satellite.sensor.rememberDeviceOrientation
import com.asiainfo.satellite.ui.components.CameraPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 相机视场角（度），用于把卫星方位投影到屏幕
private const val H_FOV = 60.0
private const val V_FOV = 75.0

/**
 * AR 卫星：相机实景 + 陀螺仪朝向 + GPS 定位 + SGP4 实时方位叠加
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARScreen(
    onBack: () -> Unit,
    repository: SatelliteRepository = SatelliteRepository()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var observer by remember { mutableStateOf<ObserverLocation?>(null) }
    var looks by remember { mutableStateOf<List<SatelliteLook>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("正在初始化…") }

    val orientation by rememberDeviceOrientation()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocation
    }

    // 首次进入请求权限
    LaunchedEffect(Unit) {
        val need = mutableListOf<String>()
        if (!hasCamera) need += Manifest.permission.CAMERA
        if (!hasLocation) need += Manifest.permission.ACCESS_FINE_LOCATION
        if (need.isNotEmpty()) permissionLauncher.launch(need.toTypedArray())
    }

    // 获取定位
    LaunchedEffect(hasLocation) {
        if (hasLocation) {
            status = "正在定位…"
            observer = fetchObserverLocation(context)
            if (observer == null) {
                // 定位失败时使用默认位置（北京）
                observer = ObserverLocation(39.9042, 116.4074, 50.0)
                status = "定位失败，使用默认位置（北京）"
            }
        }
    }

    // 加载 TLE 并周期性计算方位
    LaunchedEffect(observer) {
        val obs = observer ?: return@LaunchedEffect
        status = "正在加载卫星 TLE…"
        val result = repository.getAllSatellites()
        result.onSuccess { sats ->
            totalCount = sats.size
            status = ""
            while (true) {
                looks = repository.computeLooks(
                    sats, obs.latitude, obs.longitude, obs.altitude
                ).sortedByDescending { it.elevationDeg }
                delay(1000)
            }
        }.onFailure { e ->
            status = "加载失败：${e.message}"
        }
    }

    val visibleLooks = remember(looks) { looks.filter { it.isVisible } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR 卫星") },
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
                .background(Color.Black)
        ) {
            if (hasCamera) {
                CameraPreview(modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("需要相机权限以开启 AR 实景", color = Color(0xFF8AA0C0))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }) { Text("授予权限") }
                    }
                }
            }

            // 卫星叠加层
            SatelliteSky(
                looks = visibleLooks,
                deviceAzimuth = orientation.azimuth.toDouble(),
                devicePitch = orientation.pitch.toDouble(),
                modifier = Modifier.fillMaxSize()
            )

            // 准星
            Box(Modifier.align(Alignment.Center)) {
                Text("+", color = Color(0x88FFFFFF), fontSize = 28.sp)
            }

            // 指南针
            ARCompass(
                azimuth = orientation.azimuth,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )

            // 状态提示
            if (status.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF070A18).copy(alpha = 0.85f)
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2DE2FF),
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(status, color = Color(0xFFEAF6FF), fontSize = 13.sp)
                    }
                }
            }

            // 底部信息
            ARInfo(
                azimuth = orientation.azimuth,
                elevation = orientation.pitch,
                visibleCount = visibleLooks.size,
                totalCount = totalCount,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * 把可见卫星按方位/俯仰投影到屏幕
 */
@Composable
private fun SatelliteSky(
    looks: List<SatelliteLook>,
    deviceAzimuth: Double,
    devicePitch: Double,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = Color(0xFFEAF6FF).toArgb()
            textSize = 30f
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        looks.forEach { look ->
            val dAz = angleDiff(look.azimuthDeg, deviceAzimuth)
            val dEl = look.elevationDeg - devicePitch

            if (kotlin.math.abs(dAz) <= H_FOV / 2 && kotlin.math.abs(dEl) <= V_FOV / 2) {
                val x = cx + (dAz / (H_FOV / 2)).toFloat() * (w / 2f)
                val y = cy - (dEl / (V_FOV / 2)).toFloat() * (h / 2f)

                val color = when (look.satellite.constellation) {
                    SatelliteConstellation.GW -> Color(0xFF2DE2FF)
                    SatelliteConstellation.G60 -> Color(0xFFFF4ECD)
                    SatelliteConstellation.TQ -> Color(0xFF4DFFB8)
                }

                drawCircle(color = color.copy(alpha = 0.25f), radius = 26f, center = Offset(x, y))
                drawCircle(color = color, radius = 10f, center = Offset(x, y))
                drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))

                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        look.satellite.name,
                        x + 30f,
                        y + 10f,
                        labelPaint
                    )
                }
            }
        }
    }
}

@Composable
private fun ARCompass(azimuth: Float, modifier: Modifier = Modifier) {
    val dir = azimuthToDirection(azimuth)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF070A18).copy(alpha = 0.8f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dir, color = Color(0xFF2DE2FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${azimuth.toInt()}°", color = Color(0xFFEAF6FF), fontSize = 14.sp)
        }
    }
}

@Composable
private fun ARInfo(
    azimuth: Float,
    elevation: Float,
    visibleCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF070A18).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            InfoCol("方位角", "${azimuth.toInt()}°", Color(0xFF2DE2FF))
            InfoCol("俯仰角", "${elevation.toInt()}°", Color(0xFF4DFFB8))
            InfoCol("视野内", "$visibleCount", Color(0xFFFF4ECD))
            InfoCol("地平线上", "$totalCount", Color(0xFFEAF6FF))
        }
    }
}

@Composable
private fun InfoCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF8AA0C0), fontSize = 12.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** 两个方位角之间的最短夹角（-180~180） */
private fun angleDiff(a: Double, b: Double): Double {
    var d = a - b
    while (d > 180) d -= 360
    while (d < -180) d += 360
    return d
}

private fun azimuthToDirection(az: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((az + 22.5f) % 360f) / 45f).toInt()
    return dirs[idx.coerceIn(0, 7)]
}
