package com.asiainfo.satellite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 卫星数据仓库
 * 负责从CelesTrak获取数据并进行过滤和缓存
 */
class SatelliteRepository {
    
    private var cachedSatellites: List<Satellite>? = null
    
    /**
     * 获取所有卫星数据
     * 使用模拟数据避免网络请求问题
     */
    suspend fun getAllSatellites(forceRefresh: Boolean = false): Result<List<Satellite>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && cachedSatellites != null) {
                    return@withContext Result.success(cachedSatellites!!)
                }
                
                // 使用模拟数据
                val satellites = generateMockSatellites()
                cachedSatellites = satellites
                
                Result.success(satellites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 生成模拟卫星数据
     */
    private fun generateMockSatellites(): List<Satellite> {
        val satellites = mutableListOf<Satellite>()
        
        // 模拟GW卫星
        repeat(10) { index ->
            satellites.add(Satellite(
                id = index,
                name = "GUOWANG-${index + 1}",
                constellation = SatelliteConstellation.GW,
                tle = TLEData("GUOWANG-${index + 1}", "1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927", "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537")
            ))
        }
        
        // 模拟G60卫星
        repeat(10) { index ->
            satellites.add(Satellite(
                id = index + 10,
                name = "QIANFAN-${index + 1}",
                constellation = SatelliteConstellation.G60,
                tle = TLEData("QIANFAN-${index + 1}", "1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927", "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537")
            ))
        }
        
        // 模拟天启卫星
        repeat(5) { index ->
            satellites.add(Satellite(
                id = index + 20,
                name = "TIANQI-${index + 1}",
                constellation = SatelliteConstellation.TQ,
                tle = TLEData("TIANQI-${index + 1}", "1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927", "2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537")
            ))
        }
        
        return satellites
    }
    
    /**
     * 按星座过滤卫星
     */
    fun filterByConstellation(satellites: List<Satellite>, constellation: SatelliteConstellation?): List<Satellite> {
        return if (constellation == null) {
            satellites
        } else {
            satellites.filter { it.constellation == constellation }
        }
    }
}
