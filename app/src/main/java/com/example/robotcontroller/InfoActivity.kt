
package com.example.robotcontroller

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.webkit.WebChromeClient

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        // Nút quay lại
        val btnBack: Button = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // Quay về MainActivity
        }
        // WebView hiển thị video
        val webView: WebView = findViewById(R.id.webViewVideo)
        // Bật JavaScript
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webChromeClient = WebChromeClient()  // xem video full m

        // Đường dẫn video YouTube (embed)
        val html = """
            <html>
            <body style="margin:0;padding:0;">
                <iframe width="100%" height="100%" 
                    src="https://www.youtube.com/embed/9XlRo5uRoxo"  
                    frameborder="0" allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            null,
            html,
            "text/html",
            "utf-8",
            null
        )


        // Load dữ liệu vào WebView
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
