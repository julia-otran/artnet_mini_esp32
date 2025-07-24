package us.juhouse.eletronic.artnetminiesp32

enum class BluetoothSerialRequest(val code: Int) {
    BLUETOOTH_REQUEST_TYPE_NONE(0),
    BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS(1),
    BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD(2),
    BLUETOOTH_REQUEST_TYPE_GET_INFO(3),
}