#include <Arduino.h>
#include <WiFi.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_SSD1306.h>
#include <MAX30105.h>
#include "heartRate.h"

#ifndef WATCH_WIFI_SSID
#define WATCH_WIFI_SSID "CHANGE_ME"
#endif

#ifndef WATCH_WIFI_PASSWORD
#define WATCH_WIFI_PASSWORD "CHANGE_ME"
#endif

#ifndef WATCH_SERVER_HOST
#define WATCH_SERVER_HOST "192.168.1.100"
#endif

#ifndef WATCH_SERVER_PORT
#define WATCH_SERVER_PORT 9000
#endif

#ifndef WATCH_IMEI
#define WATCH_IMEI "123456789012345"
#endif

#ifndef WATCH_I2C_SDA
#define WATCH_I2C_SDA 8
#endif

#ifndef WATCH_I2C_SCL
#define WATCH_I2C_SCL 9
#endif

namespace {

constexpr uint32_t SERIAL_BAUD = 115200;
constexpr uint32_t WIFI_RETRY_MS = 5000;
constexpr uint32_t TCP_RETRY_MS = 3000;
constexpr uint32_t SENSOR_MS = 40;
constexpr uint32_t HEARTBEAT_MS = 30000;
constexpr uint32_t HEALTH_UPLOAD_MS = 10000;
constexpr uint32_t DISPLAY_MS = 500;
constexpr int OLED_WIDTH = 128;
constexpr int OLED_HEIGHT = 64;
constexpr int OLED_ADDR = 0x3C;

WiFiClient tcp;
MAX30105 max30102;
Adafruit_MPU6050 mpu;
Adafruit_SSD1306 display(OLED_WIDTH, OLED_HEIGHT, &Wire, -1);

bool maxReady = false;
bool mpuReady = false;
bool oledReady = false;
bool loggedIn = false;

uint32_t lastWifiAttempt = 0;
uint32_t lastTcpAttempt = 0;
uint32_t lastSensorRead = 0;
uint32_t lastHeartbeatUpload = 0;
uint32_t lastHealthUpload = 0;
uint32_t lastDisplayUpdate = 0;

int heartRate = 0;
int bloodOxygen = 0;
int steps = 0;
int rollovers = 0;
int calories = 0;

float lastAccelMagnitude = 0.0f;
bool stepArmed = true;

String readTcpFrame(uint32_t timeoutMs) {
  uint32_t start = millis();
  String frame;
  while (millis() - start < timeoutMs) {
    while (tcp.connected() && tcp.available()) {
      char ch = static_cast<char>(tcp.read());
      frame += ch;
      if (ch == '#') {
        return frame;
      }
    }
    delay(5);
  }
  return frame;
}

bool sendFrame(const String &frame) {
  if (!tcp.connected()) {
    return false;
  }
  Serial.print(F("TX "));
  Serial.println(frame);
  tcp.print(frame);
  tcp.flush();

  String response = readTcpFrame(300);
  if (response.length() > 0) {
    Serial.print(F("RX "));
    Serial.println(response);
  }
  return true;
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    return;
  }

  uint32_t now = millis();
  if (now - lastWifiAttempt < WIFI_RETRY_MS) {
    return;
  }
  lastWifiAttempt = now;

  Serial.print(F("Connecting Wi-Fi: "));
  Serial.println(WATCH_WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WATCH_WIFI_SSID, WATCH_WIFI_PASSWORD);
}

void connectTcp() {
  if (WiFi.status() != WL_CONNECTED || tcp.connected()) {
    return;
  }

  uint32_t now = millis();
  if (now - lastTcpAttempt < TCP_RETRY_MS) {
    return;
  }
  lastTcpAttempt = now;

  Serial.print(F("Connecting TCP "));
  Serial.print(WATCH_SERVER_HOST);
  Serial.print(F(":"));
  Serial.println(WATCH_SERVER_PORT);

  tcp.stop();
  loggedIn = false;
  if (!tcp.connect(WATCH_SERVER_HOST, WATCH_SERVER_PORT)) {
    Serial.println(F("TCP connect failed"));
    return;
  }

  String login = String("IW*AP00*") + WATCH_IMEI + "#";
  loggedIn = sendFrame(login);
}

void updateMax30102() {
  if (!maxReady || !max30102.available()) {
    if (maxReady) {
      max30102.check();
    }
    return;
  }

  uint32_t ir = max30102.getIR();
  uint32_t red = max30102.getRed();
  max30102.nextSample();

  if (ir < 50000) {
    return;
  }

  if (checkForBeat(ir)) {
    static uint32_t lastBeat = 0;
    uint32_t now = millis();
    if (lastBeat > 0) {
      float bpm = 60000.0f / static_cast<float>(now - lastBeat);
      if (bpm >= 45.0f && bpm <= 180.0f) {
        heartRate = static_cast<int>(heartRate == 0 ? bpm : heartRate * 0.75f + bpm * 0.25f);
      }
    }
    lastBeat = now;
  }

  if (red > 0 && ir > 0) {
    float ratio = static_cast<float>(red) / static_cast<float>(ir);
    int estimate = static_cast<int>(110.0f - 18.0f * ratio);
    bloodOxygen = constrain(estimate, 90, 99);
  }
}

void updateMpu6050() {
  if (!mpuReady) {
    return;
  }

  sensors_event_t accel;
  sensors_event_t gyro;
  sensors_event_t temp;
  mpu.getEvent(&accel, &gyro, &temp);

  float magnitude = sqrtf(
      accel.acceleration.x * accel.acceleration.x +
      accel.acceleration.y * accel.acceleration.y +
      accel.acceleration.z * accel.acceleration.z);
  float delta = fabsf(magnitude - lastAccelMagnitude);
  lastAccelMagnitude = magnitude;

  if (stepArmed && delta > 3.5f) {
    steps += 1;
    calories = static_cast<int>(steps * 0.045f);
    stepArmed = false;
  } else if (!stepArmed && delta < 1.2f) {
    stepArmed = true;
  }

  if (magnitude > 28.0f) {
    rollovers += 1;
  }
}

void updateSensors() {
  uint32_t now = millis();
  if (now - lastSensorRead < SENSOR_MS) {
    return;
  }
  lastSensorRead = now;
  updateMax30102();
  updateMpu6050();
}

void uploadHeartbeat() {
  uint32_t now = millis();
  if (!loggedIn || now - lastHeartbeatUpload < HEARTBEAT_MS) {
    return;
  }
  lastHeartbeatUpload = now;

  String frame = String("IW*AP03*1,") + steps + "," + rollovers + "," + calories + "#";
  sendFrame(frame);
}

void uploadHealth() {
  uint32_t now = millis();
  if (!loggedIn || now - lastHealthUpload < HEALTH_UPLOAD_MS) {
    return;
  }
  if (heartRate <= 0 && bloodOxygen <= 0) {
    return;
  }
  lastHealthUpload = now;

  int hr = heartRate > 0 ? heartRate : 0;
  int spo2 = bloodOxygen > 0 ? bloodOxygen : 0;
  String frame = String("IW*APHP*") + hr + ",0,0," + spo2 + ",0,0#";
  sendFrame(frame);
}

void updateDisplay() {
  if (!oledReady) {
    return;
  }
  uint32_t now = millis();
  if (now - lastDisplayUpdate < DISPLAY_MS) {
    return;
  }
  lastDisplayUpdate = now;

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.setTextSize(1);
  display.print(F("WiFi "));
  display.println(WiFi.status() == WL_CONNECTED ? F("OK") : F("..."));
  display.print(F("TCP  "));
  display.println(tcp.connected() ? F("OK") : F("..."));
  display.print(F("HR   "));
  display.println(heartRate > 0 ? String(heartRate) + " bpm" : "--");
  display.print(F("SpO2 "));
  display.println(bloodOxygen > 0 ? String(bloodOxygen) + "%" : "--");
  display.print(F("Step "));
  display.println(steps);
  display.display();
}

void initSensors() {
  Wire.begin(WATCH_I2C_SDA, WATCH_I2C_SCL);

  oledReady = display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR);
  if (oledReady) {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println(F("Health watch boot"));
    display.display();
  } else {
    Serial.println(F("OLED not found"));
  }

  maxReady = max30102.begin(Wire, I2C_SPEED_FAST);
  if (maxReady) {
    max30102.setup(0x1F, 4, 2, 100, 411, 4096);
    max30102.setPulseAmplitudeRed(0x1F);
    max30102.setPulseAmplitudeIR(0x1F);
    max30102.setPulseAmplitudeGreen(0);
    Serial.println(F("MAX30102 ready"));
  } else {
    Serial.println(F("MAX30102 not found"));
  }

  mpuReady = mpu.begin(0x68, &Wire);
  if (mpuReady) {
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
    Serial.println(F("MPU6050 ready"));
  } else {
    Serial.println(F("MPU6050 not found"));
  }
}

}  // namespace

void setup() {
  Serial.begin(SERIAL_BAUD);
  delay(300);
  Serial.println();
  Serial.println(F("ESP32-C3 Wi-Fi health watch"));
  initSensors();
}

void loop() {
  connectWifi();
  connectTcp();
  updateSensors();
  uploadHeartbeat();
  uploadHealth();
  updateDisplay();

  if (WiFi.status() == WL_CONNECTED && !tcp.connected()) {
    loggedIn = false;
  }
}
