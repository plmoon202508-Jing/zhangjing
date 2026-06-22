import Foundation

// 简化的SGP4算法实现
// 这是一个基础实现，实际应用中建议使用成熟的SGP4库

class SGP4 {
    
    // 地球半径（km）
    static let RE_KM = 6371.0
    
    // 从TLE计算卫星位置
    static func computePosition(tle: TLEData, at time: Date) -> (x: Double, y: Double, z: Double, lat: Double, lon: Double, alt: Double) {
        // 这是一个简化的实现
        // 实际SGP4算法需要复杂的数学计算
        
        // 提取轨道参数（简化版）
        let inclination = extractTLEParameter(line: tle.line2, start: 8, length: 8)
        let raan = extractTLEParameter(line: tle.line2, start: 17, length: 8)
        let eccentricity = extractTLEParameter(line: tle.line2, start: 26, length: 7) / 1e7
        let meanAnomaly = extractTLEParameter(line: tle.line2, start: 43, length: 8)
        
        // 计算当前位置（简化版 - 使用圆形轨道近似）
        let timeSinceEpoch = time.timeIntervalSince1970 - getEpochTime(from: tle.line1)
        let meanMotion = extractTLEParameter(line: tle.line1, start: 52, length: 11) * 2 * .pi / 86400.0
        
        let currentMeanAnomaly = meanAnomaly + meanMotion * timeSinceEpoch
        let trueAnomaly = currentMeanAnomaly // 简化：假设圆轨道
        
        // 计算位置
        let r = RE_KM + 500.0 // 假设500km高度
        let x = r * cos(trueAnomaly)
        let y = r * sin(trueAnomaly)
        let z = 0.0
        
        // 转换为经纬度
        let lat = asin(z / r) * 180.0 / .pi
        let lon = atan2(y, x) * 180.0 / .pi
        let alt = r - RE_KM
        
        return (x, y, z, lat, lon, alt)
    }
    
    // 计算卫星的观测方位
    static func computeLook(satellite: Satellite, observerLat: Double, observerLon: Double, observerAlt: Double, at time: Date) -> SatelliteLook {
        let position = computePosition(tle: satellite.tle, at: time)
        
        // 转换为ENU坐标系
        let satLat = position.lat * .pi / 180.0
        let satLon = position.lon * .pi / 180.0
        let obsLat = observerLat * .pi / 180.0
        let obsLon = observerLon * .pi / 180.0
        
        // 计算相对位置
        let dLat = satLat - obsLat
        let dLon = satLon - obsLon
        
        // 简化的方位角和仰角计算
        let azimuth = atan2(sin(dLon), cos(dLat) * tan(obsLat) - sin(dLat) * cos(dLon)) * 180.0 / .pi
        let elevation = asin(sin(dLat) * sin(satLat) + cos(dLat) * cos(satLat) * cos(dLon)) * 180.0 / .pi
        
        // 计算距离
        let range = sqrt(pow(position.x, 2) + pow(position.y, 2) + pow(position.z, 2))
        
        let isVisible = elevation > 0.0
        
        return SatelliteLook(
            satellite: satellite,
            azimuthDeg: azimuth,
            elevationDeg: elevation,
            rangeKm: range,
            altitudeKm: position.alt,
            isVisible: isVisible
        )
    }
    
    // 计算卫星下点
    static func computeSubPoint(satellite: Satellite, at time: Date) -> SatSubPoint {
        let position = computePosition(tle: satellite.tle, at: time)
        return SatSubPoint(
            satellite: satellite,
            latDeg: position.lat,
            lonDeg: position.lon,
            altKm: position.alt
        )
    }
    
    // 辅助函数：从TLE行提取参数
    private static func extractTLEParameter(line: String, start: Int, length: Int) -> Double {
        let index = line.index(line.startIndex, offsetBy: start)
        let endIndex = line.index(index, offsetBy: length)
        let substring = String(line[index..<endIndex]).trimmingCharacters(in: .whitespaces)
        return Double(substring) ?? 0.0
    }
    
    // 辅助函数：从TLE获取时间
    private static func getEpochTime(from line: String) -> TimeInterval {
        // 简化实现：返回当前时间
        return Date().timeIntervalSince1970
    }
}