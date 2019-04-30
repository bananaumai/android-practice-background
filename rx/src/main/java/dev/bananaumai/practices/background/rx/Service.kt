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
import io.reactivex.FlowableSubscriber
import kotlin.random.Random
import io.reactivex.processors.*
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscription

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

    fun startHandle(processor: Flowable<Any>) {
        val subscriber = object : FlowableSubscriber<Any> {
            override fun onSubscribe(s: Subscription) {
                Log.d(tag, "${Thread.currentThread().name}: onSubscribe")
            }

            override fun onNext(t: Any?) {
                Log.d(tag, "${Thread.currentThread().name}: ${t.toString()}")
            }

            override fun onComplete() {
                Log.d(tag, "${Thread.currentThread().name}: onComplete")
            }

            override fun onError(t: Throwable?) {
                if (t != null) {
                    Log.d(tag, t.message)
                    throw t
                }
            }
        }

        processor
            .observeOn(Schedulers.computation())
            .onBackpressureLatest()
            .subscribe({ data ->
                if (rand.nextInt(10) % 2 == 0) {
                    Thread.sleep(40)
                }
                Log.d(tag, "${Thread.currentThread().name}: ${data.toString()}")
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
        AccelerometerEventEmitter.start(sensorManager, processor)
    }
}

class AccelerometerEvent(private val event: SensorEvent) {
    override fun toString() =
        "${this.javaClass.name} { timestamp: ${event.timestamp}, values: ${event.values.joinToString(",", "[", "]")} }"
}

object AccelerometerEventEmitter {
    val tag = this.javaClass.name

    fun start(manager: SensorManager, processor: PublishProcessor<Any>) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                Log.d(tag, "${Thread.currentThread().name}: emit event ${event.timestamp}")
                processor.onNext(AccelerometerEvent(event))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }
}