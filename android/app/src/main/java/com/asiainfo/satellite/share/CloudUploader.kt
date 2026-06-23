package com.asiainfo.satellite.share

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 分享图片云上传：把生成的 PNG 上传到腾讯云主机的轻量服务（sat_share_server.py）。
 * 返回图片直链与 H5 查看/下载页地址（二维码即编码该页地址）。
 */
object CloudUploader {

    const val BASE = "http://101.35.112.92:8090"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    data class UploadResult(val id: String, val img: String, val page: String)
    data class ApkUploadResult(val id: String, val apk: String, val page: String)

    /**
     * 上传 PNG 字节；可指定 id 以覆盖已有图片（用于先传无码图、再回填二维码后覆盖）。
     * 失败抛异常。
     */
    fun upload(png: ByteArray, id: String? = null): UploadResult {
        val url = if (id != null) "$BASE/upload?id=$id" else "$BASE/upload"
        val body = png.toRequestBody("image/png".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("上传失败 HTTP ${resp.code}")
            val txt = resp.body?.string() ?: throw IllegalStateException("空响应")
            val j = JSONObject(txt)
            return UploadResult(j.getString("id"), j.getString("img"), j.getString("page"))
        }
    }

    /**
     * 上报星座全景命名事件到云端大屏（/screen 实时展示）。
     * 失败仅返回 false，不影响本地命名。
     */
    fun postNaming(
        satId: String,
        satName: String,
        customName: String,
        user: String?,
        constellation: String,
        lat: Double,
        lon: Double,
        alt: Double
    ): Boolean {
        return try {
            val json = JSONObject().apply {
                put("satId", satId)
                put("satName", satName)
                put("customName", customName)
                put("user", user ?: "")
                put("constellation", constellation)
                put("lat", lat)
                put("lon", lon)
                put("alt", alt)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url("$BASE/name").post(body).build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 上传 APK 字节；可指定 id 以覆盖已有文件。
     * 失败抛异常。
     */
    fun uploadApk(apk: ByteArray, id: String? = null): ApkUploadResult {
        val url = if (id != null) "$BASE/upload-apk?id=$id" else "$BASE/upload-apk"
        val body = apk.toRequestBody("application/vnd.android.package-archive".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("APK上传失败 HTTP ${resp.code}")
            val txt = resp.body?.string() ?: throw IllegalStateException("空响应")
            val j = JSONObject(txt)
            return ApkUploadResult(j.getString("id"), j.getString("apk"), j.getString("page"))
        }
    }
}
