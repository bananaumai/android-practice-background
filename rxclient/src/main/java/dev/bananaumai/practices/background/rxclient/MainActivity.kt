package dev.bananaumai.practices.background.rxclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.util.Log
import dev.bananaumai.practices.background.rx.IRemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val tag = this.javaClass.name

    private var remoteServiceBound = false
    private var remoteService: IRemoteService? = null
    private val remoteServiceConnection = object : ServiceConnection {
        private val tag = this.javaClass.name

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(tag, "onServiceConnected: rxclient")

            if (service != null) {
                remoteService = IRemoteService.Stub.asInterface(service)
                remoteServiceBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteServiceBound = false
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.d(tag, "binding died")
        }

    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Intent("service.emitter").also {
        //Intent().also {
            //Log.d(tag, "intent action = ${it.action}")
            //it.action = "service.emitter"
            it.setPackage("dev.bananaumai.practices.background.rx")
            //it.setClassName("dev.bananaumai.practices.background.rx", "IRemoteService")
            bindService(it, remoteServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStart() {
        super.onStart()

        Log.d(tag, "onStart")

        scope.launch {
            Log.d(tag, "coroutine launched")

            repeat(100) {
                if (remoteServiceBound) {
                    Log.d(tag, "remote service is bound : $it")
                    val res = remoteService?.send(it)
                    Log.d(tag, "result is $res")
                } else {
                    Log.d(tag, "remote service is not bound yet : $it")
                }

                delay(100)
            }
        }
    }

}
