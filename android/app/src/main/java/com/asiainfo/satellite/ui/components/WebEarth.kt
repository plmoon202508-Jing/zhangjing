package com.asiainfo.satellite.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 内嵌已开发的 Web 3D 地球（Three.js + 真实 TLE + 点击查看/改名）。
 * 资源已打包进 assets/web，three.js 与 CelesTrak 数据走网络加载。
 *
 * @param hash 深链接，例如 "constellation" / "ar"
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebEarth(
    modifier: Modifier = Modifier,
    hash: String = "constellation"
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Log.d("WebEarth", "Creating WebView with hash: $hash")
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.BLACK)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("WebEarth", "Page finished: $url")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d("WebEarth-Console", "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}")
                        }
                        return true
                    }
                }

                val url = "file:///android_asset/web/index.html?embed=1#$hash"
                Log.d("WebEarth", "Loading URL: $url")
                loadUrl(url)
            }
        }
    )
}
