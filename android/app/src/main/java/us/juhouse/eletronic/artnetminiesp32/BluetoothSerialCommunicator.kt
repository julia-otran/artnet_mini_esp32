package us.juhouse.eletronic.artnetminiesp32

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import java.io.IOException
import java.util.UUID

class BluetoothSerialCommunicator(private val device: BluetoothDevice, private val callbacks: Callbacks): Runnable {
    interface Callbacks {
        fun onDataReceived(data: String)
    }

    private var workerThread: Thread? = null
    private var stopWorker = false
    private var dataString = ""
    private var syncObject = Object()

    private var request: BluetoothSerialRequest = BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_NONE
    private var requestData: BluetoothSerialData? = null

    init {
        stopWorker = false
        workerThread = Thread(this)
        workerThread?.start()
    }

    fun reload() {
        synchronized(syncObject) {
            request = BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_GET_INFO
            syncObject.notify()
        }
    }

    fun sendRequest(requestData: BluetoothSerialData) {
        synchronized(syncObject) {
            this.requestData = requestData
            request = requestData.getType()
            syncObject.notify()
        }
    }

    fun dispose() {
        stopWorker = true
    }

    @SuppressLint("MissingPermission")
    override fun run() {
        val uuid =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID

        try {
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()

            val inputStream = socket.inputStream
            val outputStream = socket.outputStream

            while (!stopWorker) {
                while (inputStream.available() > 0) {
                    dataString += inputStream.read().toChar()
                }

                callbacks.onDataReceived(dataString)

                if (request == BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_NONE) {
                    synchronized(syncObject) {
                        syncObject.wait(1000)
                    }

                    continue
                }

                dataString = ""

                outputStream.write(request.code)

                when (request) {
                    BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_NONE ->
                        outputStream.flush()

                    BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_GET_INFO ->
                        outputStream.flush()

                    BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD -> {
                        requestData?.writeToStream(outputStream)
                        outputStream.flush()
                    }

                    BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS -> {
                        requestData?.writeToStream(outputStream)
                        outputStream.flush()
                    }
                }

                synchronized(syncObject) {
                    request = BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_NONE
                    requestData = null
                }
            }

            inputStream.close()
            outputStream.close()
            socket.close()
        } catch (_: IOException) {

        }
    }
}