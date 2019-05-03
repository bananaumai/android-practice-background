package dev.bananaumai.practices.background.rx

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
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class DataHandler : Service() {
    private val tag = this.javaClass.name
    private val rand = Random.Default
    private val binder: LocalBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@DataHandler
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun startHandle(stream: Flowable<Any>) {
        stream
            .observeOn(Schedulers.computation())
            .onBackpressureLatest()
            .subscribe({ event ->
                val duration = 40 * rand.nextLong(5)
                Log.d(tag, "[wait $duration ms] $event (${Thread.currentThread().name})")
                Thread.sleep(duration)
            }, { error ->
                Log.d(tag, error.message)
            })
    }
}

class DataEmitter : Service() {
    private val binder: LocalBinder = LocalBinder()
    private lateinit var subscription: Disposable
    private var running = false

    inner class LocalBinder : Binder() {
        fun getService() = this@DataEmitter
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun startEmit(processor: PublishProcessor<Any>) {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = Accelerometer(sensorManager)

        subscription = listOf(accelerometer)
            .map { it.start().run { toStream() } }
            .reduce { acc, stream -> acc.mergeWith(stream) }
            .subscribe { processor.onNext(it) }

        running = true
    }

    fun stopEmit() {
        if (running) {
            subscription.dispose()
        }
    }
}

interface Emittable {
    fun start(): Emittable
    fun toStream(): Flowable<Any>
}

data class AccelerometerEvent(
    val timestampe: Long,
    val values: List<Float>,
    val createdAt: Instant = Instant.now()
)

class Accelerometer(val sensorManager: SensorManager) : Emittable {
    private val tag = this.javaClass.name
    private val processor = PublishProcessor.create<Any>()

    override fun start(): Accelerometer {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                val evt = AccelerometerEvent(event.timestamp / 1_000_000, event.values.toList())
                Log.d(tag, "[before] $evt (${Thread.currentThread().name})")
                processor.onNext(evt)
                Log.d(tag, "[after] $evt (${Thread.currentThread().name})")
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        HandlerThread("AccelerometerHandlerThread", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, Handler(looper))
        }

        return this
    }

    override fun toStream(): Flowable<Any> = processor.throttleLast(100, TimeUnit.MILLISECONDS)
}