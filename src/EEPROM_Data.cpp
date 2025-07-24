#include <Arduino.h>
#include <EEPROM.h>
#include <EEPROM_Data.h>
#include <ArtNet.h>

EEPROM_Data currentData;
EEPROM_Data storedData;


void EEPROM_DataInitialize() {
    EEPROM.begin(sizeof(EEPROM_Data));
    EEPROM.readBytes(0, &storedData, sizeof(EEPROM_Data));

    if (EEPROM_DataIsValid(&storedData, 0)) {
        memcpy(&currentData, &storedData, sizeof(EEPROM_Data));
    } else {
        EEPROM_DataReset();
    }
}

EEPROM_Data* EEPROM_DataGet() {
    return &currentData;
}

void EEPROM_DataStore() {
    EEPROM.writeBytes(0, &currentData, sizeof(EEPROM_Data));
    EEPROM.commit();
    memcpy(&storedData, &currentData, sizeof(EEPROM_Data));
}

void EEPROM_DataReset() {
    currentData.systemPassword[0] = 0;
    currentData.channelCount = DMX_MAX_CHANNELS;
    currentData.wirelessMode = WIRELESS_MODE_UNINITIALIZED;
    currentData.wirelessSSID[0] = 0;
    currentData.wirelessPassword[0] = 0;

    EEPROM_DataStore();
}

uint8_t EEPROM_DataIsValid(EEPROM_Data *data, char **err) {

    uint8_t dataValid = true;
    uint8_t found = false;

    for (uint8_t i = 0; i < SYSTEM_PASSWORD_MAX_LENGTH + 1; i++) {
        if (data->systemPassword[i] == 0) {
            found = true;
            break;
        }
    }

    dataValid = dataValid && found;

    if (!dataValid && err) {
        (*err) = "System Password invalid.";
        return false;
    }

    dataValid = dataValid && data->channelCount >= DMX_MIN_CHANNELS && data->channelCount <= DMX_MAX_CHANNELS;

    if (!dataValid && err) {
        (*err) = "Channel Count invalid.";
        return false;
    }

    dataValid = dataValid && data->wirelessMode <= WIRELESS_MODE_AP;

    if (!dataValid && err) {
        (*err) = "Wireless mode invalid.";
        return false;
    }

    dataValid = dataValid && data->net <= ART_NET_MAX_NET;

    if (!dataValid && err) {
        (*err) = "ArtNet Net invalid.";
        return false;
    }

    found = false;

    for (uint8_t i = 0; i < WIFI_SSID_MAX_LENGTH + 1; i++) {
        if (data->wirelessSSID[i] == 0) {
            found = true;
            break;
        }
    }

    if (!found && err) {
        (*err) = "Wireless SSID has no termination char.";
        return false;
    }

    dataValid = dataValid && found && strlen(data->wirelessSSID) >= WIFI_SSID_MIN_LENGTH;

    if (!dataValid && err) {
        (*err) = "Wireless SSID is too short.";
        return false;
    }

    found = false;

    for (uint8_t i = 0; i < WIFI_PASSWORD_MAX_LENGTH + 1; i++) {
        if (data->wirelessPassword[i] == 0) {
            found = true;
            break;
        }
    }

    if (!found && err) {
        (*err) = "Wireless Password has no termination char.";
        return false;
    }

    dataValid = dataValid && found && strlen(data->wirelessPassword) >= WIFI_PASSWORD_MIN_LENGTH;

    if (!dataValid && err) {
        (*err) = "Wireless Password too short.";
        return false;
    }

    return dataValid;
}