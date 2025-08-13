/**
 * @file main.cpp
 * @brief 36쌍 LED 시각검사 시제품 - ESP32 펌웨어 메인
 * @version 0.1.0
 * @date 2025-08-10
 * 
 * ULN2803A 드라이버 기반 LED 제어 시스템
 * Bluetooth 통신을 통한 Android 앱 연동
 * JSON 프로토콜 기반 명령 처리
 */

#include <Arduino.h>
#include <BluetoothSerial.h>
#include <ArduinoJson.h>
#include "led_controller.h"
#include "bluetooth_manager.h"
#include "config.h"

// 전역 객체
BluetoothSerial SerialBT;
LEDController ledController;
BluetoothManager btManager;

// 상태 변수
bool deviceConnected = false;
unsigned long lastHeartbeat = 0;

void setup() {
    Serial.begin(115200);
    Serial.println("=== LED Vision Tester v0.1.0 ===");
    Serial.println("ESP32 펌웨어 시작");
    
    // LED 컨트롤러 초기화
    if (!ledController.init()) {
        Serial.println("ERROR: LED 컨트롤러 초기화 실패");
        while(1) delay(1000);
    }
    
    // Bluetooth 초기화
    if (!btManager.init(&SerialBT, BT_DEVICE_NAME)) {
        Serial.println("ERROR: Bluetooth 초기화 실패");
        while(1) delay(1000);
    }
    
    Serial.println("시스템 초기화 완료");
    Serial.println("Android 앱 연결 대기 중...");
    
    // 초기 LED 테스트
    ledController.testSequence();
}

void loop() {
    // Bluetooth 연결 상태 확인
    deviceConnected = SerialBT.hasClient();
    
    // 연결 상태가 변경된 경우
    static bool lastConnected = false;
    if (deviceConnected != lastConnected) {
        lastConnected = deviceConnected;
        if (deviceConnected) {
            Serial.println("Android 앱 연결됨");
            sendStatusMessage("CONNECTED");
        } else {
            Serial.println("Android 앱 연결 해제됨");
            ledController.stopSequence();
        }
    }
    
    // 명령 처리
    if (deviceConnected && SerialBT.available()) {
        String command = SerialBT.readString();
        command.trim();
        processCommand(command);
    }
    
    // LED 시퀀스 업데이트
    ledController.update();
    
    // 하트비트 전송 (5초 간격)
    if (deviceConnected && millis() - lastHeartbeat > HEARTBEAT_INTERVAL) {
        lastHeartbeat = millis();
        sendHeartbeat();
    }
    
    delay(10);
}

/**
 * @brief JSON 명령 처리
 * @param command 수신된 JSON 명령 문자열
 */
void processCommand(String command) {
    Serial.println("수신 명령: " + command);
    
    DynamicJsonDocument doc(1024);
    DeserializationError error = deserializeJson(doc, command);
    
    if (error) {
        Serial.println("JSON 파싱 오류: " + String(error.c_str()));
        sendErrorResponse("INVALID_JSON", error.c_str());
        return;
    }
    
    String cmd = doc["command"];
    String requestId = doc["id"];
    
    if (cmd == "START_SEQUENCE") {
        handleStartSequence(doc, requestId);
    }
    else if (cmd == "STOP_SEQUENCE") {
        handleStopSequence(requestId);
    }
    else if (cmd == "SET_LED") {
        handleSetLED(doc, requestId);
    }
    else if (cmd == "GET_STATUS") {
        handleGetStatus(requestId);
    }
    else if (cmd == "PING") {
        handlePing(requestId);
    }
    else {
        sendErrorResponse("UNKNOWN_COMMAND", cmd.c_str(), requestId);
    }
}

/**
 * @brief 시퀀스 시작 명령 처리
 */
void handleStartSequence(DynamicJsonDocument& doc, String requestId) {
    int sequenceType = doc["params"]["type"] | 0;  // 0: random, 1: sequential
    int interval = doc["params"]["interval"] | 800; // ms
    
    if (ledController.startSequence(sequenceType, interval)) {
        sendSuccessResponse("SEQUENCE_STARTED", requestId);
        Serial.println("LED 시퀀스 시작: 타입=" + String(sequenceType) + ", 간격=" + String(interval) + "ms");
    } else {
        sendErrorResponse("SEQUENCE_START_FAILED", "Already running", requestId);
    }
}

/**
 * @brief 시퀀스 정지 명령 처리
 */
void handleStopSequence(String requestId) {
    ledController.stopSequence();
    sendSuccessResponse("SEQUENCE_STOPPED", requestId);
    Serial.println("LED 시퀀스 정지");
}

/**
 * @brief 개별 LED 제어 명령 처리
 */
void handleSetLED(DynamicJsonDocument& doc, String requestId) {
    int pairId = doc["params"]["pair"];
    String color = doc["params"]["color"];
    bool state = doc["params"]["state"];
    
    if (pairId < 0 || pairId >= LED_PAIR_COUNT) {
        sendErrorResponse("INVALID_LED_PAIR", "Pair ID out of range", requestId);
        return;
    }
    
    if (ledController.setLED(pairId, color == "red" ? LED_RED : LED_GREEN, state)) {
        sendSuccessResponse("LED_SET", requestId);
    } else {
        sendErrorResponse("LED_SET_FAILED", "Hardware error", requestId);
    }
}

/**
 * @brief 상태 조회 명령 처리
 */
void handleGetStatus(String requestId) {
    DynamicJsonDocument response(1024);
    response["type"] = "RESPONSE";
    response["id"] = requestId;
    response["status"] = "success";
    response["data"]["connected"] = deviceConnected;
    response["data"]["sequence_running"] = ledController.isSequenceRunning();
    response["data"]["uptime"] = millis();
    response["data"]["free_heap"] = ESP.getFreeHeap();
    
    String output;
    serializeJson(response, output);
    SerialBT.println(output);
}

/**
 * @brief Ping 명령 처리
 */
void handlePing(String requestId) {
    sendSuccessResponse("PONG", requestId);
}

/**
 * @brief 성공 응답 전송
 */
void sendSuccessResponse(String result, String requestId) {
    DynamicJsonDocument response(512);
    response["type"] = "RESPONSE";
    response["id"] = requestId;
    response["status"] = "success";
    response["result"] = result;
    response["timestamp"] = millis();
    
    String output;
    serializeJson(response, output);
    SerialBT.println(output);
}

/**
 * @brief 오류 응답 전송
 */
void sendErrorResponse(String error, String message, String requestId = "") {
    DynamicJsonDocument response(512);
    response["type"] = "RESPONSE";
    response["status"] = "error";
    response["error"] = error;
    response["message"] = message;
    response["timestamp"] = millis();
    
    if (requestId.length() > 0) {
        response["id"] = requestId;
    }
    
    String output;
    serializeJson(response, output);
    SerialBT.println(output);
}

/**
 * @brief 상태 메시지 전송
 */
void sendStatusMessage(String status) {
    DynamicJsonDocument message(256);
    message["type"] = "STATUS";
    message["status"] = status;
    message["timestamp"] = millis();
    
    String output;
    serializeJson(message, output);
    SerialBT.println(output);
}

/**
 * @brief 하트비트 전송
 */
void sendHeartbeat() {
    DynamicJsonDocument heartbeat(256);
    heartbeat["type"] = "HEARTBEAT";
    heartbeat["timestamp"] = millis();
    heartbeat["uptime"] = millis();
    
    String output;
    serializeJson(heartbeat, output);
    SerialBT.println(output);
}