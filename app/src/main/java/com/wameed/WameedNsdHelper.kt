package com.wameed

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Handles Network Service Discovery (mDNS) to make the phone discoverable by the PC.
 */
class WameedNsdHelper(context: Context) {
    private val TAG = "WameedNsdHelper"
    private val SERVICE_TYPE = "_wameed._tcp"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serviceName: String? = null

    fun registerService(port: Int) {
        unregisterService() // Ensure any previous instance is stopped

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            this.serviceName = WameedPrefs.getDeviceName()
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.i(TAG, "Service registered: $serviceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "Service unregistered: ${arg0.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister NSD service", e)
            }
            registrationListener = null
        }
    }
}
