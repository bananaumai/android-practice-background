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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
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
        channel.consumeEach {
            Log.d(tag, "${Thread.currentThread().name}: $it")
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
        AccelerometerEventEmitter.start(sensorManager, processor)
    }
}

class AccelerometerEvent(private val event: SensorEvent) {
    override fun toString() =
        "${this.javaClass.name} { timestamp: ${event.timestamp}, values: ${event.values.joinToString(",", "[", "]")} }"
}

object AccelerometerEventEmitter {
    private val tag = this.javaClass.name

    fun start(manager: SensorManager, channel: Channel<Any>) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                Log.d(tag, "${Thread.currentThread().name}: emit event ${event.timestamp}")
                runBlocking {
                    channel.send(AccelerometerEvent(event))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }
}