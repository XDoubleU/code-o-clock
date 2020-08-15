package com.xdoubleu.xwatchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.*


/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

class MyWatchFace : CanvasWatchFaceService(){

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false

        private lateinit var textWhite: Paint
        private lateinit var textRed: Paint
        private lateinit var textBlue: Paint
        private lateinit var textGreen: Paint
        private lateinit var textMagenta: Paint

        /*WatchFace Data*/

        /*Time*/
        private var hours = " "
        private var minutes = " "
        private var seconds = " "
        private var gmtOffset = 0

        /*Date*/
        private val days = arrayOf("monday","tuesday","wednesday","thursday","friday","saturday","sunday")
        private var dayOfWeek = " "
        private var day = " "
        private var month = " "
        private var year = " "

        /*Battery*/
        private var percentage = 0
        private var chargingStatus = false


        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()

                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            mCalendar = Calendar.getInstance()

            initializeTextColors()
        }

        private fun initializeTextColors() {
            textWhite = Paint().apply {
                color = Color.WHITE
                typeface = Typeface.MONOSPACE
                textSize = 25F
                isAntiAlias = true
            }

            textRed = Paint().apply {
                color = Color.rgb(203, 76, 22)
                typeface = Typeface.MONOSPACE
                textSize = 25F
                isAntiAlias = true
            }

            textBlue = Paint().apply {
                color = Color.rgb(38, 138, 210)
                textSize = 25F
                isAntiAlias = true
            }

            textGreen = Paint().apply {
                color = Color.rgb(133, 153, 0)
                textSize = 25F
                isAntiAlias = true
            }

            textMagenta = Paint().apply {
                color = Color.rgb(211, 54, 130)
                textSize = 25F
                isAntiAlias = true
            }

            textBlue.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textGreen.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textMagenta.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                textWhite.alpha = if (inMuteMode) 100 else 255
                textBlue.alpha = if (inMuteMode) 100 else 255
                textGreen.alpha = if (inMuteMode) 100 else 255
                textRed.alpha = if (inMuteMode) 100 else 255
                textMagenta.alpha = if (inMuteMode) 100 else 255
                invalidate()
            }

        }

        private fun time(){
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            val mTimeZone = mCalendar.timeZone
            val twoDigitsFormat = DecimalFormat("00")

            hours = twoDigitsFormat.format(mCalendar.get(Calendar.HOUR_OF_DAY))
            minutes = twoDigitsFormat.format(mCalendar.get(Calendar.MINUTE))
            seconds = twoDigitsFormat.format(mCalendar.get(Calendar.SECOND))

            dayOfWeek = days[mCalendar.get(Calendar.DAY_OF_WEEK)-2]
            day = twoDigitsFormat.format(mCalendar.get(Calendar.DAY_OF_MONTH))
            month = twoDigitsFormat.format(mCalendar.get(Calendar.MONTH))
            year = mCalendar.get(Calendar.YEAR).toString()

            gmtOffset = mTimeZone.getOffset(mCalendar.timeInMillis)/3600000
        }

        private fun getBatteryPercentage() : Int{
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            return batteryIntent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        }

        private fun isBatteryCharging() : Boolean{
            val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            /*Background*/
            canvas.drawColor(Color.BLACK)

            drawStaticText(canvas)
            drawDynamicText(canvas)
        }

        private fun drawStaticText(canvas: Canvas){
            canvas.drawText(
                "class",
                70f,
                75f,
                textRed)

            canvas.drawText(
                "watchFace{",
                160f,
                75f,
                textWhite)

            canvas.drawText(
                "time =",
                85f,
                115f,
                textWhite)

            canvas.drawText(
                "timezone =",
                85f,
                155f,
                textWhite)

            canvas.drawText(
                "day =",
                85f,
                195f,
                textWhite)

            canvas.drawText(
                "date =",
                85f,
                235f,
                textWhite)

            canvas.drawText(
                "battery =",
                85f,
                275f,
                textWhite)

            canvas.drawText(
                "charging =",
                85f,
                315f,
                textWhite)

            canvas.drawText(
                "}",
                70f,
                355f,
                textWhite)
        }
        private fun drawDynamicText(canvas: Canvas){
            /*Data*/
            time()
            percentage = getBatteryPercentage()
            chargingStatus = isBatteryCharging()


            canvas.drawText(
                "$hours:$minutes:$seconds",
                190f,
                115f,
                textBlue)

            canvas.drawText(
                "GMT+$gmtOffset",
                250f,
                155f,
                textBlue)

            canvas.drawText(
                dayOfWeek,
                175f,
                195f,
                textGreen)

            canvas.drawText(
                "$day/$month/$year",
                190f,
                235f,
                textGreen)

            canvas.drawText(
                "$percentage%",
                235f,
                275f,
                textMagenta)

            canvas.drawText(
                "$chargingStatus",
                250f,
                315f,
                textMagenta)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}


