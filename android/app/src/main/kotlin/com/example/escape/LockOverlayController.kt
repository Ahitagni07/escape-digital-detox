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
        missionGenerating: Boolean
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

        if (!missionActive) {
            overlayProgress?.text = buildString {
                append("🔒 SOCIAL ACCESS LOCKED\n\n")
                append("Complete this mission first to earn your social-media window.\n")
                append("Open ESCAPE and tap Start Mission.")
            }
            actionButton?.text = "OPEN ESCAPE"
            emergencyHint?.text = "\nThe same mission stays active; ESCAPE will not nag you with a new one every time."
        } else {
            val remaining = max(0, missionTargetSeconds - missionSeconds)

            overlayProgress?.text = buildString {
                append(if (evening) "🌙 SCREEN-FREE TIME\n" else "🌱 MISSION IN PROGRESS\n")
                append(formatTime(missionSeconds))
                append(" / ")
                append(formatTime(missionTargetSeconds))
                append("\n")

                if (!evening && stepSensorAvailable) {
                    append("👟 $steps / $minSteps steps\n")
                }

                append("\nRemaining: ")
                append(formatTime(remaining))
            }
            actionButton?.text = "PUT PHONE AWAY"
            emergencyHint?.text = "\nUrgent? Open ESCAPE and use Emergency Unlock."
        }
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
            isFocusable = true
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
            text = "Complete a screen-free mission first."
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
        root.addView(emergencyHint)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
