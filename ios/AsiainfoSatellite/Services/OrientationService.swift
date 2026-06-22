import Foundation
import CoreMotion
import Combine

class OrientationService: ObservableObject {
    @Published var azimuth: Double = 0.0
    @Published var pitch: Double = 0.0
    @Published var roll: Double = 0.0
    
    private let motionManager = CMMotionManager()
    private var timer: Timer?
    
    init() {
        startUpdates()
    }
    
    func startUpdates() {
        guard motionManager.isDeviceMotionAvailable else {
            print("Device motion not available")
            return
        }
        
        motionManager.deviceMotionUpdateInterval = 0.1
        motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical)
        
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.updateOrientation()
        }
    }
    
    func stopUpdates() {
        motionManager.stopDeviceMotionUpdates()
        timer?.invalidate()
        timer = nil
    }
    
    private func updateOrientation() {
        guard let motion = motionManager.deviceMotion else { return }
        
        // 计算方位角（简化版）
        let attitude = motion.attitude
        azimuth = attitude.yaw * 180.0 / .pi
        pitch = attitude.pitch * 180.0 / .pi
        roll = attitude.roll * 180.0 / .pi
        
        // 确保角度在0-360范围内
        if azimuth < 0 {
            azimuth += 360.0
        }
    }
    
    deinit {
        stopUpdates()
    }
}