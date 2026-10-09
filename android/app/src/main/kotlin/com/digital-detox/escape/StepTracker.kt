package com.example.escape

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.max

class StepTracker(
    context: Context,
    private val permissionHelper: PermissionHelper
) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

    private val stepSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var latestStepCount = -1f
    private var baseline = -1f
    private var missionActive = false

    val available: Boolean
        get() = stepSensor != null

    fun start() {
        val sensor = stepSensor ?: return

        if (!permissionHelper.hasActivityRecognitionPermission()) {
            return
        }

        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun startMission() {
        missionActive = true
        baseline = latestStepCount
    }

    fun finishMission() {
        missionActive = false
        baseline = -1f
    }

    fun stepsSinceMissionStart(): Int {
        if (!available) return 0
        if (baseline < 0f || latestStepCount < 0f) return 0

        return max(
            0,
            (latestStepCount - baseline).toInt()
        )
    }

    fun hasBaseline(): Boolean =
        !available || baseline >= 0f

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) {
            return
        }

        latestStepCount = event.values.firstOrNull() ?: return

        if (missionActive && baseline < 0f) {
            baseline = latestStepCount
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) = Unit
}
