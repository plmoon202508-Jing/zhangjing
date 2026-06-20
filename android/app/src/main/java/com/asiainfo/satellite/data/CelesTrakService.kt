package com.asiainfo.satellite.data

import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * CelesTrak TLE数据服务
 * 数据来源: https://celestrak.org/
 */
interface CelesTrakService {
    
    @GET("gp.php?GROUP=active&FORMAT=tle")
    suspend fun getActiveTLEs(): List<TLEData>
    
    companion object {
        private const val BASE_URL = "https://celestrak.org/NORAD/elements/"
        
        fun create(): CelesTrakService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CelesTrakService::class.java)
        }
    }
}

/**
 * TLE (Two-Line Element) 数据模型
 */
@Serializable
data class TLEData(
    val name: String,
    val line1: String,
    val line2: String
)

/**
 * 卫星星座枚举
 */
enum class SatelliteConstellation(val displayName: String, val namePrefix: String) {
    GW("星网 GW", "GUOWANG"),
    G60("G60 千帆", "QIANFAN"),
    TQ("天启", "TIANQI")
}

/**
 * 卫星数据模型
 */
data class Satellite(
    val id: Int,
    val name: String,
    val constellation: SatelliteConstellation,
    val tle: TLEData
)
