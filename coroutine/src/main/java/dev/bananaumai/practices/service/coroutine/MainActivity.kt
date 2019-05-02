package dev.bananaumai.practices.service.coroutine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.channels.Channel

class MainActivity : AppCompatActivity() {
    private val tag = this.javaClass.name

    private val channel = Channel<Any>(100)

    private var boundEmitter = false
    private var emitter: DataEmitter? = null
    private val emitterConnection = object : ServiceConnection {
        private val tag = this.javaClass.name

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "onServiceConnected")
            val binder = service as DataEmitter.LocalBinder
            emitter = binder.getService()
            emitter?.startEmit(channel)
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
            Log.d(tag, "onServiceConnected - start")
            val binder = service as DataHandler.LocalBinder
            handler = binder.getService()
            handler?.startHandle(channel)
            boundHandler = true
            Log.d(tag, "onServiceConnected - end")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundHandler = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
