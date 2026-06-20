package com.asiainfo.satellite.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext

/**
 * 设备朝向（指向天空时摄像头光轴方向）
 * @param azimuth 方位角 0-360（设备指向的罗盘方向，正北顺时针）
 * @param pitch   俯仰角 -90~90（设备指向地平线以上为正）
 * @param roll    横滚角
 */
data class DeviceOrientation(
    val azimuth: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f
)

/**
 * 监听旋转矢量传感器，返回设备朝向。
 * 使用「相机指向」模型：手机竖直举起、背面摄像头对准天空时，
 * azimuth 表示摄像头光轴的水平方位，pitch 表示其仰角。
 */
@Composable
fun rememberDeviceOrientation(): State<DeviceOrientation> {
    val context = LocalContext.current
    val orientationState = remember { mutableStateOf(DeviceOrientation()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // 将坐标系重映射为「相机看向的方向」：
                // 设备 Z 轴（屏幕外法线）取反即摄像头方向。
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
                SensorManager.getOrientation(remappedMatrix, orientationAngles)

                var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                orientationState.value = DeviceOrientation(
                    azimuth = azimuthDeg,
                    pitch = -pitchDeg,
                    roll = rollDeg
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }

    return orientationState
}
