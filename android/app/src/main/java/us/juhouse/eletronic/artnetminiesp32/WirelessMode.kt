package us.juhouse.eletronic.artnetminiesp32

enum class WirelessMode(val code: Int) {
    NONE(0),
    CLIENT_DHCP(1),
    AP(2)
}