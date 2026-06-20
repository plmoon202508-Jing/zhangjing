package com.asiainfo.satellite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
 * 星座全景屏幕
 * 显示3D地球和卫星数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationScreen(
    onBack: () -> Unit,
    repository: SatelliteRepository = SatelliteRepository()
) {
    var satellites by remember { mutableStateOf<List<Satellite>>(emptyList()) }
    var selectedConstellation by remember { mutableStateOf<SatelliteConstellation?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // 加载卫星数据
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            val result = repository.getAllSatellites()
            result.onSuccess { data ->
                satellites = data
                isLoading = false
            }.onFailure { e ->
                error = e.message
                isLoading = false
            }
        }
    }
    
    // 过滤卫星
    val filteredSatellites = remember(selectedConstellation, satellites) {
        repository.filterByConstellation(satellites, selectedConstellation)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("星座全景") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF03040A))
        ) {
            // 卫星统计
            SatelliteStats(
                totalSatellites = satellites.size,
                filteredCount = filteredSatellites.size,
                selectedConstellation = selectedConstellation
            )
            
            // 星座筛选器
            ConstellationFilter(
                selectedConstellation = selectedConstellation,
                onConstellationSelected = { selectedConstellation = it }
            )
            
            // 3D地球区域 (占位符)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp)
                    .background(
                        Color(0xFF070A18),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "3D地球渲染区域",
                    color = Color(0xFF8AA0C0),
                    fontSize = 14.sp
                )
            }
            
            // 卫星列表
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2DE2FF))
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "加载失败: $error",
                        color = Color(0xFFFF4ECD),
                        fontSize = 14.sp
                    )
                }
            } else {
                SatelliteList(
                    satellites = filteredSatellites,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * 卫星统计信息
 */
@Composable
fun SatelliteStats(
    totalSatellites: Int,
    filteredCount: Int,
    selectedConstellation: SatelliteConstellation?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "在轨卫星",
                color = Color(0xFF8AA0C0),
                fontSize = 12.sp
            )
            Text(
                "$totalSatellites",
                color = Color(0xFF2DE2FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column {
            Text(
                "当前显示",
                color = Color(0xFF8AA0C0),
                fontSize = 12.sp
            )
            Text(
                "$filteredCount",
                color = Color(0xFF4DFFB8),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 星座筛选器
 */
@Composable
fun ConstellationFilter(
    selectedConstellation: SatelliteConstellation?,
    onConstellationSelected: (SatelliteConstellation?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedConstellation == null,
            onClick = { onConstellationSelected(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF2DE2FF),
                selectedLabelColor = Color(0xFF03040A)
            )
        )
        
        SatelliteConstellation.values().forEach { constellation ->
            FilterChip(
                selected = selectedConstellation == constellation,
                onClick = { onConstellationSelected(constellation) },
                label = { Text(constellation.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (constellation) {
                        SatelliteConstellation.GW -> Color(0xFF2DE2FF)
                        SatelliteConstellation.G60 -> Color(0xFFFF4ECD)
                        SatelliteConstellation.TQ -> Color(0xFF4DFFB8)
                    },
                    selectedLabelColor = Color(0xFF03040A)
                )
            )
        }
    }
}

/**
 * 卫星列表（可滚动）
 */
@Composable
fun SatelliteList(
    satellites: List<Satellite>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(satellites) { satellite ->
            SatelliteItem(satellite = satellite)
        }
    }
}

/**
 * 单个卫星项
 */
@Composable
fun SatelliteItem(satellite: Satellite) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF070A18)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    satellite.name,
                    color = Color(0xFFEAF6FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    satellite.constellation.displayName,
                    color = Color(0xFF8AA0C0),
                    fontSize = 12.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when (satellite.constellation) {
                            SatelliteConstellation.GW -> Color(0xFF2DE2FF)
                            SatelliteConstellation.G60 -> Color(0xFFFF4ECD)
                            SatelliteConstellation.TQ -> Color(0xFF4DFFB8)
                        },
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}
