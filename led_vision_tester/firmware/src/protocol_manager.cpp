/*
 * 고도화된 JSON 프로토콜 + ACK 시스템 구현
 * 
 * 작성일: 2025-08-10
 * Phase 2 Week 5
 */

#include "protocol_manager.h"
#include <esp_random.h>

// ================================
// 생성자 및 초기화
// ================================
ProtocolManager::ProtocolManager() :
    btSerial(nullptr),
    messageCounter(0),
    currentState(STATE_IDLE),
    isConnected(false),
    lastHeartbeat(0),
    lastActivity(0),
    onCommand(nullptr),
    onResponse(nullptr),
    onError(nullptr)
{
    // 통계 초기화
    resetStatistics();
}

ProtocolManager::~ProtocolManager() {
    cleanup();
}

bool ProtocolManager::initialize(BluetoothSerial* serial) {
    if (!serial) {
        Serial.println("[Protocol] 오류: Bluetooth Serial이 null입니다");
        return false;
    }
    
    btSerial = serial;
    isConnected = serial->hasClient();
    lastActivity = millis();
    
    Serial.println("[Protocol] 프로토콜 매니저 초기화 완료");
    Serial.printf("[Protocol] 버전: %s\n", PROTOCOL_VERSION);
    
    return true;
}

void ProtocolManager::cleanup() {
    // 대기 중인 ACK 정리
    pendingAcks.clear();
    
    // 메시지 큐 정리
    while (!messageQueue.empty()) {
        messageQueue.pop();
    }
    
    btSerial = nullptr;
    isConnected = false;
    
    Serial.println("[Protocol] 프로토콜 매니저 정리 완료");
}

// ================================
// 유틸리티 함수 구현
// ================================
String ProtocolManager::generateMessageId() {
    char buffer[16];
    sprintf(buffer, "msg_%08X_%04X", 
           (unsigned int)millis(), 
           (unsigned int)(esp_random() & 0xFFFF));
    return String(buffer);
}

String messageTypeToString(MessageType type) {
    switch (type) {
        case MSG_COMMAND: return "COMMAND";
        case MSG_RESPONSE: return "RESPONSE";
        case MSG_ACK: return "ACK";
        case MSG_NACK: return "NACK";
        case MSG_HEARTBEAT: return "HEARTBEAT";
        case MSG_STATUS: return "STATUS";
        case MSG_ERROR: return "ERROR";
        default: return "UNKNOWN";
    }
}

String commandTypeToString(CommandType cmd) {
    switch (cmd) {
        case CMD_SET_LED: return "SET_LED";
        case CMD_START_SEQUENCE: return "START_SEQUENCE";
        case CMD_STOP_SEQUENCE: return "STOP_SEQUENCE";
        case CMD_PAUSE_SEQUENCE: return "PAUSE_SEQUENCE";
        case CMD_RESUME_SEQUENCE: return "RESUME_SEQUENCE";
        case CMD_GET_STATUS: return "GET_STATUS";
        case CMD_SET_CONFIG: return "SET_CONFIG";
        case CMD_GET_CONFIG: return "GET_CONFIG";
        case CMD_CALIBRATE: return "CALIBRATE";
        case CMD_RESET: return "RESET";
        default: return "UNKNOWN";
    }
}

String systemStateToString(SystemState state) {
    switch (state) {
        case STATE_IDLE: return "IDLE";
        case STATE_SEQUENCE_RUNNING: return "SEQUENCE_RUNNING";
        case STATE_SEQUENCE_PAUSED: return "SEQUENCE_PAUSED";
        case STATE_CALIBRATING: return "CALIBRATING";
        case STATE_ERROR: return "ERROR";
        default: return "UNKNOWN";
    }
}

// ================================
// 메시지 전송
// ================================
bool ProtocolManager::sendCommand(CommandType cmd, const JsonDocument& params, bool needsAck) {
    if (!btSerial || !isConnected) {
        Serial.println("[Protocol] 오류: 연결되지 않음");
        return false;
    }
    
    Message msg;
    msg.id = generateMessageId();
    msg.type = MSG_COMMAND;
    msg.command = cmd;
    msg.timestamp = millis();
    msg.needsAck = needsAck;
    msg.retryCount = 0;
    
    // JSON 페이로드 구성
    msg.payload["type"] = messageTypeToString(msg.type);
    msg.payload["id"] = msg.id;
    msg.payload["command"] = commandTypeToString(cmd);
    msg.payload["timestamp"] = msg.timestamp;
    msg.payload["params"] = params;
    msg.payload["version"] = PROTOCOL_VERSION;
    
    if (needsAck) {
        msg.payload["needsAck"] = true;
    }
    
    // JSON 직렬화
    String jsonData;
    serializeJson(msg.payload, jsonData);
    
    // 전송
    sendRawMessage(jsonData);
    
    // ACK가 필요한 경우 대기 목록에 추가
    if (needsAck) {
        PendingAck pending;
        pending.messageId = msg.id;
        pending.sendTime = millis();
        pending.retryCount = 0;
        pending.originalMessage = msg;
        
        pendingAcks[msg.id] = pending;
    }
    
    stats.totalSent++;
    Serial.printf("[Protocol] 명령 전송: %s (ID: %s)\n", 
                  commandTypeToString(cmd).c_str(), msg.id.c_str());
    
    return true;
}

bool ProtocolManager::sendResponse(const String& requestId, bool success, const JsonDocument& data) {
    if (!btSerial || !isConnected) {
        return false;
    }
    
    JsonDocument response;
    response["type"] = "RESPONSE";
    response["id"] = generateMessageId();
    response["requestId"] = requestId;
    response["success"] = success;
    response["timestamp"] = millis();
    response["version"] = PROTOCOL_VERSION;
    
    if (success) {
        response["data"] = data;
    } else {
        response["error"] = data.containsKey("error") ? data["error"] : "Unknown error";
        response["errorCode"] = data.containsKey("errorCode") ? data["errorCode"] : -1;
    }
    
    String jsonData;
    serializeJson(response, jsonData);
    sendRawMessage(jsonData);
    
    stats.totalSent++;
    Serial.printf("[Protocol] 응답 전송: %s (요청 ID: %s)\n", 
                  success ? "성공" : "실패", requestId.c_str());
    
    return true;
}

bool ProtocolManager::sendStatus(const JsonDocument& statusData) {
    if (!btSerial || !isConnected) {
        return false;
    }
    
    JsonDocument status;
    status["type"] = "STATUS";
    status["id"] = generateMessageId();
    status["timestamp"] = millis();
    status["state"] = systemStateToString(currentState);
    status["version"] = PROTOCOL_VERSION;
    status["data"] = statusData;
    
    String jsonData;
    serializeJson(status, jsonData);
    sendRawMessage(jsonData);
    
    stats.totalSent++;
    return true;
}

bool ProtocolManager::sendHeartbeat() {
    if (!btSerial || !isConnected) {
        return false;
    }
    
    JsonDocument heartbeat;
    heartbeat["type"] = "HEARTBEAT";
    heartbeat["id"] = generateMessageId();
    heartbeat["timestamp"] = millis();
    heartbeat["state"] = systemStateToString(currentState);
    heartbeat["version"] = PROTOCOL_VERSION;
    heartbeat["uptime"] = millis();
    heartbeat["freeHeap"] = ESP.getFreeHeap();
    
    String jsonData;
    serializeJson(heartbeat, jsonData);
    sendRawMessage(jsonData);
    
    lastHeartbeat = millis();
    return true;
}

void ProtocolManager::sendRawMessage(const String& jsonData) {
    if (btSerial && isConnected) {
        btSerial->println(jsonData);
        lastActivity = millis();
        
        #ifdef DEBUG_PROTOCOL
        Serial.printf("[Protocol] 전송: %s\n", jsonData.c_str());
        #endif
    }
}

// ================================
// 메시지 수신 및 처리
// ================================
void ProtocolManager::processIncomingData() {
    if (!btSerial || !btSerial->available()) {
        return;
    }
    
    while (btSerial->available()) {
        String rawData = btSerial->readStringUntil('\n');
        rawData.trim();
        
        if (rawData.length() == 0) {
            continue;
        }
        
        lastActivity = millis();
        stats.totalReceived++;
        
        #ifdef DEBUG_PROTOCOL
        Serial.printf("[Protocol] 수신: %s\n", rawData.c_str());
        #endif
        
        Message msg;
        if (parseIncomingMessage(rawData, msg)) {
            processMessage(msg);
        }
    }
}

bool ProtocolManager::parseIncomingMessage(const String& rawData, Message& msg) {
    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, rawData);
    
    if (error) {
        Serial.printf("[Protocol] JSON 파싱 오류: %s\n", error.c_str());
        return false;
    }
    
    // 필수 필드 확인
    if (!doc.containsKey("type") || !doc.containsKey("id")) {
        Serial.println("[Protocol] 필수 필드 누락");
        return false;
    }
    
    msg.id = doc["id"].as<String>();
    String typeStr = doc["type"].as<String>();
    msg.timestamp = doc.containsKey("timestamp") ? doc["timestamp"].as<unsigned long>() : millis();
    msg.payload = doc;
    
    // 메시지 타입 변환
    if (typeStr == "COMMAND") msg.type = MSG_COMMAND;
    else if (typeStr == "RESPONSE") msg.type = MSG_RESPONSE;
    else if (typeStr == "ACK") msg.type = MSG_ACK;
    else if (typeStr == "NACK") msg.type = MSG_NACK;
    else if (typeStr == "HEARTBEAT") msg.type = MSG_HEARTBEAT;
    else if (typeStr == "STATUS") msg.type = MSG_STATUS;
    else if (typeStr == "ERROR") msg.type = MSG_ERROR;
    else {
        Serial.printf("[Protocol] 알 수 없는 메시지 타입: %s\n", typeStr.c_str());
        return false;
    }
    
    return true;
}

void ProtocolManager::processMessage(const Message& msg) {
    // 중복 메시지 확인
    if (msg.id == lastReceivedId) {
        Serial.println("[Protocol] 중복 메시지 무시");
        sendAck(msg.id, true, "Duplicate message");
        return;
    }
    lastReceivedId = msg.id;
    
    switch (msg.type) {
        case MSG_COMMAND:
            handleCommand(msg);
            break;
            
        case MSG_RESPONSE:
            handleResponse(msg);
            break;
            
        case MSG_ACK:
            handleAck(msg);
            break;
            
        case MSG_NACK:
            handleNack(msg);
            break;
            
        case MSG_HEARTBEAT:
            handleHeartbeat(msg);
            break;
            
        case MSG_STATUS:
            handleStatus(msg);
            break;
            
        case MSG_ERROR:
            handleError(msg);
            break;
    }
}

void ProtocolManager::handleCommand(const Message& msg) {
    // 명령 타입 파싱
    String cmdStr = msg.payload["command"].as<String>();
    CommandType cmdType;
    
    if (cmdStr == "SET_LED") cmdType = CMD_SET_LED;
    else if (cmdStr == "START_SEQUENCE") cmdType = CMD_START_SEQUENCE;
    else if (cmdStr == "STOP_SEQUENCE") cmdType = CMD_STOP_SEQUENCE;
    else if (cmdStr == "PAUSE_SEQUENCE") cmdType = CMD_PAUSE_SEQUENCE;
    else if (cmdStr == "RESUME_SEQUENCE") cmdType = CMD_RESUME_SEQUENCE;
    else if (cmdStr == "GET_STATUS") cmdType = CMD_GET_STATUS;
    else if (cmdStr == "SET_CONFIG") cmdType = CMD_SET_CONFIG;
    else if (cmdStr == "GET_CONFIG") cmdType = CMD_GET_CONFIG;
    else if (cmdStr == "CALIBRATE") cmdType = CMD_CALIBRATE;
    else if (cmdStr == "RESET") cmdType = CMD_RESET;
    else {
        sendNack(msg.id, "Unknown command: " + cmdStr);
        return;
    }
    
    // ACK 필요시 전송
    if (msg.payload.containsKey("needsAck") && msg.payload["needsAck"].as<bool>()) {
        sendAck(msg.id);
    }
    
    // 명령 처리 콜백 호출
    if (onCommand) {
        Message cmdMsg = msg;
        cmdMsg.command = cmdType;
        onCommand(cmdMsg);
    }
    
    Serial.printf("[Protocol] 명령 처리: %s (ID: %s)\n", 
                  cmdStr.c_str(), msg.id.c_str());
}

void ProtocolManager::handleAck(const Message& msg) {
    String originalId = msg.payload["originalId"].as<String>();
    
    auto it = pendingAcks.find(originalId);
    if (it != pendingAcks.end()) {
        unsigned long responseTime = millis() - it->second.sendTime;
        
        // 응답 시간 통계 업데이트
        stats.averageResponseTime = (stats.averageResponseTime * stats.totalAcked + responseTime) / (stats.totalAcked + 1);
        stats.totalAcked++;
        
        pendingAcks.erase(it);
        
        Serial.printf("[Protocol] ACK 수신: %s (응답시간: %lums)\n", 
                      originalId.c_str(), responseTime);
    }
}

void ProtocolManager::handleNack(const Message& msg) {
    String originalId = msg.payload["originalId"].as<String>();
    String error = msg.payload["error"].as<String>();
    
    auto it = pendingAcks.find(originalId);
    if (it != pendingAcks.end()) {
        stats.totalNacked++;
        
        // 재전송 시도
        if (it->second.retryCount < MAX_RETRIES) {
            it->second.retryCount++;
            it->second.sendTime = millis();
            
            String jsonData;
            serializeJson(it->second.originalMessage.payload, jsonData);
            sendRawMessage(jsonData);
            
            stats.totalRetries++;
            Serial.printf("[Protocol] NACK 수신, 재전송 시도 %d: %s\n", 
                          it->second.retryCount, originalId.c_str());
        } else {
            // 최대 재전송 횟수 초과
            pendingAcks.erase(it);
            Serial.printf("[Protocol] NACK 수신, 재전송 포기: %s (오류: %s)\n", 
                          originalId.c_str(), error.c_str());
        }
    }
}

// ================================
// ACK 관리
// ================================
void ProtocolManager::sendAck(const String& messageId, bool success, const String& error) {
    JsonDocument ack;
    ack["type"] = success ? "ACK" : "NACK";
    ack["id"] = generateMessageId();
    ack["originalId"] = messageId;
    ack["timestamp"] = millis();
    ack["version"] = PROTOCOL_VERSION;
    
    if (!success && error.length() > 0) {
        ack["error"] = error;
    }
    
    String jsonData;
    serializeJson(ack, jsonData);
    sendRawMessage(jsonData);
}

void ProtocolManager::sendNack(const String& messageId, const String& error) {
    sendAck(messageId, false, error);
}

void ProtocolManager::handleAckTimeout() {
    unsigned long currentTime = millis();
    auto it = pendingAcks.begin();
    
    while (it != pendingAcks.end()) {
        if (currentTime - it->second.sendTime > ACK_TIMEOUT_MS) {
            if (it->second.retryCount < MAX_RETRIES) {
                // 재전송
                it->second.retryCount++;
                it->second.sendTime = currentTime;
                
                String jsonData;
                serializeJson(it->second.originalMessage.payload, jsonData);
                sendRawMessage(jsonData);
                
                stats.totalRetries++;
                Serial.printf("[Protocol] ACK 타임아웃, 재전송 시도 %d: %s\n", 
                              it->second.retryCount, it->first.c_str());
                ++it;
            } else {
                // 포기
                stats.totalTimeouts++;
                Serial.printf("[Protocol] ACK 타임아웃, 포기: %s\n", it->first.c_str());
                it = pendingAcks.erase(it);
            }
        } else {
            ++it;
        }
    }
}

// ================================
// 주기적 업데이트
// ================================
void ProtocolManager::update() {
    // 연결 상태 확인
    bool connected = btSerial && btSerial->hasClient();
    if (connected != isConnected) {
        isConnected = connected;
        Serial.printf("[Protocol] 연결 상태 변경: %s\n", connected ? "연결됨" : "연결 해제됨");
        
        if (!connected) {
            // 연결 해제시 대기 중인 ACK 정리
            pendingAcks.clear();
        }
    }
    
    // 수신 데이터 처리
    processIncomingData();
    
    // ACK 타임아웃 처리
    handleAckTimeout();
    
    // 하트비트 전송
    if (isConnected && (millis() - lastHeartbeat > HEARTBEAT_INTERVAL_MS)) {
        sendHeartbeat();
    }
}

// ================================
// 상태 및 통계 관리
// ================================
bool ProtocolManager::isConnectionActive() {
    return isConnected && (millis() - lastActivity < 30000); // 30초 이내 활동
}

ProtocolManager::Statistics ProtocolManager::getStatistics() {
    return stats;
}

void ProtocolManager::resetStatistics() {
    stats.totalSent = 0;
    stats.totalReceived = 0;
    stats.totalAcked = 0;
    stats.totalNacked = 0;
    stats.totalRetries = 0;
    stats.totalTimeouts = 0;
    stats.averageResponseTime = 0.0;
}

SystemState ProtocolManager::getSystemState() {
    return currentState;
}

void ProtocolManager::setSystemState(SystemState state) {
    if (currentState != state) {
        SystemState oldState = currentState;
        currentState = state;
        
        Serial.printf("[Protocol] 상태 변경: %s -> %s\n", 
                      systemStateToString(oldState).c_str(), 
                      systemStateToString(state).c_str());
        
        // 상태 변경 알림 전송
        JsonDocument statusData;
        statusData["previousState"] = systemStateToString(oldState);
        statusData["newState"] = systemStateToString(state);
        statusData["timestamp"] = millis();
        
        sendStatus(statusData);
    }
}

// ================================
// 콜백 등록
// ================================
void ProtocolManager::setCommandCallback(MessageCallback callback) {
    onCommand = callback;
}

void ProtocolManager::setResponseCallback(MessageCallback callback) {
    onResponse = callback;
}

void ProtocolManager::setErrorCallback(MessageCallback callback) {
    onError = callback;
}

// ================================
// 디버깅 도구
// ================================
void printProtocolStatistics(const ProtocolManager::Statistics& stats) {
    Serial.println("\n=== 프로토콜 통계 ===");
    Serial.printf("전송: %lu, 수신: %lu\n", stats.totalSent, stats.totalReceived);
    Serial.printf("ACK: %lu, NACK: %lu\n", stats.totalAcked, stats.totalNacked);
    Serial.printf("재전송: %lu, 타임아웃: %lu\n", stats.totalRetries, stats.totalTimeouts);
    Serial.printf("평균 응답시간: %.1fms\n", stats.averageResponseTime);
    
    if (stats.totalSent > 0) {
        double successRate = (double)stats.totalAcked / stats.totalSent * 100.0;
        Serial.printf("성공률: %.1f%%\n", successRate);
    }
    Serial.println("===================\n");
}