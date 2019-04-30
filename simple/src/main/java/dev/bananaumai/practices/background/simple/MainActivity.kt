package dev.bananaumai.practices.background.simple

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log

class MainActivity : AppCompatActivity() {
    private val tag = this.javaClass.name

    private var boundEmitter = false
    private lateinit var emitter: DataEmitter
    private val emitterConnection = object : ServiceConnection {
        private val tag = this.javaClass.name

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "onServiceConnected")
            val binder = service as DataEmitter.LocalBinder
            emitter = binder.getService()
            boundEmitter = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundEmitter = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Intent(this, DataEmitter::class.java).also {
            Log.d(tag, "before bindService")
            bindService(it, emitterConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")

        super.onDestroy()
    }
}
