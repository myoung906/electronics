/**
 * @file led_controller.cpp
 * @brief LED 컨트롤러 클래스 구현
 * @version 0.1.0
 * @date 2025-08-10
 */

#include "led_controller.h"
#include <ArduinoJson.h>

// LED 쌍 매핑 테이블 (36쌍)
const LEDMapping LED_MAP[LED_PAIR_COUNT] = {
    // 내경 12쌍 (0-11)
    {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7},  // IC1의 8채널
    {1, 0}, {1, 1}, {1, 2}, {1, 3},                                  // IC2의 첫 4채널
    
    // 중간 12쌍 (12-23)  
    {1, 4}, {1, 5}, {1, 6}, {1, 7},                                  // IC2의 마지막 4채널
    {2, 0}, {2, 1}, {2, 2}, {2, 3}, {2, 4}, {2, 5}, {2, 6}, {2, 7}, // IC3의 8채널
    
    // 외곽 12쌍 (24-35)
    {3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4}, {3, 5}, {3, 6}, {3, 7}, // IC4의 8채널
    {4, 0}, {4, 1}, {4, 2}, {4, 3}                                   // IC5의 첫 4채널
};

/**
 * @brief 생성자
 */
LEDController::LEDController() {
    sequenceRunning = false;
    sequenceType = SEQUENCE_RANDOM;
    sequenceInterval = DEFAULT_SEQUENCE_INTERVAL;
    lastSequenceTime = 0;
    currentSequenceIndex = 0;
    
    // LED 상태 초기화
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        ledState[i][LED_RED] = false;
        ledState[i][LED_GREEN] = false;
    }
}

/**
 * @brief 소멸자
 */
LEDController::~LEDController() {
    clearAllLEDs();
}

/**
 * @brief LED 컨트롤러 초기화
 * @return 초기화 성공 여부
 */
bool LEDController::init() {
    DEBUG_PRINTLN("LED Controller 초기화 시작");
    
    try {
        initPins();
        clearAllLEDs();
        
        DEBUG_PRINTLN("LED Controller 초기화 완료");
        return true;
    }
    catch (...) {
        DEBUG_PRINTLN("LED Controller 초기화 실패");
        return false;
    }
}

/**
 * @brief GPIO 핀 초기화
 */
void LEDController::initPins() {
    DEBUG_PRINTLN("GPIO 핀 초기화");
    
    for (int ic = 0; ic < ULN_IC_COUNT; ic++) {
        for (int ch = 0; ch < CHANNELS_PER_IC; ch++) {
            int pin = ULN_PINS[ic][ch];
            pinMode(pin, OUTPUT);
            digitalWrite(pin, LOW);  // ULN2803A는 LOW에서 LED OFF
            
            #if DEBUG_LED_CONTROL
                DEBUG_PRINT("IC");
                DEBUG_PRINT(ic);
                DEBUG_PRINT(" CH");
                DEBUG_PRINT(ch);
                DEBUG_PRINT(" -> GPIO");
                DEBUG_PRINTLN(pin);
            #endif
        }
    }
}

/**
 * @brief 시스템 리셋
 */
void LEDController::reset() {
    stopSequence();
    clearAllLEDs();
    DEBUG_PRINTLN("LED Controller 리셋 완료");
}

/**
 * @brief 개별 LED 제어
 * @param pairId LED 쌍 번호 (0-35)
 * @param color LED 색상 (LED_RED/LED_GREEN)
 * @param state LED 상태 (true=ON, false=OFF)
 * @return 제어 성공 여부
 */
bool LEDController::setLED(int pairId, int color, bool state) {
    if (pairId < 0 || pairId >= LED_PAIR_COUNT) {
        DEBUG_PRINTLN("ERROR: 잘못된 LED 쌍 번호: " + String(pairId));
        return false;
    }
    
    if (color != LED_RED && color != LED_GREEN) {
        DEBUG_PRINTLN("ERROR: 잘못된 LED 색상: " + String(color));
        return false;
    }
    
    // 해당 쌍의 다른 색상 LED 끄기 (동시 점등 방지)
    if (state) {
        int otherColor = (color == LED_RED) ? LED_GREEN : LED_RED;
        if (ledState[pairId][otherColor]) {
            setLED(pairId, otherColor, false);
        }
    }
    
    // LED 상태 업데이트
    ledState[pairId][color] = state;
    
    // 하드웨어 제어
    LEDMapping mapping = LED_MAP[pairId];
    int ic = mapping.ic;
    int channel = mapping.channel;
    
    // RED/GREEN 채널 계산 (각 쌍마다 연속된 2개 채널 사용)
    int actualChannel = channel * 2 + color;  // RED=0, GREEN=1
    
    setHardwareLED(ic, actualChannel, state);
    
    #if DEBUG_LED_CONTROL
        DEBUG_PRINT("LED ");
        DEBUG_PRINT(pairId);
        DEBUG_PRINT(color == LED_RED ? "R" : "G");
        DEBUG_PRINT(state ? " ON" : " OFF");
        DEBUG_PRINT(" -> IC");
        DEBUG_PRINT(ic);
        DEBUG_PRINT(" CH");
        DEBUG_PRINTLN(actualChannel);
    #endif
    
    return true;
}

/**
 * @brief LED 상태 조회
 * @param pairId LED 쌍 번호
 * @param color LED 색상
 * @return LED 상태
 */
bool LEDController::getLED(int pairId, int color) {
    if (pairId < 0 || pairId >= LED_PAIR_COUNT) return false;
    if (color != LED_RED && color != LED_GREEN) return false;
    
    return ledState[pairId][color];
}

/**
 * @brief 모든 LED 제어
 * @param state LED 상태
 */
void LEDController::setAllLEDs(bool state) {
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        setLED(i, LED_RED, state);
        setLED(i, LED_GREEN, state);
    }
}

/**
 * @brief 모든 LED 끄기
 */
void LEDController::clearAllLEDs() {
    setAllLEDs(false);
    DEBUG_PRINTLN("모든 LED 끄기");
}

/**
 * @brief LED 쌍 제어 (하나의 색상만)
 * @param pairId LED 쌍 번호
 * @param color LED 색상
 */
bool LEDController::setPair(int pairId, int color) {
    return setLED(pairId, color, true);
}

/**
 * @brief LED 쌍 끄기
 * @param pairId LED 쌍 번호
 */
void LEDController::clearPair(int pairId) {
    setLED(pairId, LED_RED, false);
    setLED(pairId, LED_GREEN, false);
}

/**
 * @brief 모든 LED 쌍 끄기
 */
void LEDController::clearAllPairs() {
    clearAllLEDs();
}

/**
 * @brief 시퀀스 시작
 * @param type 시퀀스 타입 (RANDOM/SEQUENTIAL)
 * @param interval 점등 간격 (ms)
 * @return 시작 성공 여부
 */
bool LEDController::startSequence(int type, int interval) {
    if (sequenceRunning) {
        DEBUG_PRINTLN("ERROR: 시퀀스가 이미 실행 중입니다");
        return false;
    }
    
    // 매개변수 검증
    if (interval < MIN_SEQUENCE_INTERVAL || interval > MAX_SEQUENCE_INTERVAL) {
        DEBUG_PRINTLN("ERROR: 잘못된 시퀀스 간격: " + String(interval));
        return false;
    }
    
    sequenceType = type;
    sequenceInterval = interval;
    currentSequenceIndex = 0;
    lastSequenceTime = millis();
    
    // 시퀀스 순서 생성
    if (type == SEQUENCE_RANDOM) {
        generateRandomSequence();
        DEBUG_PRINTLN("무작위 시퀀스 생성");
    } else {
        generateSequentialSequence();
        DEBUG_PRINTLN("순차 시퀀스 생성");
    }
    
    sequenceRunning = true;
    clearAllLEDs();
    
    DEBUG_PRINT("LED 시퀀스 시작: 간격=");
    DEBUG_PRINT(interval);
    DEBUG_PRINTLN("ms");
    
    return true;
}

/**
 * @brief 시퀀스 정지
 */
void LEDController::stopSequence() {
    sequenceRunning = false;
    clearAllLEDs();
    DEBUG_PRINTLN("LED 시퀀스 정지");
}

/**
 * @brief 시퀀스 실행 상태 확인
 * @return 실행 상태
 */
bool LEDController::isSequenceRunning() {
    return sequenceRunning;
}

/**
 * @brief 시퀀스 업데이트 (메인 루프에서 호출)
 */
void LEDController::update() {
    if (!sequenceRunning) return;
    
    unsigned long currentTime = millis();
    
    // 시퀀스 간격 확인
    if (currentTime - lastSequenceTime >= sequenceInterval) {
        lastSequenceTime = currentTime;
        
        // 이전 LED 끄기
        clearAllLEDs();
        
        // 현재 LED 켜기
        int pairId = sequenceOrder[currentSequenceIndex];
        int color = (currentSequenceIndex % 2 == 0) ? LED_RED : LED_GREEN;  // 교대로 색상 변경
        
        setPair(pairId, color);
        
        #if DEBUG_LED_CONTROL
            DEBUG_PRINT("시퀀스: ");
            DEBUG_PRINT(currentSequenceIndex + 1);
            DEBUG_PRINT("/");
            DEBUG_PRINT(LED_PAIR_COUNT);
            DEBUG_PRINT(" - 쌍 ");
            DEBUG_PRINT(pairId);
            DEBUG_PRINTLN(color == LED_RED ? " 빨강" : " 녹색");
        #endif
        
        // 다음 인덱스
        currentSequenceIndex++;
        
        // 시퀀스 완료 확인
        if (currentSequenceIndex >= LED_PAIR_COUNT) {
            stopSequence();
            DEBUG_PRINTLN("시퀀스 완료");
        }
    }
}

/**
 * @brief 무작위 시퀀스 생성
 */
void LEDController::generateRandomSequence() {
    // 순차 배열 생성
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        sequenceOrder[i] = i;
    }
    
    // Fisher-Yates 셔플 알고리즘
    for (int i = LED_PAIR_COUNT - 1; i > 0; i--) {
        int j = random(i + 1);
        
        // 교환
        int temp = sequenceOrder[i];
        sequenceOrder[i] = sequenceOrder[j];
        sequenceOrder[j] = temp;
    }
    
    DEBUG_PRINTLN("무작위 시퀀스 생성 완료");
}

/**
 * @brief 순차 시퀀스 생성
 */
void LEDController::generateSequentialSequence() {
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        sequenceOrder[i] = i;
    }
    
    DEBUG_PRINTLN("순차 시퀀스 생성 완료");
}

/**
 * @brief 하드웨어 LED 제어
 * @param ic ULN IC 번호
 * @param channel 채널 번호
 * @param state LED 상태
 */
void LEDController::setHardwareLED(int ic, int channel, bool state) {
    if (ic < 0 || ic >= ULN_IC_COUNT) return;
    if (channel < 0 || channel >= CHANNELS_PER_IC) return;
    
    int pin = ULN_PINS[ic][channel];
    digitalWrite(pin, state ? HIGH : LOW);  // ULN2803A는 HIGH에서 LED ON
}

/**
 * @brief 테스트 시퀀스 실행
 */
void LEDController::testSequence() {
    DEBUG_PRINTLN("LED 테스트 시퀀스 시작");
    
    // 각 쌍별 테스트
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        clearAllLEDs();
        
        // 빨강 LED 테스트
        setPair(i, LED_RED);
        delay(200);
        
        // 녹색 LED 테스트  
        clearPair(i);
        setPair(i, LED_GREEN);
        delay(200);
        
        clearPair(i);
        delay(100);
    }
    
    clearAllLEDs();
    DEBUG_PRINTLN("LED 테스트 시퀀스 완료");
}

/**
 * @brief 모든 LED 테스트
 */
void LEDController::testAllLEDs() {
    DEBUG_PRINTLN("전체 LED 테스트");
    
    // 모든 빨강 LED
    setAllLEDs(false);
    delay(500);
    
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        setLED(i, LED_RED, true);
    }
    delay(1000);
    
    // 모든 녹색 LED
    setAllLEDs(false);
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        setLED(i, LED_GREEN, true);
    }
    delay(1000);
    
    clearAllLEDs();
    DEBUG_PRINTLN("전체 LED 테스트 완료");
}

/**
 * @brief 개별 LED 쌍 테스트
 * @param pairId LED 쌍 번호
 * @return 테스트 성공 여부
 */
bool LEDController::testPair(int pairId) {
    if (pairId < 0 || pairId >= LED_PAIR_COUNT) return false;
    
    DEBUG_PRINT("LED 쌍 ");
    DEBUG_PRINT(pairId);
    DEBUG_PRINTLN(" 테스트");
    
    clearAllLEDs();
    
    // 빨강 테스트
    setPair(pairId, LED_RED);
    delay(500);
    
    // 녹색 테스트
    clearPair(pairId);
    setPair(pairId, LED_GREEN);
    delay(500);
    
    clearPair(pairId);
    return true;
}

/**
 * @brief 시퀀스 진행률 조회
 * @return 진행률 (0-100%)
 */
int LEDController::getSequenceProgress() {
    if (!sequenceRunning || LED_PAIR_COUNT == 0) return 0;
    return (currentSequenceIndex * 100) / LED_PAIR_COUNT;
}

/**
 * @brief 현재 점등 중인 LED 쌍 번호
 * @return LED 쌍 번호
 */
int LEDController::getCurrentPair() {
    if (!sequenceRunning || currentSequenceIndex >= LED_PAIR_COUNT) return -1;
    return sequenceOrder[currentSequenceIndex];
}

/**
 * @brief 상태 정보를 JSON으로 반환
 * @param statusJson 출력 JSON 문자열
 */
void LEDController::getStatus(String& statusJson) {
    DynamicJsonDocument doc(1024);
    
    doc["sequence_running"] = sequenceRunning;
    doc["sequence_type"] = sequenceType;
    doc["sequence_interval"] = sequenceInterval;
    doc["progress"] = getSequenceProgress();
    doc["current_pair"] = getCurrentPair();
    doc["total_pairs"] = LED_PAIR_COUNT;
    
    // LED 상태 배열
    JsonArray ledsRed = doc.createNestedArray("leds_red");
    JsonArray ledsGreen = doc.createNestedArray("leds_green");
    
    for (int i = 0; i < LED_PAIR_COUNT; i++) {
        ledsRed.add(ledState[i][LED_RED]);
        ledsGreen.add(ledState[i][LED_GREEN]);
    }
    
    serializeJson(doc, statusJson);
}