/**
 * @file led_controller.h
 * @brief LED 컨트롤러 클래스 헤더
 * @version 0.1.0
 * @date 2025-08-10
 */

#ifndef LED_CONTROLLER_H
#define LED_CONTROLLER_H

#include <Arduino.h>
#include "config.h"

/**
 * @class LEDController
 * @brief ULN2803A 드라이버를 통한 36쌍 LED 제어 클래스
 */
class LEDController {
private:
    // 현재 LED 상태 저장 (36쌍 × 2색)
    bool ledState[LED_PAIR_COUNT][2];
    
    // 시퀀스 관련 변수
    bool sequenceRunning;
    int sequenceType;
    int sequenceInterval;
    unsigned long lastSequenceTime;
    int currentSequenceIndex;
    int sequenceOrder[LED_PAIR_COUNT];
    
    // 내부 메서드
    void initPins();
    void generateRandomSequence();
    void generateSequentialSequence();
    void setHardwareLED(int ic, int channel, bool state);
    
public:
    LEDController();
    ~LEDController();
    
    // 초기화 및 설정
    bool init();
    void reset();
    
    // LED 제어
    bool setLED(int pairId, int color, bool state);
    bool getLED(int pairId, int color);
    void setAllLEDs(bool state);
    void clearAllLEDs();
    
    // LED 쌍 제어
    bool setPair(int pairId, int color);
    void clearPair(int pairId);
    void clearAllPairs();
    
    // 시퀀스 제어
    bool startSequence(int type = SEQUENCE_RANDOM, int interval = DEFAULT_SEQUENCE_INTERVAL);
    void stopSequence();
    bool isSequenceRunning();
    void update();
    
    // 테스트 기능
    void testSequence();
    void testAllLEDs();
    bool testPair(int pairId);
    
    // 상태 조회
    int getSequenceProgress();
    int getCurrentPair();
    void getStatus(String& statusJson);
};

#endif // LED_CONTROLLER_H