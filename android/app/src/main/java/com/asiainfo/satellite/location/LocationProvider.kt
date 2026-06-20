package com.asiainfo.satellite.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 观测者位置
 */
data class ObserverLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double // 海拔高度（米）
)

/**
 * 通过 FusedLocationProvider 获取当前位置。
 * 调用前需确保已授予定位权限。
 */
@SuppressLint("MissingPermission")
suspend fun fetchObserverLocation(context: Context): ObserverLocation? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    cont.resume(
                        ObserverLocation(loc.latitude, loc.longitude, loc.altitude)
                    )
                } else {
                    // 回退到最后已知位置
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            cont.resume(
                                last?.let {
                                    ObserverLocation(it.latitude, it.longitude, it.altitude)
                                }
                            )
                        }
                        .addOnFailureListener { cont.resume(null) }
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
