package us.juhouse.eletronic.artnetminiesp32

import java.io.OutputStream

interface BluetoothSerialData {
    fun writeToStream(outputStream: OutputStream)
    fun getType(): BluetoothSerialRequest
}
