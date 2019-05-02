package dev.bananaumai.practices.service.coroutine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.text.DateFormat
import java.util.*
import kotlin.random.Random

class DataHandler : Service() {
    private val tag = this.javaClass.name
    private val rand = Random.Default
    private val binder: LocalBinder = LocalBinder()
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    inner class LocalBinder : Binder() {
        fun getService() = this@DataHandler
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun startHandle(channel: ReceiveChannel<Any>) = scope.launch {
        // consumeEach is Experimental API
//        channel.consumeEach { event ->
//            val delayed = rand.nextInt(10) % 2 == 0
//            if (delayed) {
//                delay(200)
//            }
//            Log.d(tag, "$event (${Thread.currentThread().name}) delayed? : $delayed")
//        }

        for (event in channel) {
            val delayed = rand.nextInt(10) % 2 == 0
            if (delayed) {
                delay(1000)
            }
            Log.d(tag, "$event (${Thread.currentThread().name}) : delayed = $delayed")
        }
    }
}

class DataEmitter : Service() {
    private val binder: LocalBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@DataEmitter
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun startEmit(processor: Channel<Any>) {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        Accelerometer.start(sensorManager, processor)
    }
}

data class AccelerometerEvent(val timestampe: Long, val values: List<Float>)

object Accelerometer {
    private val tag = this.javaClass.name
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val random = Random.Default

    fun start(manager: SensorManager, channel: SendChannel<Any>) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                scope.launch {
                //runBlocking {
                    val _event = AccelerometerEvent(event.timestamp, event.values.toList())
                    Log.d(tag, "$_event (${Thread.currentThread().name}) - before")
                    channel.send(_event)
                    Log.d(tag, "$_event (${Thread.currentThread().name}) - after")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }
}