package com.example.escape

data class SupportedApp(
    val name: String,
    val packageName: String,
    val browser: Boolean
)

object SupportedApps {
    val all = listOf(
        SupportedApp("Instagram", "com.instagram.android", false),
        SupportedApp("YouTube", "com.google.android.youtube", false),
        SupportedApp("Facebook", "com.facebook.katana", false),
        SupportedApp("X", "com.twitter.android", false),
        SupportedApp("TikTok", "com.zhiliaoapp.musically", false),

        SupportedApp("Chrome", "com.android.chrome", true),
        SupportedApp("Firefox", "org.mozilla.firefox", true),
        SupportedApp("Brave", "com.brave.browser", true),
        SupportedApp("Microsoft Edge", "com.microsoft.emmx", true),
        SupportedApp(
            "Samsung Internet",
            "com.sec.android.app.sbrowser",
            true
        )
    )
}
