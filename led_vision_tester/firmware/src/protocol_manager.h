/*
 * 고도화된 JSON 프로토콜 + ACK 시스템
 * 
 * 기능:
 * - 신뢰성 있는 양방향 통신
 * - 메시지 순서 보장 
 * - 타임아웃 및 재전송
 * - 오류 복구 메커니즘
 * - 상태 동기화
 * 
 * 작성일: 2025-08-10
 * Phase 2 Week 5
 */

#ifndef PROTOCOL_MANAGER_H
#define PROTOCOL_MANAGER_H

#include <Arduino.h>
#include <ArduinoJson.h>
#include <BluetoothSerial.h>
#include <map>
#include <queue>

// ================================
// 프로토콜 상수 정의
// ================================
#define PROTOCOL_VERSION "2.0"
#define MAX_MESSAGE_SIZE 512
#define ACK_TIMEOUT_MS 3000
#define MAX_RETRIES 3
#define HEARTBEAT_INTERVAL_MS 5000
#define MESSAGE_QUEUE_SIZE 10

// 메시지 타입
enum MessageType {
    MSG_COMMAND,        // 명령 메시지
    MSG_RESPONSE,       // 응답 메시지  
    MSG_ACK,           // 확인 메시지
    MSG_NACK,          // 오류 메시지
    MSG_HEARTBEAT,     // 하트비트
    MSG_STATUS,        // 상태 알림
    MSG_ERROR          // 시스템 오류
};

// 명령 타입
enum CommandType {
    CMD_SET_LED,           // LED 개별 제어
    CMD_START_SEQUENCE,    // 시퀀스 시작
    CMD_STOP_SEQUENCE,     // 시퀀스 중지
    CMD_PAUSE_SEQUENCE,    // 시퀀스 일시정지
    CMD_RESUME_SEQUENCE,   // 시퀀스 재개
    CMD_GET_STATUS,        // 상태 조회
    CMD_SET_CONFIG,        // 설정 변경
    CMD_GET_CONFIG,        // 설정 조회
    CMD_CALIBRATE,         // 캘리브레이션
    CMD_RESET              // 시스템 리셋
};

// 시스템 상태
enum SystemState {
    STATE_IDLE,            // 대기 상태
    STATE_SEQUENCE_RUNNING,// 시퀀스 실행 중
    STATE_SEQUENCE_PAUSED, // 시퀀스 일시정지
    STATE_CALIBRATING,     // 캘리브레이션 중
    STATE_ERROR           // 오류 상태
};

// 메시지 구조체
struct Message {
    String id;                // 고유 ID
    MessageType type;         // 메시지 타입
    CommandType command;      // 명령 타입 (해당시)
    JsonDocument payload;     // 페이로드
    unsigned long timestamp;  // 타임스탬프
    int retryCount;          // 재전송 횟수
    bool needsAck;           // ACK 필요 여부
};

// ACK 대기 정보
struct PendingAck {
    String messageId;
    unsigned long sendTime;
    int retryCount;
    Message originalMessage;
};

// ================================
// ProtocolManager 클래스
// ================================
class ProtocolManager {
private:
    BluetoothSerial* btSerial;
    
    // 메시지 관리
    std::queue<Message> messageQueue;
    std::map<String, PendingAck> pendingAcks;
    String lastReceivedId;
    unsigned int messageCounter;
    
    // 시스템 상태
    SystemState currentState;
    bool isConnected;
    unsigned long lastHeartbeat;
    unsigned long lastActivity;
    
    // 콜백 함수 포인터
    typedef void (*MessageCallback)(const Message& msg);
    MessageCallback onCommand;
    MessageCallback onResponse;
    MessageCallback onError;
    
    // 내부 메소드
    String generateMessageId();
    bool parseIncomingMessage(const String& rawData, Message& msg);
    void sendRawMessage(const String& jsonData);
    void handleAckTimeout();
    void processMessageQueue();
    void sendAck(const String& messageId, bool success = true, const String& error = "");
    void sendNack(const String& messageId, const String& error);
    
public:
    ProtocolManager();
    ~ProtocolManager();
    
    // 초기화 및 해제
    bool initialize(BluetoothSerial* serial);
    void cleanup();
    
    // 연결 관리
    bool isConnectionActive();
    void setConnectionState(bool connected);
    unsigned long getLastActivityTime();
    
    // 메시지 전송
    bool sendCommand(CommandType cmd, const JsonDocument& params, bool needsAck = true);
    bool sendResponse(const String& requestId, bool success, const JsonDocument& data = JsonDocument());
    bool sendStatus(const JsonDocument& statusData);
    bool sendHeartbeat();
    
    // 메시지 수신 처리
    void processIncomingData();
    void update(); // 주기적 호출 필요
    
    // 콜백 등록
    void setCommandCallback(MessageCallback callback);
    void setResponseCallback(MessageCallback callback);
    void setErrorCallback(MessageCallback callback);
    
    // 상태 관리
    SystemState getSystemState();
    void setSystemState(SystemState state);
    
    // 설정
    void setAckTimeout(unsigned long timeout);
    void setMaxRetries(int retries);
    void setHeartbeatInterval(unsigned long interval);
    
    // 통계 정보
    struct Statistics {
        unsigned long totalSent;
        unsigned long totalReceived;
        unsigned long totalAcked;
        unsigned long totalNacked;
        unsigned long totalRetries;
        unsigned long totalTimeouts;
        double averageResponseTime;
    } stats;
    
    Statistics getStatistics();
    void resetStatistics();
};

// ================================
// 유틸리티 함수
// ================================
String messageTypeToString(MessageType type);
String commandTypeToString(CommandType cmd);
String systemStateToString(SystemState state);
MessageType stringToMessageType(const String& str);
CommandType stringToCommandType(const String& str);
SystemState stringToSystemState(const String& str);

// JSON 헬퍼 함수
JsonDocument createCommandMessage(CommandType cmd, const JsonDocument& params);
JsonDocument createResponseMessage(const String& requestId, bool success, const JsonDocument& data);
JsonDocument createStatusMessage(SystemState state, const JsonDocument& additionalData);
JsonDocument createErrorMessage(const String& error, int errorCode = 0);

// 메시지 검증
bool validateMessage(const JsonDocument& doc);
bool validateCommand(CommandType cmd, const JsonDocument& params);

// 디버깅 도구
void printMessage(const Message& msg);
void printProtocolStatistics(const ProtocolManager::Statistics& stats);

#endif // PROTOCOL_MANAGER_H