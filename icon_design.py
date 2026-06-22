#!/usr/bin/env python3
"""
生成亚信卫星时刻APP图标
设计：地球 + 卫星环绕
"""

from PIL import Image, ImageDraw, ImageFont
import math

def create_app_icon(size=512):
    """
    创建APP图标
    :param size: 图标尺寸
    :return: PIL Image对象
    """
    # 创建透明背景
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    center = size // 2
    radius = size // 3
    
    # 绘制地球（渐变蓝色）
    for i in range(radius):
        alpha = int(255 * (1 - i / radius))
        color = (30, 78, 124, alpha)  # 深蓝色
        draw.ellipse([
            center - radius + i, 
            center - radius + i, 
            center + radius - i, 
            center + radius - i
        ], fill=color)
    
    # 绘制地球高光
    highlight_radius = radius // 3
    draw.ellipse([
        center - radius // 2 - highlight_radius // 2,
        center - radius // 2 - highlight_radius // 2,
        center - radius // 2 + highlight_radius // 2,
        center - radius // 2 + highlight_radius // 2
    ], fill=(100, 180, 255, 100))
    
    # 绘制卫星轨道
    orbit_radius = radius + size // 8
    for angle in range(0, 360, 5):
        rad = math.radians(angle)
        # 椭圆轨道
        x = center + orbit_radius * math.cos(rad) * 0.8
        y = center + orbit_radius * math.sin(rad) * 0.4
        if angle % 10 == 0:
            draw.ellipse([x-2, y-2, x+2, y+2], fill=(45, 226, 255, 180))
    
    # 绘制卫星
    sat_x = center + orbit_radius * 0.8  # 卫星位置
    sat_y = center
    
    # 卫星主体
    sat_size = size // 10
    draw.rectangle([
        sat_x - sat_size // 2, 
        sat_y - sat_size // 2,
        sat_x + sat_size // 2, 
        sat_y + sat_size // 2
    ], fill=(45, 226, 255, 255))
    
    # 卫星太阳能板
    panel_width = sat_size * 2
    panel_height = sat_size // 2
    draw.rectangle([
        sat_x - panel_width // 2,
        sat_y - panel_height // 2,
        sat_x + panel_width // 2,
        sat_y + panel_height // 2
    ], fill=(30, 144, 255, 200))
    
    # 添加大气层辉光
    glow_radius = radius + size // 10
    for i in range(10):
        alpha = int(50 * (1 - i / 10))
        draw.ellipse([
            center - glow_radius - i * 2,
            center - glow_radius - i * 2,
            center + glow_radius + i * 2,
            center + glow_radius + i * 2
        ], outline=(45, 226, 255, alpha), width=2)
    
    return img

def generate_android_icons():
    """生成Android所需的各种尺寸图标"""
    sizes = [
        (72, 'mipmap-hdpi/ic_launcher.png'),
        (96, 'mipmap-xhdpi/ic_launcher.png'),
        (144, 'mipmap-xxhdpi/ic_launcher.png'),
        (192, 'mipmap-xxxhdpi/ic_launcher.png'),
        (48, 'mipmap-mdpi/ic_launcher.png'),
    ]
    
    base_path = '/Users/luyan/satellite-app/android/app/src/main/res'
    
    for size, path in sizes:
        icon = create_app_icon(size)
        full_path = f'{base_path}/{path}'
        import os
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        icon.save(full_path)
        print(f'Generated: {full_path}')

if __name__ == '__main__':
    generate_android_icons()
    print('Android icons generated successfully!')