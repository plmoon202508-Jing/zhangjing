import Foundation

// 卫星星座枚举
enum SatelliteConstellation: String, CaseIterable {
    case G60 = "千帆G60"
    case GW = "星网GW"
    case TQ = "天启"
    
    var displayName: String {
        return self.rawValue
    }
}

// 卫星数据模型
struct Satellite {
    let id: String
    let name: String
    let constellation: SatelliteConstellation
    let tle: TLEData
}

// TLE数据
struct TLEData {
    let line1: String
    let line2: String
    let noradId: String
    
    init(line1: String, line2: String) {
        self.line1 = line1
        self.line2 = line2
        // 从TLE中提取NORAD ID
        let parts = line1.split(separator: " ")
        if parts.count > 1 {
            self.noradId = String(parts[1])
        } else {
            self.noradId = "00000"
        }
    }
}

// 卫星观测数据
struct SatelliteLook {
    let satellite: Satellite
    let azimuthDeg: Double
    let elevationDeg: Double
    let rangeKm: Double
    let altitudeKm: Double
    let isVisible: Bool
}

// 卫星下点数据
struct SatSubPoint {
    let satellite: Satellite
    let latDeg: Double
    let lonDeg: Double
    let altKm: Double
}