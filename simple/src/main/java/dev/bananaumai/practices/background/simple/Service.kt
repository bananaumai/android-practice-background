package dev.bananaumai.practices.background.simple

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import java.io.Serializable
import kotlin.random.Random

class DataProcessor : Service() {
    private val tag = this.javaClass.name
    private lateinit var serviceHandler: ServiceHandler
    private val rand = Random.Default

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message?) {
            val intent = msg?.obj as Intent?

            val i = rand.nextInt(10)

            if (i % 4 == 0) {
                Thread.sleep(10)
            }

            if (intent != null) {
                val data = intent.getStringExtra("data")
                Log.d(tag, data)
            }

            Log.d(tag, "isIdle?: ${looper.queue.isIdle}")

            val printer = { x: String? ->
                Log.d(tag, x)
                Unit
            }

            if (!looper.queue.isIdle) {
                looper.dump(printer, "hoge")
            }

            super.handleMessage(msg)
        }
    }

    override fun onCreate() {
        HandlerThread("DataProcessorHandlerThread", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceHandler = ServiceHandler(looper)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceHandler.obtainMessage().also { msg ->
            msg.arg1 = startId
            msg.obj = intent
            serviceHandler.sendMessage(msg)
        }

        return START_STICKY
    }
}

class DataEmitter : Service() {
    private val tag = this.javaClass.name

    private val binder: LocalBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@DataEmitter
    }

    override fun onCreate() {
        Log.d(tag, "onCreate")

        super.onCreate()

        val dispatcher = { data: Any ->
            Intent(this, DataProcessor::class.java).also {
                it.putExtra("data", data.toString())
                startService(it)
            }
            Unit
        }

        val accelerometer = Accelerometer(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
        accelerometer.listen(dispatcher)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(tag, "onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")

        super.onDestroy()
    }
}

interface EventListener {
    fun listen(callback: (Any) -> Unit)
}

class Accelerometer(private val manager: SensorManager) : EventListener {
    inner class AccelerometerEvent(private val event: SensorEvent) : Serializable {
        override fun toString() =
            "${this.javaClass.name} { timestamp: ${event.timestamp}, values: ${event.values.joinToString(",", "[", "]")} }"
    }

    override fun listen(callback: (Any) -> Unit) {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.getType() != Sensor.TYPE_ACCELEROMETER) {
                    return
                }

                callback(AccelerometerEvent(event))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
}