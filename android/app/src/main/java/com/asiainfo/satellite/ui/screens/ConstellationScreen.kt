package com.asiainfo.satellite.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.data.CustomNameStore
import com.asiainfo.satellite.data.SatSubPoint
import com.asiainfo.satellite.data.Satellite
import com.asiainfo.satellite.data.SatelliteConstellation
import com.asiainfo.satellite.data.SatelliteRepository
import com.asiainfo.satellite.data.ContourData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val CONSTELLATION_ORDER = listOf(
    null,                          // 全部
    SatelliteConstellation.G60,
    SatelliteConstellation.GW,
    SatelliteConstellation.TQ
)

private fun constColor(c: SatelliteConstellation?): Color = when (c) {
    SatelliteConstellation.GW -> Color(0xFF2DE2FF)
    SatelliteConstellation.G60 -> Color(0xFFFF4ECD)
    SatelliteConstellation.TQ -> Color(0xFF4DFFB8)
    null -> Color(0xFF9FE8FF)
}

private const val RE_KM = 6371.0

/** 地球渲染模式 */
enum class GlobeRenderMode {
    GRID,       // 网格模式（当前默认）
    DOT_MATRIX, // 大陆点阵模式
    REALISTIC   // 真实地球模式
}

/** 正交投影后的屏幕坐标与深度（depth>=0 表示朝向观察者的前半球） */
private class Proj(val x: Float, val y: Float, val depth: Float, val unitDepth: Float)

private fun project(
    latDeg: Double, lonDeg: Double, r: Double,
    spinDeg: Float, tiltDeg: Float,
    cx: Float, cy: Float, radius: Float
): Proj {
    val lat = Math.toRadians(latDeg)
    val lon = Math.toRadians(lonDeg + spinDeg)
    val cl = cos(lat)
    val x = cl * sin(lon)
    val y = sin(lat)
    val z = cl * cos(lon)
    val t = Math.toRadians(tiltDeg.toDouble())
    val y2 = y * cos(t) - z * sin(t)
    val z2 = y * sin(t) + z * cos(t)
    val sx = cx + (radius * r * x).toFloat()
    val sy = cy - (radius * r * y2).toFloat()
    return Proj(sx, sy, (radius * r * z2).toFloat(), z2.toFloat())
}

/**
 * 星座全景：原生 2D 旋转地球 + 真实 SGP4 在轨卫星投影。
 * 可拖拽旋转/倾斜，自动自转；点击卫星查看详情并临时改名（有效期1天）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationScreen(
    onBack: () -> Unit,
    repository: SatelliteRepository = SatelliteRepository()
) {
    val context = LocalContext.current
    val nameStore = remember { CustomNameStore(context) }
    val scope = rememberCoroutineScope()

    var satellites by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var subPoints by remember { mutableStateOf<List<SatSubPoint>>(emptyList()) }
    var status by remember { mutableStateOf("正在加载星座数据…") }
    var filter by remember { mutableStateOf<SatelliteConstellation?>(null) }
    var selected by remember { mutableStateOf<SatSubPoint?>(null) }
    var renderMode by remember { mutableStateOf(GlobeRenderMode.GRID) }

    var spin by remember { mutableStateOf(0f) }
    var tilt by remember { mutableStateOf(-20f) }
    var autoSpin by remember { mutableStateOf(true) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        repository.getAllSatellites()
            .onSuccess { satellites = it; status = "" }
            .onFailure { e -> status = "加载失败：${e.message}" }
    }

    LaunchedEffect(satellites) {
        if (satellites.isEmpty()) return@LaunchedEffect
        while (true) {
            subPoints = repository.computeSubPoints(satellites)
            delay(1000)
        }
    }

    LaunchedEffect(autoSpin) {
        while (autoSpin) {
            withFrameNanos { }
            spin = (spin + 0.12f) % 360f
        }
    }

    val shown = remember(subPoints, filter) {
        subPoints.filter { filter == null || it.satellite.constellation == filter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("星座全景") },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", fontSize = 24.sp) } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1233), Color(0xFF03040A))))
        ) {
            // 地球 + 卫星
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                val cx = wPx / 2f
                val cy = hPx / 2f
                val radius = minOf(wPx, hPx) * 0.40f

                // 拖拽旋转
                val dragMod = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            autoSpin = false
                            resumeJob?.cancel()
                        },
                        onDragEnd = {
                            resumeJob = scope.launch { delay(2500); autoSpin = true }
                        }
                    ) { change, drag ->
                        change.consume()
                        spin = (spin + drag.x * 0.3f) % 360f
                        tilt = (tilt - drag.y * 0.3f).coerceIn(-85f, 85f)
                    }
                }
                // 点击选中卫星（spin/tilt 实时读取，不作为 key 以免每帧重建）
                val tapMod = Modifier.pointerInput(shown, wPx, hPx) {
                    detectTapGestures { tap ->
                        var best: SatSubPoint? = null
                        var bestD = Double.MAX_VALUE
                        shown.forEach { sp ->
                            val r = 1.0 + sp.altKm / RE_KM
                            val p = project(sp.latDeg, sp.lonDeg, r, spin, tilt, cx, cy, radius)
                            if (isVisible(p)) {
                                val d = hypot((tap.x - p.x).toDouble(), (tap.y - p.y).toDouble())
                                if (d < bestD) { bestD = d; best = sp }
                            }
                        }
                        if (best != null && bestD < 70.0) selected = best
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragMod)
                        .then(tapMod)
                ) {
                    drawGlobe(cx, cy, radius, spin, tilt, renderMode)
                    val selNorad = selected?.satellite?.tle?.noradId
                    shown.forEach { sp ->
                        val r = 1.0 + sp.altKm / RE_KM
                        val p = project(sp.latDeg, sp.lonDeg, r, spin, tilt, cx, cy, radius)
                        if (!isVisible(p)) return@forEach
                        val color = constColor(sp.satellite.constellation)
                        val isSel = sp.satellite.tle.noradId == selNorad
                        
                        // 绘制卫星轨道线（仅选中卫星）
                        if (isSel) {
                            drawOrbit(sp, spin, tilt, cx, cy, radius, color)
                        }
                        
                        drawSatDot(Offset(p.x, p.y), color, isSel)
                        val custom = nameStore.get(sp.satellite.tle.noradId)
                        if (isSel || custom != null) {
                            drawLabel(
                                custom ?: sp.satellite.name,
                                p.x + 18f, p.y - 8f, color.toArgb()
                            )
                        }
                    }
                }
            }

            // 顶部筛选 chips
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CONSTELLATION_ORDER.forEach { c ->
                    FilterChipItem(
                        label = c?.displayName ?: "全部",
                        color = constColor(c),
                        active = filter == c,
                        onClick = { filter = c; selected = null }
                    )
                }
            }

            // 地球渲染模式切换
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlobeModeChip(
                    label = "网格",
                    active = renderMode == GlobeRenderMode.GRID,
                    onClick = { renderMode = GlobeRenderMode.GRID }
                )
                GlobeModeChip(
                    label = "点阵",
                    active = renderMode == GlobeRenderMode.DOT_MATRIX,
                    onClick = { renderMode = GlobeRenderMode.DOT_MATRIX }
                )
                GlobeModeChip(
                    label = "真实",
                    active = renderMode == GlobeRenderMode.REALISTIC,
                    onClick = { renderMode = GlobeRenderMode.REALISTIC }
                )
            }

            // 在轨数量
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070A18).copy(alpha = 0.82f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (filter?.displayName ?: "全部星座") + " · 在轨",
                        color = Color(0xFF8AA0C0), fontSize = 13.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${shown.size}",
                        color = constColor(filter), fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("颗", color = Color(0xFF8AA0C0), fontSize = 13.sp)
                }
            }

            // 加载状态
            if (status.isNotEmpty()) {
                Card(
                    modifier = Modifier.align(Alignment.Center),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070A18).copy(alpha = 0.9f))
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!status.startsWith("加载失败")) {
                            CircularProgressIndicator(
                                color = Color(0xFF2DE2FF),
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(status, color = Color(0xFFEAF6FF), fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // 详情 + 改名
    selected?.let { sp ->
        SatRenameSheet(
            sp = sp,
            currentCustom = nameStore.get(sp.satellite.tle.noradId),
            onDismiss = { selected = null },
            onSave = { newName ->
                nameStore.set(sp.satellite.tle.noradId, newName)
                selected = null
            }
        )
    }
}

private fun isVisible(p: Proj): Boolean {
    // 仅显示朝向观察者的前半球卫星（后半球被地球遮挡）
    return p.unitDepth >= 0f
}

private fun DrawScope.drawGlobe(cx: Float, cy: Float, radius: Float, spin: Float, tilt: Float, mode: GlobeRenderMode) {
    // 大气辉光（所有模式通用）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x332DE2FF), Color(0x00000000)),
            center = Offset(cx, cy), radius = radius * 1.35f
        ),
        radius = radius * 1.35f, center = Offset(cx, cy)
    )

    when (mode) {
        GlobeRenderMode.GRID -> {
            // 球体（带光照感的径向渐变）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1B3C68), Color(0xFF0A1B33), Color(0xFF050D1C)),
                    center = Offset(cx - radius * 0.3f, cy - radius * 0.3f),
                    radius = radius * 1.4f
                ),
                radius = radius, center = Offset(cx, cy)
            )
            // 经纬网格
            val grat = Color(0xFF2DE2FF).copy(alpha = 0.20f)
            // 纬线
            var latLine = -60
            while (latLine <= 60) {
                var prev: Proj? = null
                var lon = 0
                while (lon <= 360) {
                    val p = project(latLine.toDouble(), lon.toDouble(), 1.0, spin, tilt, cx, cy, radius)
                    if (prev != null && prev.unitDepth >= 0f && p.unitDepth >= 0f) {
                        drawLine(grat, Offset(prev.x, prev.y), Offset(p.x, p.y), strokeWidth = 1.4f)
                    }
                    prev = p
                    lon += 6
                }
                latLine += 30
            }
            // 经线
            var lonLine = 0
            while (lonLine < 360) {
                var prev: Proj? = null
                var lat = -90
                while (lat <= 90) {
                    val p = project(lat.toDouble(), lonLine.toDouble(), 1.0, spin, tilt, cx, cy, radius)
                    if (prev != null && prev.unitDepth >= 0f && p.unitDepth >= 0f) {
                        drawLine(grat, Offset(prev.x, prev.y), Offset(p.x, p.y), strokeWidth = 1.4f)
                    }
                    prev = p
                    lat += 6
                }
                lonLine += 30
            }
        }
        GlobeRenderMode.DOT_MATRIX -> {
            // 深色球体背景
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0A1B33), Color(0xFF050D1C)),
                    center = Offset(cx - radius * 0.3f, cy - radius * 0.3f),
                    radius = radius * 1.4f
                ),
                radius = radius, center = Offset(cx, cy)
            )
            // 绘制大陆点阵
            val dotColor = Color(0xFF2DE2FF).copy(alpha = 0.6f)
            ContourData.getAllContours().forEach { contour ->
                for (i in contour.indices step 2) {
                    if (i + 1 < contour.size) {
                        val lon = contour[i]
                        val lat = contour[i + 1]
                        val p = project(lat, lon, 1.0, spin, tilt, cx, cy, radius)
                        if (p.unitDepth >= 0f) {
                            drawCircle(dotColor, radius = 2.5f, center = Offset(p.x, p.y))
                        }
                    }
                }
            }
        }
        GlobeRenderMode.REALISTIC -> {
            // 真实地球模式（简化版，使用渐变模拟海洋和陆地）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E4D7C),  // 海洋亮部
                        Color(0xFF0A2B4A),  // 海洋中部
                        Color(0xFF051826)   // 海洋暗部
                    ),
                    center = Offset(cx - radius * 0.3f, cy - radius * 0.3f),
                    radius = radius * 1.4f
                ),
                radius = radius, center = Offset(cx, cy)
            )
            // 简化的陆地轮廓（使用点阵但更密集）
            val landColor = Color(0xFF3A7A5C).copy(alpha = 0.7f)
            ContourData.getAllContours().forEach { contour ->
                for (i in contour.indices step 2) {
                    if (i + 1 < contour.size) {
                        val lon = contour[i]
                        val lat = contour[i + 1]
                        val p = project(lat, lon, 1.0, spin, tilt, cx, cy, radius)
                        if (p.unitDepth >= 0f) {
                            // 绘制更大的点模拟陆地
                            drawCircle(landColor, radius = 4.0f, center = Offset(p.x, p.y))
                        }
                    }
                }
            }
        }
    }

    // 轮廓（所有模式通用）
    drawCircle(
        color = Color(0xFF2DE2FF).copy(alpha = 0.5f),
        radius = radius, center = Offset(cx, cy), style = Stroke(width = 1.6f)
    )
}

private fun DrawScope.drawSatDot(c: Offset, color: Color, selected: Boolean) {
    val s = if (selected) 1.6f else 1f
    drawCircle(color.copy(alpha = 0.25f), radius = 11f * s, center = c)
    drawCircle(color, radius = 4.5f * s, center = c)
    drawCircle(Color.White, radius = 1.8f * s, center = c)
    if (selected) {
        drawCircle(color, radius = 16f, center = c, style = Stroke(width = 2f))
    }
}

private fun DrawScope.drawOrbit(sp: SatSubPoint, spin: Float, tilt: Float, cx: Float, cy: Float, radius: Float, color: Color) {
    // 简化的轨道绘制：基于当前卫星位置绘制一个椭圆轨道
    // 这里使用简化的圆形轨道近似
    val orbitColor = color.copy(alpha = 0.3f)
    val r = 1.0 + sp.altKm / RE_KM
    
    // 绘制轨道点（每隔10度一个点）
    var angle = 0
    while (angle < 360) {
        val orbitLat = sp.latDeg + 10 * kotlin.math.cos(Math.toRadians(angle.toDouble()))
        val orbitLon = sp.lonDeg + 10 * kotlin.math.sin(Math.toRadians(angle.toDouble()))
        
        val p = project(orbitLat, orbitLon, r, spin, tilt, cx, cy, radius)
        if (p.unitDepth >= 0f) {
            drawCircle(orbitColor, radius = 1.5f, center = Offset(p.x, p.y))
        }
        angle += 10
    }
}

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, argb: Int) {
    drawIntoCanvas {
        val paint = android.graphics.Paint().apply {
            color = argb
            textSize = 28f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
        it.nativeCanvas.drawText(text, x, y, paint)
    }
}

@Composable
private fun FilterChipItem(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (active) color else Color(0xFF070A18).copy(alpha = 0.8f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFF03040A) else Color(0xFFEAF6FF),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun GlobeModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (active) Color(0xFF2DE2FF) else Color(0xFF070A18).copy(alpha = 0.8f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFF03040A) else Color(0xFFEAF6FF),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatRenameSheet(
    sp: SatSubPoint,
    currentCustom: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val accent = constColor(sp.satellite.constellation)
    var text by remember { mutableStateOf(TextFieldValue(currentCustom ?: "")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070A18),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF2DE2FF)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                currentCustom ?: sp.satellite.name,
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(sp.satellite.constellation.displayName, color = accent, fontSize = 14.sp)

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KV("纬度", "${"%.2f".format(sp.latDeg)}°")
                KV("经度", "${"%.2f".format(sp.lonDeg)}°")
                KV("高度", "${sp.altKm.toInt()} km")
            }

            Spacer(Modifier.height(20.dp))
            Text("自定义名称（有效期 1 天）", color = Color(0xFF8AA0C0), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.text.isEmpty()) {
                    Text("给这颗卫星起个名字…", color = Color(0xFF54657F), fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onSave(null) },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复原名", color = Color(0xFF8AA0C0)) }
                Button(
                    onClick = { onSave(text.text.trim().ifBlank { null }) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("保存", color = Color(0xFF03040A), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun KV(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF8AA0C0), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color(0xFFEAF6FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
