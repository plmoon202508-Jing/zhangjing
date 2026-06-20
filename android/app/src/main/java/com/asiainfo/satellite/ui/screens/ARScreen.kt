package com.asiainfo.satellite.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asiainfo.satellite.data.Satellite
import com.asiainfo.satellite.data.SatelliteConstellation
import com.asiainfo.satellite.data.SatelliteRepository
import kotlinx.coroutines.launch

/**
 * AR卫星屏幕
 * 显示相机叠加和卫星方位指示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARScreen(
    onBack: () -> Unit,
    repository: SatelliteRepository = SatelliteRepository()
) {
    var satellites by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var azimuth by remember { mutableStateOf(0f) } // 方位角
    var elevation by remember { mutableStateOf(0f) } // 俯仰角
    
    val scope = rememberCoroutineScope()
    
    // 加载卫星数据
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            val result = repository.getAllSatellites()
            result.onSuccess { data ->
                satellites = data
                isLoading = false
            }.onFailure {
                isLoading = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR 卫星") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 相机预览区域 (占位符)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF2DE2FF))
                } else {
                    Text(
                        "相机预览区域",
                        color = Color(0xFF8AA0C0),
                        fontSize = 14.sp
                    )
                }
            }
            
            // 方位角指示器
            ARCompass(
                azimuth = azimuth,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
            
            // 卫星叠加层
            SatelliteOverlay(
                satellites = satellites.take(10), // 显示前10个卫星
                modifier = Modifier.fillMaxSize()
            )
            
            // 底部信息
            ARInfo(
                azimuth = azimuth,
                elevation = elevation,
                satelliteCount = satellites.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * AR指南针
 */
@Composable
fun ARCompass(
    azimuth: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF070A18).copy(alpha = 0.8f)
        )
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "N",
                    color = Color(0xFF2DE2FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${azimuth.toInt()}°",
                    color = Color(0xFFEAF6FF),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 卫星叠加层
 */
@Composable
fun SatelliteOverlay(
    satellites: List<Satellite>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        satellites.forEachIndexed { index, satellite ->
            // 模拟卫星位置 (实际应用中应根据真实方位角计算)
            val offsetX = when (index % 3) {
                0 -> -100.dp
                1 -> 0.dp
                else -> 100.dp
            }
            val offsetY = when (index / 3) {
                0 -> -50.dp
                1 -> 50.dp
                else -> 150.dp
            }
            
            SatelliteMarker(
                satellite = satellite,
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .align(Alignment.Center)
            )
        }
    }
}

/**
 * 卫星标记
 */
@Composable
fun SatelliteMarker(
    satellite: Satellite,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                when (satellite.constellation) {
                    SatelliteConstellation.GW -> Color(0xFF2DE2FF)
                    SatelliteConstellation.G60 -> Color(0xFFFF4ECD)
                    SatelliteConstellation.TQ -> Color(0xFF4DFFB8)
                },
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.White, CircleShape)
        )
    }
}

/**
 * AR信息面板
 */
@Composable
fun ARInfo(
    azimuth: Float,
    elevation: Float,
    satelliteCount: Int,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "方位角",
                    color = Color(0xFF8AA0C0),
                    fontSize = 12.sp
                )
                Text(
                    "${azimuth.toInt()}°",
                    color = Color(0xFF2DE2FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "俯仰角",
                    color = Color(0xFF8AA0C0),
                    fontSize = 12.sp
                )
                Text(
                    "${elevation.toInt()}°",
                    color = Color(0xFF4DFFB8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "可见卫星",
                    color = Color(0xFF8AA0C0),
                    fontSize = 12.sp
                )
                Text(
                    "$satelliteCount",
                    color = Color(0xFFFF4ECD),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
