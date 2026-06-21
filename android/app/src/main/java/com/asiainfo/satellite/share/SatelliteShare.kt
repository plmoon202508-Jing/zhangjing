package com.asiainfo.satellite.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.asiainfo.satellite.data.SatelliteLook
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 卫星分享：在底图基础上叠加卫星信息 + 二维码，生成分享图并通过系统分享面板分享。
 *
 * 底图放置于 assets/share_template.png（缺失时使用内置渐变兜底）。
 * 二维码内容由 [QR_URL] 控制（App 下载 / H5 体验链接）。
 */
object SatelliteShare {

    // TODO: 替换为真实的 App 下载页 / H5 体验链接
    const val QR_URL = "https://www.asiainfo.com"

    private const val TEMPLATE_ASSET = "share_template.png"

    /** 加载底图：优先 assets/share_template.png，缺失时生成渐变兜底底图 */
    private fun loadTemplate(context: Context): Bitmap {
        return try {
            context.assets.open(TEMPLATE_ASSET).use { BitmapFactory.decodeStream(it) }
                ?: fallbackTemplate()
        } catch (e: Exception) {
            fallbackTemplate()
        }
    }

    private fun fallbackTemplate(): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#0A1233"), Color.parseColor("#03040A")),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    /**
     * 合成分享图：底图 + 底部信息面板（卫星信息）+ 二维码。
     * 信息以相对比例排版，适配任意尺寸底图。
     */
    fun buildShareBitmap(
        context: Context,
        look: SatelliteLook,
        observerLat: Double?,
        observerLon: Double?,
        qrText: String = QR_URL
    ): Bitmap {
        val base = loadTemplate(context)
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        base.recycle()
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val unit = w / 1080f   // 以 1080 宽为基准缩放

        // 底部半透明信息面板
        val panelLeft = 60f * unit
        val panelRight = w - 60f * unit
        val panelBottom = h - 60f * unit
        val panelTop = h - 620f * unit
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC060C1C")
        }
        val radius = 36f * unit
        c.drawRoundRect(RectF(panelLeft, panelTop, panelRight, panelBottom), radius, radius, panelPaint)
        // 面板描边
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * unit
            color = Color.parseColor("#552DE2FF")
        }
        c.drawRoundRect(RectF(panelLeft, panelTop, panelRight, panelBottom), radius, radius, borderPaint)

        val padL = panelLeft + 48f * unit
        var y = panelTop + 92f * unit

        // 顶部标识
        val tagPaint = textPaint(Color.parseColor("#2DE2FF"), 30f * unit, true)
        c.drawText("亚信卫星时刻 · ASIAINFO SATELLITE MOMENT", padL, y, tagPaint)
        y += 76f * unit

        // 卫星名称
        val namePaint = textPaint(Color.WHITE, 64f * unit, true)
        c.drawText(look.satellite.name, padL, y, namePaint)
        y += 50f * unit

        // 星座
        val accent = constellationColor(look)
        val subPaint = textPaint(accent, 34f * unit, false)
        c.drawText(look.satellite.constellation.displayName, padL, y, subPaint)
        y += 64f * unit

        // 信息行
        val labelPaint = textPaint(Color.parseColor("#8AA0C0"), 30f * unit, false)
        val valuePaint = textPaint(Color.parseColor("#EAF6FF"), 38f * unit, true)
        val lineGap = 78f * unit
        val infoX2 = padL + 360f * unit
        drawKV(c, "方位角", "${look.azimuthDeg.toInt()}°", padL, infoX2, y, labelPaint, valuePaint)
        y += lineGap
        drawKV(c, "俯仰角", "${look.elevationDeg.toInt()}°", padL, infoX2, y, labelPaint, valuePaint)
        y += lineGap
        drawKV(c, "距离", "${look.rangeKm.toInt()} km", padL, infoX2, y, labelPaint, valuePaint)
        y += lineGap
        drawKV(c, "轨道高度", "${look.altitudeKm.toInt()} km", padL, infoX2, y, labelPaint, valuePaint)
        y += lineGap

        // 观测信息
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val locStr = if (observerLat != null && observerLon != null)
            String.format(Locale.CHINA, "%.2f°, %.2f°", observerLat, observerLon) else "未知"
        c.drawText("观测时间  $time", padL, y, labelPaint)
        y += 50f * unit
        c.drawText("观测位置  $locStr", padL, y, labelPaint)

        // 二维码（右下角）
        val qrSize = (260f * unit).toInt()
        val qr = qrBitmap(qrText, qrSize)
        val qrX = panelRight - qrSize - 48f * unit
        val qrY = panelBottom - qrSize - 48f * unit
        // 二维码白底卡片
        val cardPad = 14f * unit
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(
            RectF(qrX - cardPad, qrY - cardPad, qrX + qrSize + cardPad, qrY + qrSize + cardPad),
            12f * unit, 12f * unit, cardPaint
        )
        c.drawBitmap(qr, qrX, qrY, null)
        qr.recycle()
        val scanPaint = textPaint(Color.parseColor("#8AA0C0"), 24f * unit, false).apply {
            textAlign = Paint.Align.CENTER
        }
        c.drawText("扫码体验/下载", qrX + qrSize / 2f, qrY + qrSize + 38f * unit, scanPaint)

        return bmp
    }

    private fun drawKV(
        c: Canvas, label: String, value: String,
        labelX: Float, valueX: Float, y: Float,
        labelPaint: Paint, valuePaint: Paint
    ) {
        c.drawText(label, labelX, y, labelPaint)
        c.drawText(value, valueX, y, valuePaint)
    }

    private fun constellationColor(look: SatelliteLook): Int = when (look.satellite.constellation) {
        com.asiainfo.satellite.data.SatelliteConstellation.GW -> Color.parseColor("#2DE2FF")
        com.asiainfo.satellite.data.SatelliteConstellation.G60 -> Color.parseColor("#FF4ECD")
        com.asiainfo.satellite.data.SatelliteConstellation.TQ -> Color.parseColor("#4DFFB8")
    }

    private fun textPaint(c: Int, size: Float, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

    /** 用 zxing 生成二维码 Bitmap */
    fun qrBitmap(text: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val dark = Color.parseColor("#03040A")
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) dark else Color.WHITE)
            }
        }
        return bmp
    }

    /** 保存到 cacheDir/shared 并通过系统分享面板分享（用户可选微信→朋友圈） */
    fun shareBitmap(context: Context, bmp: Bitmap, caption: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "satellite_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享卫星时刻").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
