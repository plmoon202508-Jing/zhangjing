import SwiftUI

struct MainView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem {
                    Label("首页", systemImage: "house.fill")
                }
                .tag(0)
            
            ConstellationView()
                .tabItem {
                    Label("星座全景", systemImage: "globe.americas.fill")
                }
                .tag(1)
            
            ARView()
                .tabItem {
                    Label("AR卫星", systemImage: "camera.fill")
                }
                .tag(2)
        }
        .accentColor(.cyan)
    }
}

struct HomeView: View {
    var body: some View {
        NavigationView {
            ZStack {
                // 背景渐变
                LinearGradient(
                    colors: [Color(red: 0.04, green: 0.07, blue: 0.2), Color(red: 0.01, green: 0.02, blue: 0.04)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    Spacer()
                    
                    // Logo
                    Image("asiainfo_logo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 200, height: 96)
                    
                    Text("亚信卫星时刻")
                        .font(.title)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                    
                    Text("ASIAINFO · SATELLITE MOMENT")
                        .font(.caption)
                        .tracking(4)
                        .foregroundColor(Color(red: 0.53, green: 0.63, blue: 0.75))
                    
                    Spacer()
                    
                    // 功能按钮
                    VStack(spacing: 15) {
                        NavigationButton(
                            title: "星座全景",
                            subtitle: "地球卫星星座可视化",
                            icon: "globe.americas.fill",
                            color: .cyan
                        )
                        
                        NavigationButton(
                            title: "AR 卫星",
                            subtitle: "实景增强卫星观测",
                            icon: "camera.fill",
                            color: .purple
                        )
                    }
                    .padding(.horizontal, 28)
                    
                    Spacer()
                    
                    // 下载二维码
                    VStack {
                        HStack {
                            Image(systemName: "qrcode")
                                .foregroundColor(Color(red: 0.53, green: 0.63, blue: 0.75))
                            Text("APP下载二维码")
                                .foregroundColor(.white)
                                .font(.subheadline)
                        }
                        .padding()
                        .background(Color(red: 0.07, green: 0.11, blue: 0.21))
                        .cornerRadius(12)
                    }
                    
                    Text("v1.0.0 · 数据来源 CelesTrak")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.33, green: 0.4, blue: 0.5))
                        .padding(.bottom, 22)
                }
            }
            .navigationTitle("亚信卫星时刻")
            .navigationBarHidden(true)
        }
    }
}

struct NavigationButton: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    
    var body: some View {
        HStack {
            ZStack {
                RoundedRectangle(cornerRadius: 13)
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 46, height: 46)
                
                Image(systemName: icon)
                    .foregroundColor(color)
                    .font(.system(size: 26))
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .foregroundColor(.white)
                    .font(.headline)
                
                Text(subtitle)
                    .foregroundColor(Color(red: 0.53, green: 0.63, blue: 0.75))
                    .font(.caption)
            }
            
            Spacer()
            
            Text("→")
                .foregroundColor(Color(red: 0.53, green: 0.63, blue: 0.75))
                .font(.title3)
        }
        .padding()
        .background(Color(red: 0.07, green: 0.11, blue: 0.21).opacity(0.55))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(color.opacity(0.4), lineWidth: 1)
        )
        .cornerRadius(18)
    }
}

struct ConstellationView: View {
    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()
                Text("星座全景功能开发中...")
                    .foregroundColor(.white)
            }
            .navigationTitle("星座全景")
        }
    }
}

struct ARView: View {
    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()
                Text("AR卫星功能开发中...")
                    .foregroundColor(.white)
            }
            .navigationTitle("AR卫星")
        }
    }
}

#Preview {
    MainView()
}