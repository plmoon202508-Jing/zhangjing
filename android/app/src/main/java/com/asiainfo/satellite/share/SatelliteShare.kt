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

    // 邮票区高度 = 底图宽度 * 该比例（保证不同尺寸底图比例一致）
    private const val STAMP_RATIO = 0.62f

    /** 邮票区在最终图中的顶边 y（底图正下方） */
    private fun stampTop(w: Int, h: Int): Float = (h - w * STAMP_RATIO)

    /**
     * 合成分享图：底图原样在上，下方扩展出一块「邮票样式」白色区域，
     * 卫星信息排在邮票区内（不遮挡底图）。二维码区域预留，调用 [drawQrOnto] 填充。
     */
    fun buildShareBitmap(
        context: Context,
        look: SatelliteLook,
        observerLat: Double?,
        observerLon: Double?,
        userName: String? = null,
        qr: Bitmap? = null
    ): Bitmap {
        val template = loadTemplate(context)
        val w = template.width
        val stampH = (w * STAMP_RATIO).toInt()
        val h = template.height + stampH

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val unit = w / 1080f

        // 1) 顶部背景（邮票区外的底色）+ 底图
        c.drawColor(Color.parseColor("#03040A"))
        c.drawBitmap(template, 0f, 0f, null)
        template.recycle()

        // 2) 邮票区
        val top = stampTop(w, h)
        drawStamp(c, look, observerLat, observerLon, userName, w.toFloat(), top, h.toFloat(), unit)

        // 3) 二维码（如已生成）
        if (qr != null) drawQrOnto(bmp, qr)

        return bmp
    }

    /** 绘制邮票样式白色区域 + 卫星信息 + 预留二维码位 */
    private fun drawStamp(
        c: Canvas,
        look: SatelliteLook,
        observerLat: Double?,
        observerLon: Double?,
        userName: String?,
        w: Float, stampTop: Float, h: Float,
        unit: Float
    ) {
        val margin = 56f * unit
        val left = margin
        val right = w - margin
        val top = stampTop + margin
        val bottom = h - margin

        // 白色邮票底
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val rect = RectF(left, top, right, bottom)
        c.drawRect(rect, white)

        // 邮票锯齿（沿四边画底色小半圆，形成打孔效果）
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#03040A") }
        val tooth = 18f * unit
        var x = left
        while (x <= right) {
            c.drawCircle(x, top, tooth, bg)
            c.drawCircle(x, bottom, tooth, bg)
            x += tooth * 2
        }
        var yy = top
        while (yy <= bottom) {
            c.drawCircle(left, yy, tooth, bg)
            c.drawCircle(right, yy, tooth, bg)
            yy += tooth * 2
        }

        // 内描边（虚线感）
        val inset = 22f * unit
        val innerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * unit
            color = Color.parseColor("#1F3B5B")
        }
        c.drawRect(RectF(left + inset, top + inset, right - inset, bottom - inset), innerBorder)

        val accent = constellationColor(look)
        val padL = left + inset + 36f * unit
        var y = top + inset + 70f * unit

        // 顶部标识
        c.drawText("亚信卫星时刻 · ASIAINFO SATELLITE MOMENT",
            padL, y, textPaint(accent, 28f * unit, true))
        y += 86f * unit

        // 卫星名称 + 星座 + 体验人（一排显示）
        c.drawText(look.satellite.name, padL, y, textPaint(Color.parseColor("#0B1A33"), 60f * unit, true))
        val constellationText = look.satellite.constellation.displayName
        val userNameText = userName?.let { " · 体验人：$it" } ?: ""
        c.drawText("$constellationText$userNameText", padL + 400f * unit, y, textPaint(accent, 28f * unit, true))
        y += 70f * unit

        // 卫星 6 项信息（两列呈现：标签在上、数值在下）
        val labelPaint = textPaint(Color.parseColor("#6B7C99"), 26f * unit, false)
        val valuePaint = textPaint(Color.parseColor("#0B1A33"), 34f * unit, true)
        val valuePaintSm = textPaint(Color.parseColor("#0B1A33"), 28f * unit, true)
        val col1X = padL
        val col2X = padL + 330f * unit
        val rowGap = 104f * unit
        val labelToValue = 40f * unit

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val locStr = if (observerLat != null && observerLon != null)
            String.format(Locale.CHINA, "%.2f°, %.2f°", observerLat, observerLon) else "未知"

        // 第一行
        drawCell(c, "方位角", "${look.azimuthDeg.toInt()}°", col1X, y, labelPaint, valuePaint, labelToValue)
        drawCell(c, "俯仰角", "${look.elevationDeg.toInt()}°", col2X, y, labelPaint, valuePaint, labelToValue)
        y += rowGap
        // 第二行
        drawCell(c, "斜距", "${look.rangeKm.toInt()} km", col1X, y, labelPaint, valuePaint, labelToValue)
        drawCell(c, "轨道高度", "${look.altitudeKm.toInt()} km", col2X, y, labelPaint, valuePaint, labelToValue)
        y += rowGap
        // 第三行
        drawCell(c, "观测时间", time, col1X, y, labelPaint, valuePaintSm, labelToValue)
        drawCell(c, "观测位置", locStr, col2X, y, labelPaint, valuePaintSm, labelToValue)
        y += rowGap

        // 二维码占位框 + 文案（右下角）
        val qrRect = qrRectFor(w.toInt(), h.toInt())
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f * unit; color = Color.parseColor("#C4D2E6")
        }
        c.drawRect(qrRect, frame)
        c.drawText("扫码查看/下载",
            qrRect.centerX(), qrRect.bottom + 42f * unit,
            textPaint(Color.parseColor("#6B7C99"), 26f * unit, false).apply { textAlign = Paint.Align.CENTER })
    }

    /** 二维码在最终图中的矩形（邮票区右下角，build 与填充共用，保证一致） */
    private fun qrRectFor(w: Int, h: Int): RectF {
        val unit = w / 1080f
        val margin = 56f * unit
        val inset = 22f * unit
        val bottom = h - margin - inset - 60f * unit
        val right = w - margin - inset - 36f * unit
        val size = 220f * unit
        return RectF(right - size, bottom - size, right, bottom)
    }

    /** 将二维码绘制进预留位置（上传拿到 URL 后调用） */
    fun drawQrOnto(bmp: Bitmap, qr: Bitmap) {
        val c = Canvas(bmp)
        val rect = qrRectFor(bmp.width, bmp.height)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRect(rect, white)
        val dst = RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f)
        c.drawBitmap(qr, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** 两列布局单元格：标签在上、数值在下 */
    private fun drawCell(
        c: Canvas, label: String, value: String,
        x: Float, y: Float,
        labelPaint: Paint, valuePaint: Paint, labelToValue: Float
    ) {
        c.drawText(label, x, y, labelPaint)
        c.drawText(value, x, y + labelToValue, valuePaint)
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

    /** Bitmap → PNG 字节 */
    fun bitmapToPng(bmp: Bitmap): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
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
