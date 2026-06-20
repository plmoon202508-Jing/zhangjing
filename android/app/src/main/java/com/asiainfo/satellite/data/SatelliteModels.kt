package com.asiainfo.satellite.data

/**
 * TLE (Two-Line Element) 数据模型
 */
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
