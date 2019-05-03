package dev.bananaumai.practices.background.rx

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.reactivex.processors.PublishProcessor

class MainActivity : AppCompatActivity() {
    private val tag = this.javaClass.name

    private val processor = PublishProcessor.create<Any>()

    private var boundEmitter = false
    private var emitter: DataEmitter? = null
    private val emitterConnection = object : ServiceConnection {
        private val tag = this.javaClass.name

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "onServiceConnected")
            val binder = service as DataEmitter.LocalBinder
            emitter = binder.getService()
            emitter?.startEmit(processor)
            boundEmitter = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundEmitter = false
        }
    }

    private var boundHandler = false
    private var handler: DataHandler? = null
    private val handlerConnection = object : ServiceConnection {
        private val tag = this.javaClass.name

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "onServiceConnected")
            val binder = service as DataHandler.LocalBinder
            handler = binder.getService()
            handler?.startHandle(processor)
            boundHandler = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundHandler = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)
            return
        }

        Intent(this, DataEmitter::class.java).also {
            bindService(it, emitterConnection, Context.BIND_AUTO_CREATE)
        }

        Intent(this, DataHandler::class.java).also {
            bindService(it, handlerConnection, Context.BIND_AUTO_CREATE)
        }

    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")

        super.onDestroy()
    }
}
