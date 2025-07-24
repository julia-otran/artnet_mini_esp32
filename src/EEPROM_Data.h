#include <Arduino.h>
#include <DMX.h>

#define SYSTEM_PASSWORD_MAX_LENGTH 12
#define WIFI_SSID_MIN_LENGTH 1
#define WIFI_SSID_MAX_LENGTH 200
#define WIFI_PASSWORD_MIN_LENGTH 8
#define WIFI_PASSWORD_MAX_LENGTH 200

enum EEPROM_DataWirelessMode {
    WIRELESS_MODE_UNINITIALIZED = 0,
    WIRELESS_MODE_CLIENT_DHCP = 1,
    WIRELESS_MODE_AP = 2
};

typedef struct {
    char systemPassword[SYSTEM_PASSWORD_MAX_LENGTH + 1];
    uint16_t channelCount;
    uint8_t net;
    uint8_t subuni;
    uint8_t wirelessMode;
    char wirelessSSID[WIFI_SSID_MAX_LENGTH + 1];
    char wirelessPassword[WIFI_PASSWORD_MAX_LENGTH + 1];
} EEPROM_Data;

void EEPROM_DataInitialize();
EEPROM_Data* EEPROM_DataGet();
void EEPROM_DataStore();
void EEPROM_DataReset();
uint8_t EEPROM_DataIsValid(EEPROM_Data *data, char **err);
