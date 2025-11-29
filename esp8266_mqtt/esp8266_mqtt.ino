#include <ArduinoMqttClient.h>
#include <ESP8266WiFi.h>
#include <ArduinoJson.h>

char ssid[] = "9-2-3-4";     // 修改为你的WiFi名称
char pass[] = "a987654321";  // 修改为你的WiFi密码

WiFiClient wifiClient;
MqttClient mqttClient(wifiClient);

const char broker[] = "iot-06z00gg646n4wfu.mqtt.iothub.aliyuncs.com";
const int port = 1883;

const char inTopic[] = "/i8t1JQgMtaq/ESP8266/user/ESP8266";

void setup() {
  Serial.begin(9600);
  pinMode(D4, OUTPUT);  // 初始化LED控制引脚
  digitalWrite(D4,HIGH);
  // 连接WiFi
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);
  while (WiFi.begin(ssid, pass) != WL_CONNECTED) {
    delay(5000);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected!");

  // 配置MQTT客户端
  mqttClient.setId("i8t1JQgMtaq.ESP8266|securemode=2,signmethod=hmacsha256,timestamp=1747889063624|");
  mqttClient.setUsernamePassword("ESP8266&i8t1JQgMtaq", "4885ee670ad31dfc2bdec6aa4efb1da9422477f0d4a0ad1696e3ab743db183f5");

  // 连接MQTT代理
  if (!mqttClient.connect(broker, port)) {
    Serial.print("MQTT connection failed! Error code: ");
    Serial.println(mqttClient.connectError());
    while(1);
  }
  Serial.println("Connected to MQTT broker!");

  // 设置消息回调
  mqttClient.onMessage(onMqttMessage);
  mqttClient.subscribe(inTopic);
  Serial.print("Subscribed to: ");
  Serial.println(inTopic);
}

void loop() {
  mqttClient.poll();  // 保持MQTT连接活跃
}

void onMqttMessage(int messageSize) {
  String receivedPayload;
  
  // 读取完整消息
  while (mqttClient.available()) {
    receivedPayload += (char)mqttClient.read();
  }

  // 打印原始消息
  // Serial.println("\nReceived raw message:");
  // Serial.println(receivedPayload);
  if(receivedPayload=="1")
  {
    Serial.println("开机成功");
    // Serial.println(receivedPayload);
    digitalWrite(D4,LOW);
    delay(500);
    digitalWrite(D4,HIGH);
    return;
  }
  // 解析JSON消息
  DynamicJsonDocument doc(1024);
  DeserializationError error = deserializeJson(doc, receivedPayload);
  
  if (error) {
    Serial.print("JSON parsing failed: ");
    Serial.println(error.c_str());
    return;
  }

  // 打印格式化消息
  // Serial.println("\nParsed message content:");
  // serializeJsonPretty(doc, Serial);
  // Serial.println("\n");

}