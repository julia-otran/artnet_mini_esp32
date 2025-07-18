#include <Arduino.h>
#include <EEPROM_Data.h>
#include <BluetoothSerial.h>
#include <string.h>
#include <WiFi.h>
#include <ArtNet.h>

#include "hal/uart_ll.h"

using namespace art_net;

#define LED_CATHODE_PIN GPIO_NUM_4
#define RESET_PREFERENCES_PIN GPIO_NUM_14
#define BLUETOOTH_DATA_RECEIVE_TIMEOUT_MILLIS 1000

enum BluetoothRequestType {
  BLUETOOTH_REQUEST_TYPE_NONE,
  BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS,
  BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD
};

EEPROM_Data* settings;
EEPROM_Data tempSettings;
BluetoothSerial SerialBT;

unsigned long lastSettingsAuthFail;
unsigned long lastBTReceivedData;
uint8_t lastSettingsAuthFailCount;
uint8_t bluetoothRequestType;

wl_status_t lastWiFiStatus;
uint8_t settingReloadWiFi;
WiFiUDP UDP;

ArtNet MyArtNet;

uint8_t dmxDataBuffers[2][513];
uint8_t* dmxWriteDataBuffer;
uint8_t* dmxReadDataBuffer;
uint8_t dmxWriteDataBufferHasNewData;

uint16_t currentWriteBufferIndex;
unsigned long lastTransmit;
unsigned long breakStartedAt;


void onDmxDataSend(uint8_t universe, uint8_t ctrlByte, const uint8_t *data, const uint16_t size) {
  if (size <= DMX_MAX_CHANNELS) { 
    memcpy(dmxWriteDataBuffer, dmxReadDataBuffer, 513);
    dmxWriteDataBuffer[0] = ctrlByte;
    memcpy(&dmxWriteDataBuffer[1], data, size);
    dmxWriteDataBufferHasNewData = 1;
  }
}

void sendAtrNetPacket(uint32_t dstIP, uint16_t dstPort, const uint8_t *data, uint32_t size) {
  UDP.beginPacket(dstIP, dstPort);
  UDP.write(data, size);
  UDP.endPacket();
}

void setup() {
  pinMode(LED_CATHODE_PIN, OUTPUT);
  digitalWrite(LED_CATHODE_PIN, LOW);

  pinMode(RESET_PREFERENCES_PIN, INPUT_PULLDOWN);

  Serial2.begin(250000, SERIAL_8N2);

  EEPROM_DataInitialize();

  if (digitalRead(RESET_PREFERENCES_PIN) == HIGH) {
    EEPROM_DataReset();
  }

  SerialBT.begin("ArtNet Mini ESP32");

  settings = EEPROM_DataGet();
  settingReloadWiFi = 1;
  lastSettingsAuthFail = 0;
  lastSettingsAuthFailCount = 0;
  bluetoothRequestType = BLUETOOTH_REQUEST_TYPE_NONE;
  lastBTReceivedData = 0;

  lastWiFiStatus = WiFi.status();

  MyArtNet.net = settings->net;
  MyArtNet.subnet = settings->subuni >> 4;
  MyArtNet.setDmxDataCallback(onDmxDataSend);
  MyArtNet.setSendPacketCallback(sendAtrNetPacket);

  dmxWriteDataBuffer = dmxDataBuffers[0];
  dmxReadDataBuffer = dmxDataBuffers[1];
  dmxWriteDataBufferHasNewData = 0;
  currentWriteBufferIndex = 0;
  lastTransmit = 0;
  breakStartedAt = 0;
}

void loadSettingsFromBluetooth() {
  if (lastSettingsAuthFail) {
    if (millis() - lastSettingsAuthFail > (1000 * lastSettingsAuthFailCount)) {
      SerialBT.println("[ER] BAD Password");

      while (SerialBT.available()) { SerialBT.read(); }

      lastSettingsAuthFail = 0;

      if (lastSettingsAuthFailCount > 200) {
        lastSettingsAuthFailCount = 200;
      }
    }
  } else if (bluetoothRequestType == BLUETOOTH_REQUEST_TYPE_NONE && SerialBT.available()) { 
    bluetoothRequestType = SerialBT.read();
    lastBTReceivedData = 0;
    
    if (bluetoothRequestType != BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS && bluetoothRequestType != BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD) {
      bluetoothRequestType = BLUETOOTH_REQUEST_TYPE_NONE;
      while (SerialBT.available()) { SerialBT.read(); }
    }
  } else if (SerialBT.available() > sizeof(EEPROM_Data)) {
    while (SerialBT.available()) { SerialBT.read(); }
    SerialBT.println("[ER] Protocol Fail");
  } else if (bluetoothRequestType != BLUETOOTH_REQUEST_TYPE_NONE && SerialBT.available()) {
    if (lastBTReceivedData == 0) {
      lastBTReceivedData = millis();
    } else if (millis() - lastBTReceivedData > BLUETOOTH_DATA_RECEIVE_TIMEOUT_MILLIS) {
      while (SerialBT.available()) { SerialBT.read(); }
      lastBTReceivedData = 0;
    }
  } else if (bluetoothRequestType == BLUETOOTH_REQUEST_TYPE_CHANGE_SETTINGS && SerialBT.available() == sizeof(EEPROM_Data)) {
    lastBTReceivedData = 0;
    SerialBT.readBytes((uint8_t*)&tempSettings, sizeof(EEPROM_Data));

    if (EEPROM_DataIsValid(&tempSettings)) {
      if (strncmp(tempSettings.systemPassword, settings->systemPassword, SYSTEM_PASSWORD_MAX_LENGTH) == 0) {
        lastSettingsAuthFail = 0;
        lastSettingsAuthFailCount = 0;

        if (settings->wirelessMode != tempSettings.wirelessMode || strcmp(settings->wirelessSSID, tempSettings.wirelessSSID) || strcmp(settings->wirelessPassword, tempSettings.wirelessPassword)) {
          settingReloadWiFi = 1;
        }

        memcpy(settings, &tempSettings, sizeof(EEPROM_Data));
        EEPROM_DataStore();

        MyArtNet.net = settings->net;
        MyArtNet.subnet = settings->subuni >> 4;

        SerialBT.println("[OK] Settings Loaded!");
      } else {
        lastSettingsAuthFail = millis();
        lastSettingsAuthFailCount++;
      }
    } else {
      SerialBT.println("[ER] Settings are invalid! Rolled back");
    }
  } else if (bluetoothRequestType == BLUETOOTH_REQUEST_TYPE_CHANGE_PASSWORD && SerialBT.available() == SYSTEM_PASSWORD_MAX_LENGTH * 2) {
    lastBTReceivedData = 0;
    SerialBT.readBytes((uint8_t*)&tempSettings, SYSTEM_PASSWORD_MAX_LENGTH);
    if (strncmp(tempSettings.systemPassword, settings->systemPassword, SYSTEM_PASSWORD_MAX_LENGTH)) {
      SerialBT.readBytes((uint8_t*)&settings, SYSTEM_PASSWORD_MAX_LENGTH);
      settings->systemPassword[SYSTEM_PASSWORD_MAX_LENGTH] = 0;
      lastSettingsAuthFail = 0;
      lastSettingsAuthFailCount = 0;
      EEPROM_DataStore();
      SerialBT.println("[OK] Password Changed!");
    } else {
      lastSettingsAuthFail = millis();
      lastSettingsAuthFailCount++;
    }
  }
}

void reconnectWiFi() {
  wl_status_t wifiStatus;

  if (settingReloadWiFi && WiFi.isConnected()) {
    WiFi.disconnect();
  }

  if (settings->wirelessMode == CLIENT_DHCP) {
    WiFi.begin(settings->wirelessSSID, settings->wirelessPassword);
    settingReloadWiFi = 0;
  }

  if (settings->wirelessMode == AP) {
    WiFi.softAP(settings->wirelessSSID, settings->wirelessPassword);
    settingReloadWiFi = 0;
  }

  wifiStatus = WiFi.status();

  if (lastWiFiStatus != wifiStatus) {
    if (wifiStatus == WL_CONNECTED) {
      MyArtNet.ip = WiFi.localIP();
      WiFi.macAddress(MyArtNet.mac);

      UDP.begin(0x1936);
    } else {
      UDP.stop();
    }

    lastWiFiStatus = wifiStatus;
  }
}

void loop() {
  loadSettingsFromBluetooth();
  reconnectWiFi();

  if (currentWriteBufferIndex == 0) {
    if (breakStartedAt == 0) {
      pinMode(LED_CATHODE_PIN, INPUT);
      breakStartedAt = micros();
    } else if (micros() - breakStartedAt >= DMX_BREAK_LOW_INTERVAL_MICROS) {
      pinMode(LED_CATHODE_PIN, OUTPUT);
      digitalWrite(LED_CATHODE_PIN, LOW);
      delayMicroseconds(DMX_BREAK_HIGH_INTERVAL_MICROS);
      breakStartedAt = 0;
    }
  }

  while (breakStartedAt == 0 && Serial2.availableForWrite() && currentWriteBufferIndex < settings->channelCount + 1) {
    Serial2.write(dmxReadDataBuffer[currentWriteBufferIndex]);
    currentWriteBufferIndex++;
  }

  if (uart_ll_is_tx_idle(UART_LL_GET_HW(2)) && currentWriteBufferIndex >= settings->channelCount + 1) {
    if (dmxWriteDataBufferHasNewData) {
      uint8_t *aux = dmxWriteDataBuffer;
      dmxWriteDataBuffer = dmxReadDataBuffer;
      dmxReadDataBuffer = aux;
      currentWriteBufferIndex = 0;
      lastTransmit = 0;
    } else if (lastTransmit == 0) {
      lastTransmit = millis();
    } else if (millis() - lastTransmit > DMX_MAX_TRANSMIT_INTERVAL_MS) {
      currentWriteBufferIndex = 0;
      lastTransmit = 0;
    }
  }
}
