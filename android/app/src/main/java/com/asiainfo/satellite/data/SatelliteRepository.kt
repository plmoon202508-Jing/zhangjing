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
                
                val tleData = celesTrakService.getActiveTLEs()
                val satellites = parseSatellites(tleData)
                cachedSatellites = satellites
                
                Result.success(satellites)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 解析TLE数据并过滤目标星座
     */
    private fun parseSatellites(tleData: List<TLEData>): List<Satellite> {
        return tleData.mapIndexed { index, tle ->
            val constellation = identifyConstellation(tle.name)
            Satellite(
                id = index,
                name = tle.name,
                constellation = constellation,
                tle = tle
            )
        }.filter { it.constellation != SatelliteConstellation.GW || 
                     it.name.startsWith("GUOWANG") || 
                     it.name.startsWith("HULIANWANG") }
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
