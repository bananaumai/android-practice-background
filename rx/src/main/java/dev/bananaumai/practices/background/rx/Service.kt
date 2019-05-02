package dev.bananaumai.practices.background.rx

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import io.reactivex.Flowable
import kotlin.random.Random
import io.reactivex.processors.*
import io.reactivex.schedulers.Schedulers
import java.time.Instant
import java.util.concurrent.TimeUnit

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
            .throttleLast(100, TimeUnit.MILLISECONDS)
            .subscribe({ event ->
                Log.d(tag, "$event (${Thread.currentThread().name})")
                if (rand.nextInt(10) % 2 == 0) {
                    Thread.sleep(40)
                }
            }, { error ->
                Log.d(tag, error.message)
            })
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

    fun startEmit(processor: PublishProcessor<Any>) {
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

    fun start(manager: SensorManager, processor: PublishProcessor<Any>) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                val evt = AccelerometerEvent(event.timestamp / 1_000_000, event.values.toList())
                Log.d(tag, "$evt (${Thread.currentThread().name}) - before")
                processor.onNext(evt)
                Log.d(tag, "$evt (${Thread.currentThread().name}) - after")
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }
}