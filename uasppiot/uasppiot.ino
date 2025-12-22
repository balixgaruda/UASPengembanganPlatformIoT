#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <time.h>
#include <PZEM004Tv30.h>

const char* WIFI_SSID = "AndroidAP";
const char* WIFI_PASS = "ultrasgaruda";
const char* MQTT_BROKER_IP = "192.168.43.243";
const int MQTT_PORT = 1883;
const char* MQTT_CLIENT_ID = "ESP32-01";
const char* TOPIC_SENSOR = "iot/sensor/ESP32-01";
const char* TOPIC_RELAY = "iot/relay/ESP32-01";
const char* TOPIC_RELAY_CMD = "iot/command/ESP32-01";
const char* ESP_ID = "ESP32-01";

#define RELAY_PIN 23
#define RELAY_INVERTED 0
#define PZEM_RX_PIN 16
#define PZEM_TX_PIN 17

PZEM004Tv30 pzem(Serial2, PZEM_RX_PIN, PZEM_TX_PIN);

const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 7*3600;
const int daylightOffset_sec = 0;

const unsigned long SENSOR_POST_INTERVAL = 30UL * 1000UL;
const unsigned long PZEM_READ_INTERVAL = 3000;

WiFiClient espClient;
PubSubClient mqttClient(espClient);
unsigned long lastSensorPost = 0;
unsigned long lastPzemRead = 0;
float lastVoltage = 0;
float lastCurrent = 0;
float lastPower = 0;
float lastEnergy = 0;

String isoTimestamp() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) return "";
  char buf[32];
  time_t rawtime = mktime(&timeinfo);
  struct tm *gmt = gmtime(&rawtime);
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", gmt);
  return String(buf);
}

void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
  Serial.println("WiFi connected");
}

void connectMQTT() {
  int attempts = 0;
  while (!mqttClient.connected() && attempts < 3) {
    if (mqttClient.connect(MQTT_CLIENT_ID)) {
      mqttClient.subscribe(TOPIC_RELAY_CMD);
      Serial.print("MQTT connected, subscribed to: ");
      Serial.println(TOPIC_RELAY_CMD);
    } else {
      delay(2000);
      attempts++;
    }
  }
}

void publishSensorData() {
  if (!mqttClient.connected()) return;
  
  StaticJsonDocument<256> doc;
  doc["esp_id"] = ESP_ID;
  doc["voltage"] = lastVoltage;
  doc["current"] = lastCurrent;
  doc["power"] = lastPower;
  doc["relay_status"] = (digitalRead(RELAY_PIN) ^ RELAY_INVERTED) ? "ON" : "OFF";
  doc["timestamp"] = isoTimestamp();
  
  char payload[256];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_SENSOR, payload);
  Serial.println("Sensor data published");
}

void publishRelayEvent(const String &command, const String &reason) {
  if (!mqttClient.connected()) return;
  
  StaticJsonDocument<256> doc;
  doc["esp_id"] = ESP_ID;
  doc["relay_id"] = "Relay-1";
  doc["command"] = command;
  doc["reason"] = reason;
  doc["initiated_by"] = "system";
  doc["timestamp"] = isoTimestamp();
  
  char payload[256];
  serializeJson(doc, payload);
  mqttClient.publish(TOPIC_RELAY, payload);
  Serial.print("Relay ");
  Serial.print(command);
  Serial.print(" (");
  Serial.print(reason);
  Serial.println(")");
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  char message[length + 1];
  memcpy(message, payload, length);
  message[length] = '\0';
  
  StaticJsonDocument<128> doc;
  DeserializationError error = deserializeJson(doc, message);
  if (error) return;
  
  const char* target_relay = doc["relay_id"];
  if (target_relay && strcmp(target_relay, "Relay-ESP32-01") != 0) {
    Serial.print("Command ignored - not for this device: ");
    Serial.println(target_relay);
    return;
  }
  
  String command = doc["command"];
  
  if (command == "ON") {
    digitalWrite(RELAY_PIN, RELAY_INVERTED ? LOW : HIGH);
    Serial.println("Command received: Relay ON");
    publishRelayEvent("ON", "MANUAL");
  } 
  else if (command == "OFF") {
    digitalWrite(RELAY_PIN, RELAY_INVERTED ? HIGH : LOW);
    Serial.println("Command received: Relay OFF");
    publishRelayEvent("OFF", "MANUAL");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial2.begin(9600, SERIAL_8N1, PZEM_RX_PIN, PZEM_TX_PIN);
  delay(3000);
  
  bool pzemOK = false;
  for (int i = 0; i < 5; i++) {
    float testVoltage = pzem.voltage();
    if (!isnan(testVoltage)) {
      Serial.print("PZEM ready, voltage: ");
      Serial.print(testVoltage, 1);
      Serial.println(" V");
      pzemOK = true;
      break;
    }
    delay(1000);
  }
  if (!pzemOK) {
    pzem.resetEnergy();
    delay(1000);
  }
  
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, RELAY_INVERTED ? LOW : HIGH);
  Serial.println("Relay initialized: ON");
  
  connectWiFi();

  mqttClient.setServer(MQTT_BROKER_IP, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);
  mqttClient.setKeepAlive(60);
  connectMQTT();
  
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  delay(2000);
  
  Serial.println("System ready");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
  }
  
  if (!mqttClient.connected()) {
    connectMQTT();
  }
  
  mqttClient.loop();
  unsigned long now = millis();

  if (now - lastPzemRead >= PZEM_READ_INTERVAL) {
    lastPzemRead = now;
    
    for (int attempt = 0; attempt < 3; attempt++) {
      float v = pzem.voltage();
      if (!isnan(v)) {
        lastVoltage = v;
        lastCurrent = pzem.current();
        lastPower = pzem.power();
        lastEnergy = pzem.energy();
        
        if (isnan(lastCurrent)) lastCurrent = 0;
        if (isnan(lastPower)) lastPower = 0;
        if (isnan(lastEnergy)) lastEnergy = 0;
        break;
      }
      delay(200);
    }

    Serial.printf("V: %.1fV | I: %.3fA | P: %.1fW | E: %.2fkWh\n", 
                  lastVoltage, lastCurrent, lastPower, lastEnergy);
  }

  if (now - lastSensorPost >= SENSOR_POST_INTERVAL) {
    lastSensorPost = now;
    publishSensorData();
  }

  const float POWER_THRESHOLD = 2000.0;
  static bool overload = false;

  if (lastPower > POWER_THRESHOLD && !overload) {
    Serial.println("OVERLOAD DETECTED - Relay OFF");
    digitalWrite(RELAY_PIN, RELAY_INVERTED ? HIGH : LOW);
    publishRelayEvent("OFF", "OVERLOAD");
    overload = true;
  }

  if (lastPower <= POWER_THRESHOLD && overload) {
    Serial.println("Power normal - Relay ON");
    digitalWrite(RELAY_PIN, RELAY_INVERTED ? LOW : HIGH);
    publishRelayEvent("ON", "AUTO_RECOVER");
    overload = false;
  }
}