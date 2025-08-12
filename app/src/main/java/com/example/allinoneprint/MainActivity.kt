package com.example.allinoneprint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.DecimalFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinoneprint.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

//import java.util.logging.Handler

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var btpermission = false
   // var blutetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var inputstream: InputStream? = null
    private var outputstream: OutputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readbuffer: ByteArray
    private var readbufferposition = 0

    @Volatile
    var stopWorker = false
    private var value = ""
    private val connecitonclss: connectionclass = connectionclass()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener {
            checkpermission()
        }

        binding.button2.setOnClickListener {
            if(btpermission){
                Log.d("HCck", "onCreate: ${btpermission} ")
                print_invoice()
            }else{
                checkpermission()
            }
        }
    }

    fun checkpermission() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            //device doesnt support bluetooth
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                userpermissionlauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                userpermissionlauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                userpermissionlauncher.launch(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
    }

    private val userpermissionlauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val blutetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            btpermission = true
            if (blutetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enableBtIntent)
            } else {
                scanBt()
            }

        }
    }
    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            scanBt()
        }
    }
// For RecyclerView

// ... (inside your MainActivity class) ...

    @SuppressLint("MissingPermission")
    private fun scanBt() {
        val bluetoothManager: BluetoothManager? = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        // ... (Your existing Bluetooth adapter and permission checks) ...
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            // Handle these cases with Toasts or by returning
            Toast.makeText(this, "Bluetooth not available or permission denied.", Toast.LENGTH_LONG).show()
            return
        }

        val builder = AlertDialog.Builder(this@MainActivity)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.dilog_scan, null) // Use your layout with RecyclerView
        builder.setCancelable(true) // Allow user to cancel
        builder.setView(dialogView)
        builder.setTitle("Select Paired Device")

        val recyclerViewDevices = dialogView.findViewById<RecyclerView>(R.id.list_item) // Get RecyclerView
        val dialog = builder.create()

        val pairedDevicesSet: Set<BluetoothDevice>? = try {
            bluetoothAdapter.bondedDevices
        } catch (se: SecurityException) {
            Log.e("ScanBt", "SecurityException when getting bonded devices.", se)
            Toast.makeText(this, "Permission missing to access paired devices.", Toast.LENGTH_LONG).show()
            null
        }

        val bluetoothDeviceInfos = mutableListOf<datacalss>()

        if (pairedDevicesSet != null && pairedDevicesSet.isNotEmpty()) {
            // Optional: Remove the initial blank item logic unless specifically needed
            // val datanum1 ... data.add(datanum1)

            for (device in pairedDevicesSet) {
                val deviceName = try {
                    // Ensure BLUETOOTH_CONNECT permission is checked before accessing device.name on API 31+
                    device.name ?: "Unknown Device"
                } catch (se: SecurityException) {
                    Log.e("ScanBt", "SecurityException getting device name for ${device.address}", se)
                    "No Name (Permission Issue)"
                }
                val deviceAddress = device.address ?: "No Address"
                bluetoothDeviceInfos.add(datacalss(shubh = deviceName, labh = deviceAddress))
            }

            Log.d("ScanBt_RecyclerView", "Processed devices: ${bluetoothDeviceInfos.joinToString { it.shubh }}")

            if (bluetoothDeviceInfos.isEmpty()) {
                Toast.makeText(this, "No suitable paired devices found to display.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                return
            }

            recyclerViewDevices.layoutManager = LinearLayoutManager(this@MainActivity)
            val deviceAdapter = DeviceRecyclerAdapter(bluetoothDeviceInfos) { selectedDevice ->
                // This lambda is called when an item is clicked
                binding.editTextText.setText(selectedDevice.shubh)
                connecitonclss.printer_name = selectedDevice.shubh // Already a String
                Log.d("ScanBt_Selection", "Selected: ${selectedDevice.shubh}, Address: ${selectedDevice.labh}")
                dialog.dismiss()
            }
            recyclerViewDevices.adapter = deviceAdapter

            dialog.show()

        } else {
            Toast.makeText(this, "No devices are paired with your phone.", Toast.LENGTH_LONG).show()
            dialog.dismiss() // Ensure dialog doesn't show if there are no devices
            return
        }
    }


    fun beginLisitnForData() {
        try {
            val uiHandler = Handler(Looper.getMainLooper())
            val delimeter: Byte = 10
            stopWorker = false
            readbufferposition = 0
            readbuffer = ByteArray(1024)
            workerThread = Thread {
                while (!Thread.currentThread().isInterrupted && !stopWorker) {
                    try {
                        val bytesAvailable = inputstream!!.available()
                        if (bytesAvailable > 0) {
                            val packetbytes = ByteArray(bytesAvailable)
                            inputstream!!.read(packetbytes)
                            for (i in 0 until bytesAvailable) {
                                val b = packetbytes[i]
                                if (b == delimeter) {
                                    val encodedBytes = ByteArray(readbufferposition)
                                    System.arraycopy(
                                        readbuffer, 0,
                                        encodedBytes, 0,
                                        encodedBytes.size
                                    )
                                    val data = String(encodedBytes, Charset.forName("US-ASCII"))
                                    readbufferposition = 0
                                    uiHandler.post { Log.d("E", data) }
                                } else {
                                    readbuffer[readbufferposition++] = b
                                }
                            }

                        }

                    } catch (ex: IOException) {
                        stopWorker = true
                    }
                }
            }
            workerThread!!.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun Initprinter() {
        var prname = ""
        prname = connecitonclss.printer_name.toString()
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val blutetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        try {
            if (blutetoothAdapter != null) {
                if (!blutetoothAdapter.isEnabled) {
                    val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    btActivityResultLauncher.launch(enableBluetooth)
                }
            }
            val pairedDevices = blutetoothAdapter?.bondedDevices
            if (pairedDevices != null) {
                if (pairedDevices.isNotEmpty()) {
                    if (true) {
                        for (device in pairedDevices) {
                            if (device.name == prname) {
                                bluetoothDevice = device

                                val uuid =
                                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                                val m = bluetoothDevice!!.javaClass.getMethod(
                                    "createRfcommSocket",*arrayOf<Class<*>?>(
                                        Int::class.javaPrimitiveType
                                    )
                                )
                                socket = m.invoke(bluetoothDevice, 1) as BluetoothSocket
                                blutetoothAdapter.cancelDiscovery()
                                socket!!.connect()
                                inputstream = socket!!.inputStream
                                outputstream = socket!!.outputStream
                                beginLisitnForData()
                                break
                            }
                        }
                    }
                }else{
                    value = "No Devices Found"
                    Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }catch (ex:java.lang.Exception){
            Toast.makeText(this@MainActivity, "Bluetooth Printer Not Connected", Toast.LENGTH_SHORT).show()
            socket = null
        }
    }

    fun print_invoice() {
        try {
            var str: String
            var invhdr = "Tax Invoice"
            var addr = "Deccon Tech"
            var mo = ""
            var gstin = ""
            var billno = ""
            var billdt = ""
            var tblno = ""
            var stw = ""
            var msg = ""
            var amtwd = ""

            val wnm = "self"
            val logname = "Admin"
            var amt = 0.0
            var gst = 0.0
            var gamt = 0.0
            var compname = "Thermal Printer Sample"
            mo = "9336314880"
            gstin = "gst no."
            billno = "1001"
            billdt = "12-12-2022"
            tblno = "5"

            msg = "Thank You"
            amtwd = "500"
            amt = 300.0
            gst = 100.0
            gamt = 100.0
            val textdata = StringBuilder()
            val textdata1 = StringBuilder()
            val textdata2 = StringBuilder()
            val textdata3 = StringBuilder()
            val textdata4 = StringBuilder()


            if (invhdr.isNotEmpty()) {
                textdata.append(invhdr.trimIndent())
            }
            textdata.append(compname.trimIndent())
            if (mo.isNotEmpty()) {
                textdata.append(mo.trimIndent())
            }
            if (gstin.isNotEmpty()) {
                textdata.append(gstin.trimIndent())
            }
            str = ""
            str = String.format("%-14s %17s", "Inv#$billno", "Table$tblno")
            textdata.append(str.trimIndent())
            textdata.append("Date Time : $billdt\n")

            textdata1.append("-------------------\n")
            textdata1.append(
                """Item Description""".trimIndent()
            )

            str = String.format("%-11s %9s %10s", "Qty", "Rate", "Amount")
            textdata1.append(str.trimIndent())
            textdata1.append("--------------------\n")
            val df = DecimalFormat("0.00")
            var itemname: String
            var rt: String?
            var qty: String
            var amount: String?
            for (i in 0 until 10) {
                val price = 10
                itemname = "item $i"
                rt = df.format(price)
                qty = "1 pc"
                amount = "10.0"
                textdata1.append(itemname + "\n")
                str = ""
                str = String.format("%-11s %9s %10s", qty, rt, amount)
                textdata1.append(str + "\n")
            }
            textdata1.append("------------\n")
            str = ""


            str = String.format("%-9s %-11s %10s", wnm, "Total : ", amt)
            textdata1.append(str + "\n")
            str = ""

            str = String.format("%-9s %-11s %10s", logname, "Gst :", gst)
            textdata1.append(str + "\n")

            str = ""
            str = String.format("%-7s %8s", "Grand Total : ", gamt)
            textdata2.append(str + "\n")
            textdata3.append(amtwd + "\n")
            if (msg.isNotEmpty()) {
                textdata4.append(msg + "\n")
            }

            textdata4.append("BY \n SHRAVAN SONI \n\n\n\n")
            Log.d("printsent", "IntentPrint:not sent ")

            IntentPrint(textdata.toString(), textdata1.toString(), textdata2.toString(), textdata3.toString(), textdata4.toString() )

        } catch (ex: java.lang.Exception) {
            value += "$ex\nException Intentprnt\n"
            Log.d("printnotsent", "IntentPrint:not sent ")
            Toast.makeText(this@MainActivity, "not sent", Toast.LENGTH_SHORT).show()
        }

    }

        fun IntentPrint(
            txtValue: String,
            txtValue1: String,
            txtValue2: String,
            txtValue3: String,
            txtValue4: String
        ){
            Log.d("printnotsent", "IntentPrint:not sent ")
            if(connecitonclss.printer_name.trim().isNotEmpty()){
                val buffer = txtValue.toByteArray()
                val PrintHeader = byteArrayOf(0xAA.toByte(),0x55, 2, 0)
                PrintHeader[3] = buffer.size.toByte()
                Initprinter()
                if(PrintHeader.size>128){
                    value += "\n value is more than 128 size\n"
                    Log.d("printnotsent", "IntentPrint:not sent ")
                    Toast.makeText(this@MainActivity, value, Toast.LENGTH_SHORT).show()
                }else{
                    try{
                        if(socket!= null) {
                            try {
                                val SP = byteArrayOf(0x1B, 0x40)
                                outputstream!!.write(SP)
                                Thread.sleep(1000)
                            }catch (e: InterruptedException){
                                e.printStackTrace()
                            }
                            Log.d("printsent", "IntentPrint:not sent ")
                            val FONT_1X = byteArrayOf(0x18, 0x21, 0x00)
                            outputstream!!.write(FONT_1X)
                            val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1)
                            outputstream!!.write(ALIGN_CENTER)
                            outputstream!!.write(txtValue.toByteArray())
                            val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0)
                            outputstream!!.write(ALIGN_LEFT)
                            outputstream!!.write(txtValue1.toByteArray())
                            val FONT_2X = byteArrayOf(0x1B, 0x21, 0x30)
                            outputstream!!.write(FONT_2X)
                            outputstream!!.write(txtValue2.toByteArray())
                            outputstream!!.write(FONT_1X)
                            outputstream!!.write(ALIGN_LEFT)
                            outputstream!!.write(txtValue3.toByteArray())
                            outputstream!!.write(ALIGN_CENTER)
                            outputstream!!.write(txtValue4.toByteArray())
                            val FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 66,0x00)
                            outputstream!!.write(FEED_PAPER_AND_CUT)
                            outputstream!!.flush()
                            outputstream!!.close()
                            socket!!.close()
                        }
                        }catch (ex:java.lang.Exception){
                            Toast.makeText(this@MainActivity, ex.message.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            }


        }
}



