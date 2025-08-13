/**
 * @file bluetooth_manager.cpp
 * @brief Bluetooth 통신 관리자 구현
 * @version 0.1.0
 * @date 2025-08-10
 */

#include "bluetooth_manager.h"
#include <ArduinoJson.h>

/**
 * @brief 생성자
 */
BluetoothManager::BluetoothManager() {
    serialBT = nullptr;
    initialized = false;
    connected = false;
    lastConnectionCheck = 0;
    connectionTimeout = BT_TIMEOUT;
    connectionCallback = nullptr;
    deviceName = "";
    receivedData = "";
    sendBuffer = "";
}

/**
 * @brief 소멸자
 */
BluetoothManager::~BluetoothManager() {
    if (initialized && serialBT) {
        serialBT->end();
    }
}

/**
 * @brief Bluetooth 초기화
 * @param bt BluetoothSerial 객체 포인터
 * @param name 장치 이름
 * @return 초기화 성공 여부
 */
bool BluetoothManager::init(BluetoothSerial* bt, const String& name) {
    if (bt == nullptr) {
        DEBUG_PRINTLN("ERROR: BluetoothSerial 객체가 null입니다");
        return false;
    }
    
    serialBT = bt;
    deviceName = name;
    
    DEBUG_PRINT("Bluetooth 초기화: ");
    DEBUG_PRINTLN(deviceName);
    
    // Bluetooth 시작
    if (!serialBT->begin(deviceName)) {
        DEBUG_PRINTLN("ERROR: Bluetooth 시작 실패");
        return false;
    }
    
    // PIN 설정 (필요시)
    // serialBT->setPin(BT_PIN);
    
    initialized = true;
    lastConnectionCheck = millis();
    
    DEBUG_PRINTLN("Bluetooth 초기화 완료");
    DEBUG_PRINT("장치 이름: ");
    DEBUG_PRINTLN(deviceName);
    
    return true;
}

/**
 * @brief Bluetooth 리셋
 */
void BluetoothManager::reset() {
    if (initialized && serialBT) {
        serialBT->end();
        delay(100);
        serialBT->begin(deviceName);
    }
    
    connected = false;
    receivedData = "";
    sendBuffer = "";
    
    DEBUG_PRINTLN("Bluetooth 리셋 완료");
}

/**
 * @brief 초기화 상태 확인
 * @return 초기화 여부
 */
bool BluetoothManager::isInitialized() {
    return initialized;
}

/**
 * @brief 연결 상태 확인
 * @return 연결 여부
 */
bool BluetoothManager::isConnected() {
    if (!initialized || !serialBT) return false;
    
    bool currentlyConnected = serialBT->hasClient();
    
    // 연결 상태 변화 감지
    if (currentlyConnected != connected) {
        connected = currentlyConnected;
        
        if (connected) {
            onConnect();
        } else {
            onDisconnect();
        }
        
        // 콜백 호출
        if (connectionCallback) {
            connectionCallback(connected);
        }
    }
    
    return connected;
}

/**
 * @brief 연결 상태 주기적 확인
 */
void BluetoothManager::checkConnection() {
    unsigned long currentTime = millis();
    
    if (currentTime - lastConnectionCheck > 1000) {  // 1초마다 확인
        lastConnectionCheck = currentTime;
        isConnected();
    }
}

/**
 * @brief 장치 이름 조회
 * @return 장치 이름
 */
String BluetoothManager::getDeviceName() {
    return deviceName;
}

/**
 * @brief 메시지 송신
 * @param message 송신할 메시지
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendMessage(const String& message) {
    if (!connected || !serialBT) {
        #if DEBUG_BLUETOOTH
            DEBUG_PRINTLN("WARNING: Bluetooth 연결되지 않음 - 메시지 송신 실패");
        #endif
        return false;
    }
    
    try {
        serialBT->println(message);
        
        #if DEBUG_BLUETOOTH
            DEBUG_PRINT("BT 송신: ");
            DEBUG_PRINTLN(message.substring(0, 100) + (message.length() > 100 ? "..." : ""));
        #endif
        
        return true;
    }
    catch (...) {
        DEBUG_PRINTLN("ERROR: Bluetooth 송신 실패");
        return false;
    }
}

/**
 * @brief JSON 메시지 송신
 * @param jsonData JSON 문자열
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendJson(const String& jsonData) {
    if (!isValidJson(jsonData)) {
        DEBUG_PRINTLN("ERROR: 잘못된 JSON 형식");
        return false;
    }
    
    return sendMessage(jsonData);
}

/**
 * @brief 메시지 수신
 * @return 수신된 메시지
 */
String BluetoothManager::receiveMessage() {
    if (!connected || !serialBT) return "";
    
    String message = "";
    
    while (serialBT->available()) {
        char c = serialBT->read();
        
        if (c == '\n' || c == '\r') {
            if (receivedData.length() > 0) {
                message = receivedData;
                receivedData = "";
                break;
            }
        } else {
            receivedData += c;
        }
        
        // 버퍼 오버플로우 방지
        if (receivedData.length() > 2048) {
            DEBUG_PRINTLN("WARNING: 수신 버퍼 오버플로우");
            receivedData = "";
            break;
        }
    }
    
    if (message.length() > 0) {
        #if DEBUG_BLUETOOTH
            DEBUG_PRINT("BT 수신: ");
            DEBUG_PRINTLN(message.substring(0, 100) + (message.length() > 100 ? "..." : ""));
        #endif
    }
    
    return message;
}

/**
 * @brief 수신 데이터 존재 여부
 * @return 수신 데이터 유무
 */
bool BluetoothManager::hasIncomingData() {
    if (!connected || !serialBT) return false;
    return serialBT->available() > 0;
}

/**
 * @brief 응답 메시지 송신
 * @param type 응답 타입
 * @param data 응답 데이터
 * @param requestId 요청 ID (선택사항)
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendResponse(const String& type, const String& data, const String& requestId) {
    DynamicJsonDocument response(1024);
    
    response["type"] = "RESPONSE";
    response["response_type"] = type;
    response["timestamp"] = millis();
    
    if (requestId.length() > 0) {
        response["id"] = requestId;
    }
    
    // 데이터가 JSON 형식인지 확인
    if (isValidJson(data)) {
        DynamicJsonDocument dataDoc(512);
        deserializeJson(dataDoc, data);
        response["data"] = dataDoc;
    } else {
        response["data"] = data;
    }
    
    String output;
    serializeJson(response, output);
    
    return sendMessage(output);
}

/**
 * @brief 오류 메시지 송신
 * @param error 오류 코드
 * @param message 오류 메시지
 * @param requestId 요청 ID (선택사항)
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendError(const String& error, const String& message, const String& requestId) {
    DynamicJsonDocument errorDoc(512);
    
    errorDoc["type"] = "ERROR";
    errorDoc["error"] = error;
    errorDoc["message"] = message;
    errorDoc["timestamp"] = millis();
    
    if (requestId.length() > 0) {
        errorDoc["id"] = requestId;
    }
    
    String output;
    serializeJson(errorDoc, output);
    
    return sendMessage(output);
}

/**
 * @brief 상태 메시지 송신
 * @param status 상태 정보
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendStatus(const String& status) {
    DynamicJsonDocument statusDoc(256);
    
    statusDoc["type"] = "STATUS";
    statusDoc["status"] = status;
    statusDoc["timestamp"] = millis();
    
    String output;
    serializeJson(statusDoc, output);
    
    return sendMessage(output);
}

/**
 * @brief 하트비트 송신
 * @return 송신 성공 여부
 */
bool BluetoothManager::sendHeartbeat() {
    DynamicJsonDocument heartbeat(256);
    
    heartbeat["type"] = "HEARTBEAT";
    heartbeat["timestamp"] = millis();
    heartbeat["device"] = deviceName;
    heartbeat["uptime"] = getUptime();
    
    String output;
    serializeJson(heartbeat, output);
    
    return sendMessage(output);
}

/**
 * @brief 연결 정보 조회
 * @param infoJson 출력 JSON 문자열
 */
void BluetoothManager::getConnectionInfo(String& infoJson) {
    DynamicJsonDocument doc(512);
    
    doc["connected"] = connected;
    doc["device_name"] = deviceName;
    doc["initialized"] = initialized;
    doc["uptime"] = getUptime();
    doc["last_check"] = lastConnectionCheck;
    
    serializeJson(doc, infoJson);
}

/**
 * @brief 신호 강도 조회 (ESP32는 직접 지원하지 않음)
 * @return 신호 강도 (임시값)
 */
int BluetoothManager::getSignalStrength() {
    // ESP32 Bluetooth Classic은 RSSI 직접 조회 불가
    // 연결 상태 기반 추정값 반환
    return connected ? -50 : -100;
}

/**
 * @brief 업타임 조회
 * @return 업타임 (ms)
 */
unsigned long BluetoothManager::getUptime() {
    return millis();
}

/**
 * @brief 연결 콜백 함수 등록
 * @param callback 콜백 함수
 */
void BluetoothManager::setConnectionCallback(ConnectionCallback callback) {
    connectionCallback = callback;
}

/**
 * @brief 연결 시 호출되는 내부 메서드
 */
void BluetoothManager::onConnect() {
    #if DEBUG_BLUETOOTH
        DEBUG_PRINTLN("Bluetooth 클라이언트 연결됨");
    #endif
    
    // 연결 환영 메시지
    sendStatus("CONNECTED");
}

/**
 * @brief 연결 해제 시 호출되는 내부 메서드
 */
void BluetoothManager::onDisconnect() {
    #if DEBUG_BLUETOOTH
        DEBUG_PRINTLN("Bluetooth 클라이언트 연결 해제됨");
    #endif
    
    // 버퍼 정리
    receivedData = "";
    sendBuffer = "";
}

/**
 * @brief JSON 형식 유효성 확인
 * @param data 확인할 데이터
 * @return JSON 유효성
 */
bool BluetoothManager::isValidJson(const String& data) {
    DynamicJsonDocument testDoc(256);
    DeserializationError error = deserializeJson(testDoc, data);
    return error == DeserializationError::Ok;
}