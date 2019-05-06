package dev.bananaumai.practices.background.rx

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
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
    private val bridgeStream: PublishProcessor<Any> = PublishProcessor.create<Any>()

    inner class LocalBinder : Binder() {
        fun getService() = this@DataEmitter
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun startEmit(processor: PublishProcessor<Any>) {
        if (running) return

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = Accelerometer(sensorManager)

        val locationEmitter = LocationEmitter(this)

        subscription = listOf(accelerometer, locationEmitter)
            .map { it.start().run { toStream() } }
            .reduce { acc, stream -> acc.mergeWith(stream) }
            .mergeWith(bridgeStream)
            .subscribe { processor.onNext(it) }

        running = true
    }

    fun stopEmit() {
        if (running) {
            subscription.dispose()
        }
    }

    fun send(a: Any) {
        bridgeStream.onNext(OutsideEvent(a))
    }

    private data class OutsideEvent(val a: Any)
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

data class LocationEvent(val lat: Double, val lon: Double, val speed: Float)

fun Location.toLocationEvent() = LocationEvent(latitude, longitude, speed)

class LocationEmitter(val context: Context) : Emittable {
    private val tag = this.javaClass.name
    private val processor = PublishProcessor.create<Any>()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationSettingsClient = LocationServices.getSettingsClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            if (locationResult == null) return

            val evt = locationResult.lastLocation.toLocationEvent()
            Log.d(tag, "[before] $evt (${Thread.currentThread().name})")
            processor.onNext(evt)
            Log.d(tag, "[after] $evt (${Thread.currentThread().name})")
        }
    }

    override fun start(): LocationEmitter {
        val builder = LocationSettingsRequest.Builder()
        val request = createLocationRequest()

        locationSettingsClient
            .checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Log.d(tag, "$it")
                try {
                    HandlerThread("LocationEmitterThread", Process.THREAD_PRIORITY_BACKGROUND).apply {
                        fusedLocationClient.requestLocationUpdates(request, locationCallback, looper)
                    }
                } catch (e: SecurityException) {
                    throw e
                }
            }
            .addOnFailureListener {
                throw it
            }

        return this
    }

    override fun toStream(): Flowable<Any> = processor

    private fun createLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest.create() ?: throw RuntimeException("couldn't create locatilon request")

        locationRequest.interval = 1000
        locationRequest.fastestInterval = 1000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        return locationRequest
    }
}

class RemoteService : Service() {
    private val binder: IRemoteService.Stub = object : IRemoteService.Stub() {
        override fun send(n: Int): Int {
            this@RemoteService.send(n)
            return n
        }
    }

    private var emitter: DataEmitter? =  null

    private val emitterConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DataEmitter.LocalBinder
            emitter = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            emitter = null
        }
    }

    @Synchronized fun send(a: Any) {
        emitter?.send(a)
    }

    override fun onCreate() {
        super.onCreate()

        Intent(this, DataEmitter::class.java).also {
            bindService(it, emitterConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}