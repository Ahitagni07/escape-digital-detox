package com.example.escape

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

class LockOverlayController(
    private val context: Context
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var missionTitleView: TextView? = null
    private var missionInstructionView: TextView? = null
    private var missionSourceView: TextView? = null
    private var overlayProgress: TextView? = null
    private var actionButton: Button? = null
    private var exitButton: Button? = null
    private var emergencyHint: TextView? = null

    fun show(
        lockMode: String,
        missionActive: Boolean,
        missionSeconds: Int,
        missionTargetSeconds: Int,
        steps: Int,
        minSteps: Int,
        stepSensorAvailable: Boolean,
        missionTitle: String,
        missionInstruction: String,
        missionSource: String,
        missionGenerating: Boolean,
        missionProofCode: String
    ) {
        if (!Settings.canDrawOverlays(context)) return
        if (overlayView == null) createOverlay()

        val evening = lockMode == EscapeKeys.LOCK_MODE_EVENING

        missionTitleView?.text = missionTitle
        missionInstructionView?.text = missionInstruction
        missionSourceView?.text = when {
            missionGenerating -> "🤖 Gemma is preparing local missions…"
            missionSource == "gemma-local" -> "🤖 Generated locally by Gemma"
            else -> "🌱 Built-in offline mission"
        }

        overlayProgress?.text = buildString {
            append("🔒 SOCIAL ACCESS LOCKED\n\n")
            if (evening) {
                append("Create a nature-themed note on paper for the required time.\n")
                append("Include at least 20 words.\n")
                append("Add these proof words clearly: ")
                append(missionProofCode.ifBlank { "See ESCAPE" })
                append("\nThen photograph your paper in ESCAPE.")
            } else {
                append("Step 1: take a real walk (or an opt-in weekend bike ride).\n")
                append("Step 2: find the requested nature subject.\n")
                append("Step 3: capture a NEW camera photo in ESCAPE.")
            }
        }
        actionButton?.text = "OPEN ESCAPE • START QUEST"
        emergencyHint?.text = "Need to leave? Tap GO HOME below. No need to complete a mission just to exit an app."
    }

    fun hide() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }

        overlayView = null
        missionTitleView = null
        missionInstructionView = null
        missionSourceView = null
        overlayProgress = null
        actionButton = null
        exitButton = null
        emergencyHint = null
    }

    private fun createOverlay() {
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (30 * density).toInt(),
                (30 * density).toInt(),
                (30 * density).toInt(),
                (30 * density).toInt()
            )
            setBackgroundColor(Color.rgb(19, 31, 21))
            isClickable = true
            isFocusable = false
        }

        val leaf = TextView(context).apply {
            text = "🌱"
            textSize = 60f
            gravity = Gravity.CENTER
        }

        val eyebrow = TextView(context).apply {
            text = "EARN YOUR SCROLL"
            textSize = 14f
            setTextColor(Color.rgb(178, 218, 184))
            gravity = Gravity.CENTER
        }

        missionTitleView = TextView(context).apply {
            text = "Mission ready"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }

        missionInstructionView = TextView(context).apply {
            text = "Complete a concrete offline challenge, then submit photo proof."
            textSize = 17f
            setTextColor(Color.rgb(222, 235, 224))
            gravity = Gravity.CENTER
        }

        missionSourceView = TextView(context).apply {
            text = "🌱 Built-in offline mission"
            textSize = 12f
            setTextColor(Color.rgb(178, 218, 184))
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        overlayProgress = TextView(context).apply {
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, (26 * density).toInt(), 0, (24 * density).toInt())
        }

        actionButton = Button(context).apply {
            text = "OPEN ESCAPE"
            textSize = 15f
            setOnClickListener {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    context.startActivity(launch)
                }
            }
        }

        exitButton = Button(context).apply {
            text = "← GO HOME / LEAVE APP"
            textSize = 14f
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
        }

        emergencyHint = TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.rgb(180, 190, 182))
            gravity = Gravity.CENTER
        }

        root.addView(leaf)
        root.addView(eyebrow)
        root.addView(missionTitleView)
        root.addView(missionInstructionView)
        root.addView(missionSourceView)
        root.addView(overlayProgress)
        root.addView(actionButton)
        root.addView(exitButton)
        root.addView(emergencyHint)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(root, params)
            overlayView = root
        } catch (_: Throwable) {
            hide()
        }
    }

    private fun formatTime(seconds: Int): String =
        String.format("%02d:%02d", seconds / 60, seconds % 60)
}
