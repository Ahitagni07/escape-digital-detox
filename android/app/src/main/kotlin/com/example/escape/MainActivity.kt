package com.example.escape

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private lateinit var methodChannelHandler: EscapeMethodChannelHandler

    override fun configureFlutterEngine(
        flutterEngine: FlutterEngine
    ) {
        super.configureFlutterEngine(flutterEngine)

        methodChannelHandler = EscapeMethodChannelHandler(this)
        methodChannelHandler.register(
            flutterEngine.dartExecutor.binaryMessenger
        )
    }

    @Deprecated("Deprecated in Android API; needed for the simple file-picker bridge.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (
            ::methodChannelHandler.isInitialized &&
            methodChannelHandler.onActivityResult(
                requestCode,
                resultCode,
                data
            )
        ) {
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
