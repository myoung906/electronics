/**
 * @file bluetooth_manager.h
 * @brief Bluetooth 통신 관리자 헤더
 * @version 0.1.0
 * @date 2025-08-10
 */

#ifndef BLUETOOTH_MANAGER_H
#define BLUETOOTH_MANAGER_H

#include <Arduino.h>
#include <BluetoothSerial.h>
#include "config.h"

/**
 * @class BluetoothManager
 * @brief ESP32 Bluetooth SPP 통신 관리 클래스
 */
class BluetoothManager {
private:
    BluetoothSerial* serialBT;
    String deviceName;
    bool initialized;
    bool connected;
    
    // 연결 콜백
    unsigned long lastConnectionCheck;
    unsigned long connectionTimeout;
    
    // 데이터 버퍼
    String receivedData;
    String sendBuffer;
    
    // 내부 메서드
    void onConnect();
    void onDisconnect();
    bool isValidJson(const String& data);
    
public:
    BluetoothManager();
    ~BluetoothManager();
    
    // 초기화 및 설정
    bool init(BluetoothSerial* bt, const String& name);
    void reset();
    bool isInitialized();
    
    // 연결 관리
    bool isConnected();
    void checkConnection();
    String getDeviceName();
    
    // 데이터 송수신
    bool sendMessage(const String& message);
    bool sendJson(const String& jsonData);
    String receiveMessage();
    bool hasIncomingData();
    
    // JSON 메시지 처리
    bool sendResponse(const String& type, const String& data, const String& requestId = "");
    bool sendError(const String& error, const String& message, const String& requestId = "");
    bool sendStatus(const String& status);
    bool sendHeartbeat();
    
    // 통신 상태
    void getConnectionInfo(String& infoJson);
    int getSignalStrength();
    unsigned long getUptime();
    
    // 콜백 함수 등록
    typedef void (*ConnectionCallback)(bool connected);
    void setConnectionCallback(ConnectionCallback callback);
    
private:
    ConnectionCallback connectionCallback;
};

#endif // BLUETOOTH_MANAGER_H