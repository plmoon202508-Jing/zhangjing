package com.asiainfo.satellite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.SatelliteFactory
import com.github.amsacode.predict4java.TLE
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 卫星数据仓库
 * 从 CelesTrak 拉取真实 TLE，构建 SGP4 传播器，并提供站心方位计算
 */
class SatelliteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: List<Satellite>? = null

    /**
     * 获取所有目标星座卫星（带缓存）
     */
    suspend fun getAllSatellites(forceRefresh: Boolean = false): Result<List<Satellite>> {
        return withContext(Dispatchers.IO) {
            try {
                cached?.let { if (!forceRefresh) return@withContext Result.success(it) }

                val seenNorad = HashSet<String>()
                val result = ArrayList<Satellite>()
                var id = 0

                for (constellation in SatelliteConstellation.values()) {
                    val tles = ArrayList<TLEData>()
                    for (queryName in constellation.celestrakNames) {
                        tles += fetchTles(queryName)
                    }
                    for (tle in tles) {
                        // 天启：仅保留 TIANQI- 前缀，排除天琴 TIANQIN
                        if (constellation == SatelliteConstellation.TQ) {
                            val n = tle.name.uppercase()
                            if (!n.startsWith("TIANQI") || n.startsWith("TIANQIN")) continue
                        }
                        if (!seenNorad.add(tle.noradId)) continue
                        val propagator = buildPropagator(tle) ?: continue
                        result += Satellite(id++, tle.name, constellation, tle, propagator)
                    }
                }

                if (result.isEmpty()) {
                    Result.failure(IllegalStateException("未获取到卫星数据，请检查网络"))
                } else {
                    cached = result
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 计算一批卫星相对观测者的方位/俯仰（站心坐标）
     */
    fun computeLooks(
        satellites: List<Satellite>,
        latDeg: Double,
        lonDeg: Double,
        altMeters: Double,
        date: Date = Date()
    ): List<SatelliteLook> {
        val gs = GroundStationPosition(latDeg, lonDeg, altMeters)
        val looks = ArrayList<SatelliteLook>(satellites.size)
        for (sat in satellites) {
            try {
                val pos = sat.propagator.getPosition(gs, date)
                looks += SatelliteLook(
                    satellite = sat,
                    azimuthDeg = normalizeAzimuth(Math.toDegrees(pos.azimuth)),
                    elevationDeg = Math.toDegrees(pos.elevation),
                    rangeKm = pos.range,
                    altitudeKm = pos.altitude
                )
            } catch (_: Exception) {
                // 个别卫星传播失败时跳过
            }
        }
        return looks
    }

    fun filterByConstellation(
        satellites: List<Satellite>,
        constellation: SatelliteConstellation?
    ): List<Satellite> =
        if (constellation == null) satellites
        else satellites.filter { it.constellation == constellation }

    // ---- 内部方法 ----

    private fun fetchTles(queryName: String): List<TLEData> {
        val url = "https://celestrak.org/NORAD/elements/gp.php?NAME=$queryName&FORMAT=tle"
        val request = Request.Builder().url(url).header("User-Agent", "AsiaInfoSatellite/1.0").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            return parseTleText(body)
        }
    }

    private fun parseTleText(text: String): List<TLEData> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<TLEData>()
        var i = 0
        while (i + 2 < lines.size) {
            val name = lines[i]
            val l1 = lines[i + 1]
            val l2 = lines[i + 2]
            if (l1.startsWith("1 ") && l2.startsWith("2 ")) {
                out += TLEData(name, l1, l2)
                i += 3
            } else {
                i += 1
            }
        }
        return out
    }

    private fun buildPropagator(tle: TLEData): com.github.amsacode.predict4java.Satellite? {
        return try {
            val sgp4Tle = TLE(arrayOf(tle.name, tle.line1, tle.line2))
            SatelliteFactory.createSatellite(sgp4Tle)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAzimuth(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }
}
