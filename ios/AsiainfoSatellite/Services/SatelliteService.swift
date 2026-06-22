import Foundation
import Combine

class SatelliteService: ObservableObject {
    @Published var satellites: [Satellite] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let baseURL = "https://celestrak.org/NORAD/elements/gp.php"
    
    // 获取所有卫星数据
    func fetchAllSatellites() {
        isLoading = true
        errorMessage = nil
        
        // 千帆G60星座
        fetchConstellation(constellation: .G60, groupId: "1-5")
        // 星网GW星座
        fetchConstellation(constellation: .GW, groupId: "1-6")
        // 天启星座
        fetchConstellation(constellation: .TQ, groupId: "1-7")
    }
    
    private func fetchConstellation(constellation: SatelliteConstellation, groupId: String) {
        let urlString = "\(baseURL)?GROUP=\(groupId)&FORMAT=tle"
        guard let url = URL(string: urlString) else { return }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    self?.errorMessage = "加载失败: \(error.localizedDescription)"
                    return
                }
                
                guard let data = data, let tleString = String(data: data, encoding: .utf8) else {
                    self?.errorMessage = "数据解析失败"
                    return
                }
                
                let satellites = self?.parseTLE(tleString: tleString, constellation: constellation) ?? []
                self?.satellites.append(contentsOf: satellites)
            }
        }.resume()
    }
    
    // 解析TLE数据
    private func parseTLE(tleString: String, constellation: SatelliteConstellation) -> [Satellite] {
        var satellites: [Satellite] = []
        let lines = tleString.components(separatedBy: .newlines)
        
        var i = 0
        while i < lines.count - 2 {
            let line0 = lines[i].trimmingCharacters(in: .whitespaces)
            let line1 = lines[i + 1].trimmingCharacters(in: .whitespaces)
            let line2 = lines[i + 2].trimmingCharacters(in: .whitespaces)
            
            if !line0.isEmpty && line1.hasPrefix("1 ") && line2.hasPrefix("2 ") {
                let tle = TLEData(line1: line1, line2: line2)
                let satellite = Satellite(
                    id: tle.noradId,
                    name: line0,
                    constellation: constellation,
                    tle: tle
                )
                satellites.append(satellite)
            }
            
            i += 3
        }
        
        return satellites
    }
    
    // 计算所有卫星的观测数据
    func computeLooks(satellites: [Satellite], observerLat: Double, observerLon: Double, observerAlt: Double) -> [SatelliteLook] {
        let now = Date()
        return satellites.map { satellite in
            SGP4.computeLook(
                satellite: satellite,
                observerLat: observerLat,
                observerLon: observerLon,
                observerAlt: observerAlt,
                at: now
            )
        }
    }
    
    // 计算所有卫星的下点
    func computeSubPoints(satellites: [Satellite]) -> [SatSubPoint] {
        let now = Date()
        return satellites.map { satellite in
            SGP4.computeSubPoint(satellite: satellite, at: now)
        }
    }
}