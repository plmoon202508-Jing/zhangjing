import SwiftUI
import AVFoundation
import CoreMotion

struct ARView: View {
    @StateObject private var satelliteService = SatelliteService()
    @StateObject private var locationService = LocationService()
    @StateObject private var orientationService = OrientationService()
    
    @State private var selectedConstellation: SatelliteConstellation = .G60
    @State private var userName: String = ""
    @State private var showNameDialog = false
    @State private var selectedSatellite: SatelliteLook?
    @State private var cameraPermissionGranted = false
    
    var filteredLooks: [SatelliteLook] {
        let allLooks = satelliteService.computeLooks(
            satellites: satelliteService.satellites.filter { $0.constellation == selectedConstellation },
            observerLat: locationService.latitude,
            observerLon: locationService.longitude,
            observerAlt: locationService.altitude
        )
        return allLooks.filter { $0.isVisible && $0.elevationDeg > 0 }
            .sorted { $0.elevationDeg > $1.elevationDeg }
    }
    
    var body: some View {
        ZStack {
            // 相机预览
            if cameraPermissionGranted {
                CameraPreview()
            } else {
                Color.black
                    .overlay(
                        VStack {
                            Text("需要相机权限以开启AR实景")
                                .foregroundColor(.white)
                                .padding()
                            Button("授予权限") {
                                requestCameraPermission()
                            }
                            .buttonStyle(.bordered)
                        }
                    )
            }
            
            // 卫星叠加层
            SatelliteOverlay(
                looks: filteredLooks,
                deviceAzimuth: orientationService.azimuth,
                devicePitch: orientationService.pitch,
                onSelect: { satellite in
                    if userName.isEmpty {
                        showNameDialog = true
                    } else {
                        selectedSatellite = satellite
                    }
                }
            )
            
            // 准星
            Text("+")
                .font(.system(size: 28))
                .foregroundColor(.white.opacity(0.5))
            
            // 顶部信息
            VStack {
                // 指南针
                compassView
                
                // 星座选择
                constellationSelector
                
                Spacer()
                
                // 底部信息
                arInfo
            }
            .padding()
        }
        .onAppear {
            satelliteService.fetchAllSatellites()
            locationService.requestLocation()
            checkCameraPermission()
        }
        .alert("请输入您的姓名", isPresented: $showNameDialog) {
            TextField("姓名", text: $userName)
            Button("确定") {
                showNameDialog = false
            }
            Button("取消") {
                showNameDialog = false
            }
        }
        .sheet(item: $selectedSatellite) { satellite in
            ARSatelliteDetailSheet(
                satellite: satellite,
                userName: userName
            )
        }
    }
    
    private var compassView: some View {
        HStack {
            Text(directionText)
                .font(.headline)
                .foregroundColor(.blue)
            Text("\(Int(orientationService.azimuth))°")
                .foregroundColor(.white)
        }
        .padding()
        .background(Color.black.opacity(0.5))
        .cornerRadius(10)
    }
    
    private var constellationSelector: some View {
        Picker("星座", selection: $selectedConstellation) {
            ForEach(SatelliteConstellation.allCases, id: \.self) { constellation in
                Text(constellation.displayName).tag(constellation)
            }
        }
        .pickerStyle(SegmentedPickerStyle())
        .padding()
        .background(Color.black.opacity(0.5))
        .cornerRadius(10)
    }
    
    private var arInfo: some View {
        HStack {
            VStack {
                Text("方位角")
                    .foregroundColor(.gray)
                Text("\(Int(orientationService.azimuth))°")
                    .foregroundColor(.blue)
            }
            Spacer()
            VStack {
                Text("俯仰角")
                    .foregroundColor(.gray)
                Text("\(Int(orientationService.pitch))°")
                    .foregroundColor(.green)
            }
            Spacer()
            VStack {
                Text("\(selectedConstellation.displayName)·视野内")
                    .foregroundColor(.gray)
                Text("\(filteredLooks.count)")
                    .font(.headline)
                    .foregroundColor(constellationColor(selectedConstellation))
            }
        }
        .padding()
        .background(Color.black.opacity(0.5))
        .cornerRadius(10)
    }
    
    private var directionText: String {
        let directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        let index = Int(((orientationService.azimuth + 22.5).truncatingRemainder(dividingBy: 360)) / 45)
        return directions[index % 8]
    }
    
    private func constellationColor(_ constellation: SatelliteConstellation) -> Color {
        switch constellation {
        case .G60: return Color(red: 1.0, green: 0.3, blue: 0.8)
        case .GW: return Color(red: 0.2, green: 0.9, blue: 1.0)
        case .TQ: return Color(red: 0.3, green: 1.0, blue: 0.7)
        }
    }
    
    private func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraPermissionGranted = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    cameraPermissionGranted = granted
                }
            }
        default:
            cameraPermissionGranted = false
        }
    }
    
    private func requestCameraPermission() {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                cameraPermissionGranted = granted
            }
        }
    }
}

struct CameraPreview: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        
        let captureSession = AVCaptureSession()
        guard let captureDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: captureDevice) else {
            return view
        }
        
        captureSession.addInput(input)
        
        let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.frame = view.bounds
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        DispatchQueue.global(qos: .userInitiated).async {
            captureSession.startRunning()
        }
        
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {}
}

struct SatelliteOverlay: View {
    let looks: [SatelliteLook]
    let deviceAzimuth: Double
    let devicePitch: Double
    let onSelect: (SatelliteLook) -> Void
    
    private let hFOV: Double = 60.0
    private let vFOV: Double = 75.0
    
    var body: some View {
        GeometryReader { geometry in
            Canvas { context, size in
                let w = size.width
                let h = size.height
                
                for look in looks {
                    let projected = project(look: look, deviceAzimuth: deviceAzimuth, devicePitch: devicePitch, w: w, h: h)
                    if let p = projected {
                        drawSatellite(context: context, position: p, look: look)
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(
                TapGesture()
                    .onEnded { location in
                        handleTap(at: location, in: looks, geometry: geometry)
                    }
            )
        }
    }
    
    private func project(look: SatelliteLook, deviceAzimuth: Double, devicePitch: Double, w: CGFloat, h: CGFloat) -> CGPoint? {
        let dAz = angleDiff(look.azimuthDeg, deviceAzimuth)
        let dEl = look.elevationDeg - devicePitch
        
        if abs(dAz) <= hFOV / 2 && abs(dEl) <= vFOV / 2 {
            let x = w / 2 + CGFloat(dAz / (hFOV / 2)) * (w / 2)
            let y = h / 2 - CGFloat(dEl / (vFOV / 2)) * (h / 2)
            return CGPoint(x: x, y: y)
        }
        return nil
    }
    
    private func drawSatellite(context: GraphicsContext, position: CGPoint, look: SatelliteLook) {
        let color = constellationColor(look.satellite.constellation)
        
        // 光晕
        context.fill(Path(ellipseIn: CGRect(x: position.x - 11, y: position.y - 11, width: 22, height: 22)), with: .color(color.opacity(0.25)))
        
        // 卫星主体
        context.fill(Path(ellipseIn: CGRect(x: position.x - 4.5, y: position.y - 4.5, width: 9, height: 9)), with: .color(color))
        
        // 中心亮点
        context.fill(Path(ellipseIn: CGRect(x: position.x - 1.8, y: position.y - 1.8, width: 3.6, height: 3.6)), with: .color(.white))
        
        // 信息标签
        let text = "\(look.satellite.name)\n仰\(Int(look.elevationDeg))°"
        context.draw(Text(text).font(.system(size: 12)).foregroundColor(.white), at: CGPoint(x: position.x + 12, y: position.y - 8))
    }
    
    private func handleTap(at location: CGPoint, in looks: [SatelliteLook]) {
        // 简化的点击检测
        let geometry = GeometryProxy()
        let w: CGFloat = 400 // 假设宽度
        let h: CGFloat = 800 // 假设高度
        
        var closest: SatelliteLook?
        var minDistance: CGFloat = 50.0 // 点击容差
        
        for look in looks {
            if let projected = project(look: look, deviceAzimuth: deviceAzimuth, devicePitch: devicePitch, w: w, h: h) {
                let distance = sqrt(pow(location.x - projected.x, 2) + pow(location.y - projected.y, 2))
                if distance < minDistance {
                    minDistance = distance
                    closest = look
                }
            }
        }
        
        if let closest = closest {
            onSelect(closest)
        }
    }
    
    private func angleDiff(_ a: Double, _ b: Double) -> Double {
        var d = a - b
        while d > 180 { d -= 360 }
        while d < -180 { d += 360 }
        return d
    }
    
    private func constellationColor(_ constellation: SatelliteConstellation) -> Color {
        switch constellation {
        case .G60: return Color(red: 1.0, green: 0.3, blue: 0.8)
        case .GW: return Color(red: 0.2, green: 0.9, blue: 1.0)
        case .TQ: return Color(red: 0.3, green: 1.0, blue: 0.7)
        }
    }
}

struct ARSatelliteDetailSheet: View {
    let satellite: SatelliteLook
    let userName: String
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // 体验人信息
                if !userName.isEmpty {
                    HStack {
                        Text("体验人：")
                            .foregroundColor(.gray)
                        Text(userName)
                            .foregroundColor(.blue)
                            .fontWeight(.bold)
                    }
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(10)
                }
                
                // 卫星信息
                VStack {
                    Text(satellite.satellite.name)
                        .font(.title)
                        .fontWeight(.bold)
                    Text(satellite.satellite.constellation.displayName)
                        .foregroundColor(.blue)
                }
                
                Divider()
                
                // 详细数据（一排2个指标）
                HStack {
                    VStack {
                        Text("方位角")
                            .foregroundColor(.gray)
                        Text("\(Int(satellite.azimuthDeg))°")
                            .font(.headline)
                    }
                    Spacer()
                    VStack {
                        Text("俯仰角")
                            .foregroundColor(.gray)
                        Text("\(Int(satellite.elevationDeg))°")
                            .font(.headline)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(10)
                
                HStack {
                    VStack {
                        Text("斜距")
                            .foregroundColor(.gray)
                        Text("\(Int(satellite.rangeKm)) km")
                            .font(.headline)
                    }
                    Spacer()
                    VStack {
                        Text("轨道高度")
                            .foregroundColor(.gray)
                        Text("\(Int(satellite.altitudeKm)) km")
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