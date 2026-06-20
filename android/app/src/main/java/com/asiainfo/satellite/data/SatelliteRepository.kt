package com.asiainfo.satellite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 卫星数据仓库
 * 负责从CelesTrak获取数据并进行过滤和缓存
 */
class SatelliteRepository {
    
    private val celesTrakService = CelesTrakService.create()
    private var cachedSatellites: List<Satellite>? = null
    
    /**
     * 获取所有卫星数据
     */
    suspend fun getAllSatellites(forceRefresh: Boolean = false): Result<List<Satellite>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && cachedSatellites != null) {
                    return@withContext Result.success(cachedSatellites!!)
                }
                
                val tleStrings = celesTrakService.getActiveTLEs()
                val satellites = parseTLEStrings(tleStrings)
                cachedSatellites = satellites
                
                Result.success(satellites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 解析TLE字符串并过滤目标星座
     */
    private fun parseTLEStrings(tleStrings: List<String>): List<Satellite> {
        val satellites = mutableListOf<Satellite>()
        var id = 0
        
        // TLE格式：每3行一组（名称 + line1 + line2）
        for (i in tleStrings.indices step 3) {
            if (i + 2 < tleStrings.size) {
                val name = tleStrings[i].trim()
                val line1 = tleStrings[i + 1].trim()
                val line2 = tleStrings[i + 2].trim()
                
                val constellation = identifyConstellation(name)
                val tleData = TLEData(name, line1, line2)
                
                // 只添加目标星座的卫星
                if (constellation == SatelliteConstellation.GW && 
                    (name.startsWith("GUOWANG") || name.startsWith("HULIANWANG"))) {
                    satellites.add(Satellite(id++, name, constellation, tleData))
                } else if (constellation != SatelliteConstellation.GW) {
                    satellites.add(Satellite(id++, name, constellation, tleData))
                }
            }
        }
        
        return satellites
    }
    
    /**
     * 识别卫星所属星座
     */
    private fun identifyConstellation(name: String): SatelliteConstellation {
        return when {
            name.startsWith("QIANFAN") -> SatelliteConstellation.G60
            name.startsWith("TIANQI") && !name.startsWith("TIANQIN") -> SatelliteConstellation.TQ
            name.startsWith("GUOWANG") || name.startsWith("HULIANWANG") -> SatelliteConstellation.GW
            else -> SatelliteConstellation.GW // 默认归类为GW
        }
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
