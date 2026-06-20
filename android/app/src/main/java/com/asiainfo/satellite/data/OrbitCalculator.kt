package com.asiainfo.satellite.data

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 轨道计算器
 * 简化版的SGP4轨道传播算法
 * 用于计算卫星在指定时间的位置
 */
class OrbitCalculator {
    
    /**
     * 卫星位置信息
     */
    data class SatellitePosition(
        val latitude: Double,  // 纬度 (度)
        val longitude: Double, // 经度 (度)
        val altitude: Double,  // 高度 (km)
        val velocity: Double   // 速度 (km/s)
    )
    
    /**
     * 从TLE计算卫星位置
     * @param tle TLE数据
     * @param timestamp Unix时间戳 (秒)
     * @return 卫星位置
     */
    fun calculatePosition(tle: TLEData, timestamp: Long): SatellitePosition {
        // 简化版轨道计算
        // 实际应用中应使用完整的SGP4算法
        
        // 从TLE解析轨道参数 (简化版)
        val inclination = extractInclination(tle.line2) // 轨道倾角
        val raan = extractRAAN(tle.line2) // 升交点赤经
        val eccentricity = extractEccentricity(tle.line2) // 离心率
        val meanAnomaly = extractMeanAnomaly(tle.line2) // 平近点角
        val meanMotion = extractMeanMotion(tle.line1) // 平均运动
        
        // 计算当前时间的平近点角
        val currentTime = System.currentTimeMillis() / 1000.0
        val timeDiff = currentTime - timestamp
        val currentMeanAnomaly = (meanAnomaly + meanMotion * timeDiff) % (2 * Math.PI)
        
        // 简化位置计算 (假设圆形轨道)
        val radius = 6371.0 + 500.0 // 地球半径 + 平均高度
        val x = radius * cos(currentMeanAnomaly)
        val y = radius * sin(currentMeanAnomaly)
        val z = radius * sin(inclination) * sin(currentMeanAnomaly)
        
        // 转换为经纬度
        val latitude = Math.toDegrees(Math.asin(z / radius))
        val longitude = Math.toDegrees(Math.atan2(y, x))
        val altitude = radius - 6371.0
        val velocity = sqrt(398600.0 / radius) // 简化速度计算
        
        return SatellitePosition(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            velocity = velocity
        )
    }
    
    /**
     * 从TLE第二行提取轨道倾角
     */
    private fun extractInclination(line2: String): Double {
        return line2.substring(8, 16).trim().toDouble() * Math.PI / 180.0
    }
    
    /**
     * 从TLE第二行提取升交点赤经
     */
    private fun extractRAAN(line2: String): Double {
        return line2.substring(17, 25).trim().toDouble() * Math.PI / 180.0
    }
    
    /**
     * 从TLE第二行提取离心率
     */
    private fun extractEccentricity(line2: String): Double {
        return "0.${line2.substring(26, 33).trim()}".toDouble()
    }
    
    /**
     * 从TLE第二行提取平近点角
     */
    private fun extractMeanAnomaly(line2: String): Double {
        return line2.substring(43, 51).trim().toDouble() * Math.PI / 180.0
    }
    
    /**
     * 从TLE第一行提取平均运动
     */
    private fun extractMeanMotion(line1: String): Double {
        return line1.substring(52, 63).trim().toDouble() * 2 * Math.PI / 86400.0
    }
}
