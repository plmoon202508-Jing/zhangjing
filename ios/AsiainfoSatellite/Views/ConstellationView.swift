import SwiftUI
import CoreGraphics

struct ConstellationView: View {
    @StateObject private var satelliteService = SatelliteService()
    @State private var selectedConstellation: SatelliteConstellation? = nil
    @State private var spin: Double = 0.0
    @State private var tilt: Double = -20.0
    @State private var autoSpin = true
    @State private var selectedSatellite: SatSubPoint?
    @State private var renderMode: GlobeRenderMode = .grid
    
    private let timer = Timer.publish(every: 0.05, on: .main, in: .common).autoconnect()
    
    var filteredSatellites: [Satellite] {
        if let constellation = selectedConstellation {
            return satelliteService.satellites.filter { $0.constellation == constellation }
        }
        return satelliteService.satellites
    }
    
    var body: some View {
        ZStack {
            // 背景
            LinearGradient(
                colors: [Color(red: 0.04, green: 0.07, blue: 0.2), Color(red: 0.01, green: 0.02, blue: 0.04)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack {
                // 顶部筛选器
                constellationFilter
                
                // 地球渲染
                GeometryReader { geometry in
                    GlobeView(
                        satellites: satelliteService.computeSubPoints(satellites: filteredSatellites),
                        spin: spin,
                        tilt: tilt,
                        renderMode: renderMode,
                        selectedSatellite: $selectedSatellite
                    )
                    .gesture(
                        DragGesture()
                            .onChanged { value in
                                autoSpin = false
                                spin = (spin + value.translation.width * 0.3).truncatingRemainder(dividingBy: 360)
                                tilt = (tilt - value.translation.height * 0.3).clamped(to: -85...85)
                            }
                            .onEnded { _ in
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                                    autoSpin = true
                                }
                            }
                    )
                }
                
                // 渲染模式切换
                renderModePicker
                
                // 底部信息
                satelliteInfo
            }
            
            // 加载状态
            if satelliteService.isLoading {
                ProgressView("加载星座数据...")
                    .padding()
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(10)
            }
            
            // 错误提示
            if let error = satelliteService.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .padding()
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(10)
            }
        }
        .onAppear {
            satelliteService.fetchAllSatellites()
        }
        .onReceive(timer) { _ in
            if autoSpin {
                spin = (spin + 0.12).truncatingRemainder(dividingBy: 360)
            }
        }
        .sheet(item: $selectedSatellite) { satellite in
            SatelliteDetailSheet(satellite: satellite)
        }
    }
    
    private var constellationFilter: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(title: "全部", isSelected: selectedConstellation == nil) {
                    selectedConstellation = nil
                }
                
                ForEach(SatelliteConstellation.allCases, id: \.self) { constellation in
                    FilterChip(title: constellation.displayName, isSelected: selectedConstellation == constellation) {
                        selectedConstellation = constellation
                    }
                }
            }
            .padding()
        }
    }
    
    private var renderModePicker: some View {
        HStack {
            Text("渲染模式:")
                .foregroundColor(.gray)
            
            Picker("渲染模式", selection: $renderMode) {
                Text("网格").tag(GlobeRenderMode.grid)
                Text("点阵").tag(GlobeRenderMode.dotMatrix)
                Text("真实").tag(GlobeRenderMode.realistic)
            }
            .pickerStyle(SegmentedPickerStyle())
        }
        .padding()
    }
    
    private var satelliteInfo: some View {
        HStack {
            Text("\(selectedConstellation?.displayName ?? "全部星座") · 在轨")
                .foregroundColor(.gray)
            Text("\(filteredSatellites.count)")
                .font(.headline)
                .foregroundColor(constellationColor(selectedConstellation))
            Text("颗")
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color.black.opacity(0.5))
        .cornerRadius(10)
    }
    
    private func constellationColor(_ constellation: SatelliteConstellation?) -> Color {
        switch constellation {
        case .G60: return Color(red: 1.0, green: 0.3, green: 0.8)
        case .GW: return Color(red: 0.2, green: 0.9, blue: 1.0)
        case .TQ: return Color(red: 0.3, green: 1.0, blue: 0.7)
        case nil: return Color(red: 0.6, green: 0.9, blue: 1.0)
        }
    }
}

enum GlobeRenderMode {
    case grid
    case dotMatrix
    case realistic
}

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(isSelected ? Color.blue : Color.gray.opacity(0.3))
                .foregroundColor(isSelected ? .white : .gray)
                .cornerRadius(20)
        }
    }
}

struct GlobeView: View {
    let satellites: [SatSubPoint]
    let spin: Double
    let tilt: Double
    let renderMode: GlobeRenderMode
    @Binding var selectedSatellite: SatSubPoint?
    
    var body: some View {
        Canvas { context, size in
            let cx = size.width / 2
            let cy = size.height / 2
            let radius = min(size.width, size.height) * 0.4
            
            // 绘制地球
            drawGlobe(context: context, cx: cx, cy: cy, radius: radius, spin: spin, tilt: tilt, mode: renderMode)
            
            // 绘制卫星
            for satellite in satellites {
                let projected = project(satellite: satellite, spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius)
                if projected.depth >= 0 {
                    drawSatellite(context: context, position: projected, isSelected: selectedSatellite?.satellite.id == satellite.satellite.id)
                }
            }
        }
    }
    
    private func drawGlobe(context: GraphicsContext, cx: CGFloat, cy: CGFloat, radius: CGFloat, spin: Double, tilt: Double, mode: GlobeRenderMode) {
        // 大气辉光
        let glowGradient = Gradient(colors: [
            Color.blue.opacity(0.2),
            Color.clear
        ])
        context.fill(Path(ellipseIn: CGRect(x: cx - radius * 1.35, y: cy - radius * 1.35, width: radius * 2.7, height: radius * 2.7)), with: .radialGradient(glowGradient, center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: radius * 1.35))
        
        // 地球主体
        let earthGradient = Gradient(colors: [
            Color(red: 0.1, green: 0.2, blue: 0.4),
            Color(red: 0.04, green: 0.1, blue: 0.2),
            Color(red: 0.02, green: 0.05, blue: 0.1)
        ])
        context.fill(Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(earthGradient, center: CGPoint(x: cx - radius * 0.3, y: cy - radius * 0.3), startRadius: 0, endRadius: radius * 1.4))
        
        // 根据模式绘制
        switch mode {
        case .grid:
            drawGrid(context: context, cx: cx, cy: cy, radius: radius, spin: spin, tilt: tilt)
        case .dotMatrix:
            drawContinents(context: context, cx: cx, cy: cy, radius: radius, spin: spin, tilt: tilt)
        case .realistic:
            drawRealistic(context: context, cx: cx, cy: cy, radius: radius, spin: spin, tilt: tilt)
        }
        
        // 轮廓
        context.stroke(Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)), with: .color(.blue.opacity(0.5)), lineWidth: 1.6)
    }
    
    private func drawGrid(context: GraphicsContext, cx: CGFloat, cy: CGFloat, radius: CGFloat, spin: Double, tilt: Double) {
        let gridColor = Color.blue.opacity(0.2)
        
        // 纬线
        for lat in stride(from: -60, through: 60, by: 30) {
            var prev: ProjectedPoint?
            for lon in stride(from: 0, through: 360, by: 6) {
                let p = project(lat: Double(lat), lon: Double(lon), spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius)
                if let prev = prev, prev.depth >= 0, p.depth >= 0 {
                    context.stroke(Path { path in
                        path.move(to: CGPoint(x: prev.x, y: prev.y))
                        path.addLine(to: CGPoint(x: p.x, y: p.y))
                    }, with: .color(gridColor), lineWidth: 1.4)
                }
                prev = p
            }
        }
        
        // 经线
        for lon in stride(from: 0, to: 360, by: 30) {
            var prev: ProjectedPoint?
            for lat in stride(from: -90, through: 90, by: 6) {
                let p = project(lat: Double(lat), lon: Double(lon), spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius)
                if let prev = prev, prev.depth >= 0, p.depth >= 0 {
                    context.stroke(Path { path in
                        path.move(to: CGPoint(x: prev.x, y: prev.y))
                        path.addLine(to: CGPoint(x: p.x, y: p.y))
                    }, with: .color(gridColor), lineWidth: 1.4)
                }
                prev = p
            }
        }
    }
    
    private func drawContinents(context: GraphicsContext, cx: CGFloat, cy: CGFloat, radius: CGFloat, spin: Double, tilt: Double) {
        // 简化的大陆点阵
        let continentPoints = [
            (lat: 40.0, lon: -100.0), // 北美
            (lat: -15.0, lon: -60.0), // 南美
            (lat: 50.0, lon: 10.0), // 欧洲
            (lat: 0.0, lon: 20.0), // 非洲
            (lat: 40.0, lon: 100.0), // 亚洲
            (lat: -25.0, lon: 135.0), // 澳大利亚
        ]
        
        for point in continentPoints {
            let p = project(lat: point.lat, lon: point.lon, spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius)
            if p.depth >= 0 {
                context.fill(Path(ellipseIn: CGRect(x: p.x - 2.5, y: p.y - 2.5, width: 5, height: 5)), with: .color(.blue.opacity(0.6)))
            }
        }
    }
    
    private func drawRealistic(context: GraphicsContext, cx: CGFloat, cy: CGFloat, radius: CGFloat, spin: Double, tilt: Double) {
        // 真实地球模式（简化版）
        let oceanGradient = Gradient(colors: [
            Color(red: 0.1, green: 0.3, blue: 0.5),
            Color(red: 0.04, green: 0.17, blue: 0.29),
            Color(red: 0.02, green: 0.09, blue: 0.15)
        ])
        context.fill(Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(oceanGradient, center: CGPoint(x: cx - radius * 0.3, y: cy - radius * 0.3), startRadius: 0, endRadius: radius * 1.4))
        
        // 陆地（简化版）
        let landColor = Color(red: 0.2, green: 0.5, blue: 0.4).opacity(0.7)
        for point in [(lat: 40.0, lon: -100.0), (lat: -15.0, lon: -60.0), (lat: 50.0, lon: 10.0), (lat: 0.0, lon: 20.0), (lat: 40.0, lon: 100.0), (lat: -25.0, lon: 135.0)] {
            let p = project(lat: point.lat, lon: point.lon, spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius)
            if p.depth >= 0 {
                context.fill(Path(ellipseIn: CGRect(x: p.x - 4, y: p.y - 4, width: 8, height: 8)), with: .color(landColor))
            }
        }
    }
    
    private func drawSatellite(context: GraphicsContext, position: ProjectedPoint, isSelected: Bool) {
        let scale: CGFloat = isSelected ? 1.6 : 1.0
        
        // 光晕
        context.fill(Path(ellipseIn: CGRect(x: position.x - 11 * scale, y: position.y - 11 * scale, width: 22 * scale, height: 22 * scale)), with: .color(.blue.opacity(0.25)))
        
        // 卫星主体
        context.fill(Path(ellipseIn: CGRect(x: position.x - 4.5 * scale, y: position.y - 4.5 * scale, width: 9 * scale, height: 9 * scale)), with: .color(.blue))
        
        // 中心亮点
        context.fill(Path(ellipseIn: CGRect(x: position.x - 1.8 * scale, y: position.y - 1.8 * scale, width: 3.6 * scale, height: 3.6 * scale)), with: .color(.white))
        
        if isSelected {
            context.stroke(Path(ellipseIn: CGRect(x: position.x - 16, y: position.y - 16, width: 32, height: 32)), with: .color(.blue), lineWidth: 2)
        }
    }
    
    private func project(satellite: SatSubPoint, spin: Double, tilt: Double, cx: CGFloat, cy: CGFloat, radius: CGFloat) -> ProjectedPoint {
        let r = 1.0 + satellite.altKm / 6371.0
        return project(lat: satellite.latDeg, lon: satellite.lonDeg, spin: spin, tilt: tilt, cx: cx, cy: cy, radius: radius) * r
    }
    
    private func project(lat: Double, lon: Double, spin: Double, tilt: Double, cx: CGFloat, cy: CGFloat, radius: CGFloat) -> ProjectedPoint {
        let latRad = lat * .pi / 180.0
        let lonRad = (lon + spin) * .pi / 180.0
        let cl = cos(latRad)
        let x = cl * sin(lonRad)
        let y = sin(latRad)
        let z = cl * cos(lonRad)
        
        let tiltRad = tilt * .pi / 180.0
        let y2 = y * cos(tiltRad) - z * sin(tiltRad)
        let z2 = y * sin(tiltRad) + z * cos(tiltRad)
        
        let sx = cx + radius * CGFloat(x)
        let sy = cy - radius * CGFloat(y2)
        
        return ProjectedPoint(x: sx, y: sy, depth: CGFloat(z2))
    }
}

struct ProjectedPoint {
    let x: CGFloat
    let y: CGFloat
    let depth: CGFloat
}

struct SatelliteDetailSheet: View {
    let satellite: SatSubPoint
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // 卫星信息
                VStack {
                    Text(satellite.satellite.name)
                        .font(.title)
                        .fontWeight(.bold)
                    Text(satellite.satellite.constellation.displayName)
                        .foregroundColor(.blue)
                }
                
                Divider()
                
                // 详细数据
                HStack {
                    VStack {
                        Text("纬度")
                            .foregroundColor(.gray)
                        Text(String(format: "%.2f°", satellite.latDeg))
                            .font(.headline)
                    }
                    Spacer()
                    VStack {
                        Text("经度")
                            .foregroundColor(.gray)
                        Text(String(format: "%.2f°", satellite.lonDeg))
                            .font(.headline)
                    }
                    Spacer()
                    VStack {
                        Text("高度")
                            .foregroundColor(.gray)
                        Text("\(Int(satellite.altKm)) km")
                            .font(.headline)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(10)
                
                Spacer()
            }
            .padding()
            .navigationTitle("卫星详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("关闭") {
                        dismiss()
                    }
                }
            }
        }
    }
}