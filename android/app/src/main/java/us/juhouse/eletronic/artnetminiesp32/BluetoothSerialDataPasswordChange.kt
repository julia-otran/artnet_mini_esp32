package us.juhouse.eletronic.artnetminiesp32

import java.io.OutputStream

data class BluetoothSerialDataPasswordChange(val oldPassword: String, val newPassword: String): BluetoothSerialData {
    private fun serializeStringToStream(data: String, size: Int, output: OutputStream) {
        for (i in 0..size) {
            if (data.length < i) {
                val currentChar: Int = data[i] as Int
                output.write(currentChar and 0xFF)
            } else {
                output.write(0)
            }
        }
    }

    override fun writeToStream(outputStream: OutputStream) {
        serializeStringToStream(oldPassword, 12, outputStream)
        serializeStringToStream(newPassword, 12, outputStream)
    }

    override fun getType(): BluetoothSerialRequest {
        return BluetoothSerialRequest.BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD
    }
}
