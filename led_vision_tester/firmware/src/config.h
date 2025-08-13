/**
 * @file config.h
 * @brief 36쌍 LED 시각검사 시제품 - 설정 헤더
 * @version 0.1.0
 * @date 2025-08-10
 */

#ifndef CONFIG_H
#define CONFIG_H

// === 하드웨어 설정 ===

// LED 설정
#define LED_PAIR_COUNT 36
#define LED_RED   0
#define LED_GREEN 1

// ULN2803A 드라이버 설정 (5개 IC 사용)
#define ULN_IC_COUNT 5
#define CHANNELS_PER_IC 8

// GPIO 핀 설정 (ULN2803A 입력 핀)
// IC1: GPIO 2,4,5,12,13,14,15,16 (내경 12쌍 중 8쌍)
// IC2: GPIO 17,18,19,21,22,23,25,26 (내경 4쌍 + 중간 4쌍)  
// IC3: GPIO 27,32,33,34,35,36,39,0 (중간 8쌍)
// IC4: GPIO 1,3,6,7,8,9,10,11 (외곽 8쌍)
// IC5: GPIO 20,24,28,29,30,31,37,38 (외곽 4쌍 + 예비)

const int ULN_PINS[ULN_IC_COUNT][CHANNELS_PER_IC] = {
    {2, 4, 5, 12, 13, 14, 15, 16},    // IC1
    {17, 18, 19, 21, 22, 23, 25, 26}, // IC2  
    {27, 32, 33, 34, 35, 36, 39, 0},  // IC3
    {1, 3, 6, 7, 8, 9, 10, 11},       // IC4
    {20, 24, 28, 29, 30, 31, 37, 38}  // IC5
};

// LED 쌍 배치 (3개 동심원)
#define INNER_CIRCLE_PAIRS 12   // 내경 12쌍
#define MIDDLE_CIRCLE_PAIRS 12  // 중간 12쌍  
#define OUTER_CIRCLE_PAIRS 12   // 외곽 12쌍

// LED 쌍 매핑 (쌍 번호 -> ULN IC/채널)
typedef struct {
    int ic;       // ULN IC 번호 (0-4)
    int channel;  // 채널 번호 (0-7)
} LEDMapping;

// 36쌍 LED 매핑 테이블
extern const LEDMapping LED_MAP[LED_PAIR_COUNT];

// === 통신 설정 ===

// Bluetooth 설정
#define BT_DEVICE_NAME "LED_Vision_Tester"
#define BT_PIN "1234"

// 통신 타임아웃
#define BT_TIMEOUT 5000         // ms
#define HEARTBEAT_INTERVAL 5000 // ms

// === 시퀀스 설정 ===

// 시퀀스 타입
#define SEQUENCE_RANDOM 0
#define SEQUENCE_SEQUENTIAL 1

// 기본 점등 시간
#define DEFAULT_LED_ON_TIME 800   // ms
#define MIN_LED_ON_TIME 100       // ms  
#define MAX_LED_ON_TIME 5000      // ms

// 시퀀스 간격
#define DEFAULT_SEQUENCE_INTERVAL 800 // ms
#define MIN_SEQUENCE_INTERVAL 200     // ms
#define MAX_SEQUENCE_INTERVAL 3000    // ms

// === 디버그 설정 ===

#define DEBUG_ENABLED 1
#define DEBUG_LED_CONTROL 1
#define DEBUG_BLUETOOTH 1
#define DEBUG_JSON 1

#if DEBUG_ENABLED
    #define DEBUG_PRINT(x) Serial.print(x)
    #define DEBUG_PRINTLN(x) Serial.println(x)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
#endif

#endif // CONFIG_H