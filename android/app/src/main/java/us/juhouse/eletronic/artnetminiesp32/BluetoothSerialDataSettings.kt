package us.juhouse.eletronic.artnetminiesp32

import java.io.OutputStream

data class BluetoothSerialDataSettings(val password: String, val channelCount: UInt, val net: UInt, val subnet: UInt, val universe: UInt, val wirelessMode: WirelessMode, val wirelessSSID: String, val wirelessPassword: String): BluetoothSerialData {

    private fun serializeStringToStream(data: String, size: Int, output: OutputStream) {
        for (i in 0..size) {
            if (i < data.length) {
                val currentChar: Int = data[i].code
                output.write(currentChar and 0xFF)
            } else {
                output.write(0)
            }
        }
    }

    override fun writeToStream(outputStream: OutputStream) {
        serializeStringToStream(password, 12, outputStream)
        // Bit stuff
        outputStream.write(0)
        outputStream.write((channelCount and 255u).toInt())
        outputStream.write((channelCount shr 8).toInt())
        outputStream.write(net.toInt())
        outputStream.write(((subnet.toInt()) shl 4) or ((universe.toInt()) and 0xF))
        outputStream.write(wirelessMode.code)
        serializeStringToStream(wirelessSSID, 200, outputStream)
        serializeStringToStream(wirelessPassword, 200, outputStream)
        // Bit stuff
        outputStream.write(0)
    }

    override fun getType(): BluetoothSerialRequest {
        return BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS
    }
}