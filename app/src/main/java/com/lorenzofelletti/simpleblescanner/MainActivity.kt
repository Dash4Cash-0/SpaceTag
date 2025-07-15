package com.lorenzofelletti.simpleblescanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lorenzofelletti.permissions.PermissionManager
import com.lorenzofelletti.permissions.dispatcher.dsl.checkPermissions
import com.lorenzofelletti.permissions.dispatcher.dsl.doOnDenied
import com.lorenzofelletti.permissions.dispatcher.dsl.doOnGranted
import com.lorenzofelletti.permissions.dispatcher.dsl.showRationaleDialog
import com.lorenzofelletti.permissions.dispatcher.dsl.withRequestCode
import com.lorenzofelletti.simpleblescanner.BuildConfig.DEBUG
import com.lorenzofelletti.simpleblescanner.blescanner.BleScanManager
import com.lorenzofelletti.simpleblescanner.blescanner.adapter.BleDeviceAdapter
import com.lorenzofelletti.simpleblescanner.blescanner.model.BleDevice
import com.lorenzofelletti.simpleblescanner.blescanner.model.BleScanCallback
import com.lorenzofelletti.simpleblescanner.blescanner.model.DeviceDistance
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var btnStartScan: Button

    private lateinit var permissionManager: PermissionManager

    private lateinit var btManager: BluetoothManager
    private lateinit var bleScanManager: BleScanManager

    private lateinit var foundDevices: MutableList<BleDevice>

    private lateinit var deviceDistance: DeviceDistance


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)
        permissionManager buildRequestResultsDispatcher {
            withRequestCode(BLE_PERMISSION_REQUEST_CODE) {
                checkPermissions(blePermissions)
                showRationaleDialog(getString(R.string.ble_permission_rationale))
                doOnGranted { bleScanManager.scanBleDevices() }
                doOnDenied {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.ble_permissions_denied_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // RecyclerView handling
        val rvFoundDevices = findViewById<View>(R.id.rv_found_devices) as RecyclerView
        foundDevices = BleDevice.createBleDevicesList()
        val adapter = BleDeviceAdapter(foundDevices)
        rvFoundDevices.adapter = adapter
        rvFoundDevices.layoutManager = LinearLayoutManager(this)

        // BleManager creation
        val deviceDistance = DeviceDistance()
        btManager = getSystemService(BluetoothManager::class.java)
        bleScanManager = BleScanManager(btManager, 5000, scanCallback = BleScanCallback({
            val name = it?.scanRecord?.deviceName
            val manufacturerData = it?.scanRecord?.manufacturerSpecificData
            var uuid: UUID? = null
            val rssi = it?.rssi
            var txPower : Int? = null
            val targetName = ("SpaceTag")

            if(manufacturerData != null){
                for (i in 0 until manufacturerData.size){
                    val data = manufacturerData.valueAt(i)
                    if(data != null && data.size >= 23){
                        val uuidBytes = data.copyOfRange(2, 18)
                        val buffer = ByteBuffer.wrap(uuidBytes).order(ByteOrder.BIG_ENDIAN)
                        val msb = buffer.long
                        val lsb = buffer.long
                        txPower = data[data.size - 1].toInt()
                        uuid = UUID(msb, lsb)
                        Log.d("SCAN", "Parsed UUID: $uuid")
                    }
                }
            }

            if (name.isNullOrBlank()) return@BleScanCallback
            if (uuid != null) {
                if(name == targetName && rssi != null && txPower != null) {

                    val distance = deviceDistance.calculateDistance(txPower, rssi)
                    val device = BleDevice("$name $distance")
                    if (!foundDevices.contains(device)) {
                        if (DEBUG) {
                            Log.d(
                                BleScanCallback::class.java.simpleName,
                                "${this.javaClass.enclosingMethod?.name} - Found device: $name"
                            )
                        }
                        foundDevices.add(device)
                        adapter.notifyItemInserted(foundDevices.size - 1)
                    }
                }
            }
        }))

        // Adding the actions the manager must do before and after scanning
        bleScanManager.beforeScanActions.add { btnStartScan.isEnabled = false }
        bleScanManager.beforeScanActions.add {
            foundDevices.size.let {
                foundDevices.clear()
                adapter.notifyItemRangeRemoved(0, it)
            }
        }
        bleScanManager.afterScanActions.add { btnStartScan.isEnabled = true }

        // Adding the onclick listener to the start scan button
        btnStartScan = findViewById(R.id.btn_start_scan)
        btnStartScan.setOnClickListener {
            if (DEBUG) Log.i(TAG, "${it.javaClass.simpleName}:${it.id} - onClick event")

            // Checks if the required permissions are granted and starts the scan if so, otherwise it requests them
            permissionManager checkRequestAndDispatch BLE_PERMISSION_REQUEST_CODE
        }
    }

    /**
     * Function that checks whether the permission was granted or not
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.dispatchOnRequestPermissionsResult(requestCode, grantResults)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val BLE_PERMISSION_REQUEST_CODE = 1
        @RequiresApi(Build.VERSION_CODES.S)
        private val blePermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }
}