package dev.bananaumai.practices.service.coroutine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

fun <E> ReceiveChannel<E>.throttleLast(
    interval: Long,
    context: CoroutineContext = Dispatchers.Default
): ReceiveChannel<E> = with(CoroutineScope(context)) {
    produce(coroutineContext) {
        val tag = this.javaClass.name
        var nextTime = 0L
        consumeEach {
            val currentTime = System.currentTimeMillis()
            if (currentTime > nextTime) {
                nextTime = currentTime + interval
                Log.d(tag, "[send] $it (${Thread.currentThread().name})")
                send(it)
            } else {
                Log.d(tag, "[drop] $it (${Thread.currentThread().name})")
            }
        }
    }
}

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
        val channel = channel.throttleLast(100, coroutineContext)

        while (true) {
            if (channel.isClosedForReceive) {
                break
            }

            val e = channel.receive()

            Log.d(tag, "[process] $e (${Thread.currentThread().name})")

            if (rand.nextInt(10) % 2 == 0) {
                delay(150)
            } else {
                delay(50)
            }
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

data class AccelerometerEvent(
    val timestampe: Long,
    val values: List<Float>,
    val createdAt: Instant = Instant.now()
)

object Accelerometer {
    private val tag = this.javaClass.name
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    fun start(manager: SensorManager, channel: SendChannel<Any>) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                val ev = AccelerometerEvent(event.timestamp / 1_000_000, event.values.toList())
                Log.d(tag, "[before] $ev (${Thread.currentThread().name})")

                scope.launch {
                    channel.send(ev)
                    Log.d(tag, "[after] $ev (${Thread.currentThread().name})")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        HandlerThread("AccelerometerHandlerThread", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            val handler = Handler(looper)
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, handler)
        }
    }
}