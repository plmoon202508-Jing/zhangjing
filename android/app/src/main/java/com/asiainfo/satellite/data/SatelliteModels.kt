package com.asiainfo.satellite.data

/**
 * TLE (Two-Line Element) 原始两行根数
 */
data class TLEData(
    val name: String,
    val line1: String,
    val line2: String
) {
    /** NORAD 编号（来自 line1 第 3-7 位） */
    val noradId: String
        get() = if (line1.length >= 7) line1.substring(2, 7).trim() else name
}

/**
 * 卫星星座
 * @param celestrakNames CelesTrak NAME 查询关键字（可多个，合并去重）
 */
enum class SatelliteConstellation(
    val displayName: String,
    val celestrakNames: List<String>
) {
    GW("星网 GW", listOf("GUOWANG", "HULIANWANG")),
    G60("G60 千帆", listOf("QIANFAN")),
    TQ("天启", listOf("TIANQI"))
}

/**
 * 卫星数据模型（含 SGP4 轨道传播器）
 */
data class Satellite(
    val id: Int,
    val name: String,
    val constellation: SatelliteConstellation,
    val tle: TLEData,
    val propagator: com.github.amsacode.predict4java.Satellite
)

/**
 * 卫星星下点（地理经纬度 + 高度），用于在 2D 旋转地球上投影显示。
 */
data class SatSubPoint(
    val satellite: Satellite,
    val latDeg: Double,   // 纬度 -90~90
    val lonDeg: Double,   // 经度 -180~180
    val altKm: Double     // 轨道高度 km
)

/**
 * 卫星相对观测者的瞬时方位信息
 */
data class SatelliteLook(
    val satellite: Satellite,
    val azimuthDeg: Double,   // 方位角 0-360（正北顺时针）
    val elevationDeg: Double, // 俯仰角 -90~90（地平线以上为正）
    val rangeKm: Double,      // 斜距 km
    val altitudeKm: Double    // 卫星高度 km
) {
    val isVisible: Boolean get() = elevationDeg > 0
}
