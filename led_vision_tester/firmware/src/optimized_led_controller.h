/*
 * 최적화된 LED 컨트롤러 (36쌍 지원)
 * 
 * 최적화 요소:
 * - 메모리 효율적인 비트맵 기반 LED 상태 관리
 * - 하드웨어 타이머를 이용한 정밀한 시퀀스 제어
 * - DMA를 활용한 고속 GPIO 제어
 * - 동적 메모리 할당 최소화
 * - 캐시 친화적인 데이터 구조
 * 
 * 작성일: 2025-08-10
 * Phase 2 Week 5
 */

#ifndef OPTIMIZED_LED_CONTROLLER_H
#define OPTIMIZED_LED_CONTROLLER_H

#include <Arduino.h>
#include <driver/gpio.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <freertos/queue.h>

// ================================
// 하드웨어 설정
// ================================
#define MAX_LED_PAIRS 36
#define MAX_ULN2803A_ICS 5
#define CHANNELS_PER_IC 8
#define LEDS_PER_PAIR 2  // Red + Green

// GPIO 핀 설정 (ESP32 DevKit v1 기준)
#define GPIO_PIN_COUNT 40
#define USABLE_GPIO_COUNT 36  // 실제 사용 가능한 GPIO

// 시퀀스 설정
#define MAX_SEQUENCE_LENGTH 1000
#define MIN_INTERVAL_MS 10
#define MAX_INTERVAL_MS 5000
#define DEFAULT_INTERVAL_MS 800

// 메모리 최적화 설정
#define LED_STATE_BUFFER_SIZE ((MAX_LED_PAIRS * 2 + 7) / 8)  // 비트맵 크기
#define SEQUENCE_BUFFER_SIZE 512
#define COMMAND_QUEUE_SIZE 16

// ================================
// 데이터 구조
// ================================

// LED 색상 열거형
enum LEDColor : uint8_t {
    LED_RED = 0,
    LED_GREEN = 1
};

// LED 상태
enum LEDState : uint8_t {
    LED_OFF = 0,
    LED_ON = 1
};

// 시퀀스 타입
enum SequenceType : uint8_t {
    SEQ_RANDOM = 0,
    SEQ_SEQUENTIAL = 1,
    SEQ_PATTERN = 2,
    SEQ_CUSTOM = 3
};

// 시퀀스 상태
enum SequenceState : uint8_t {
    SEQ_IDLE = 0,
    SEQ_RUNNING = 1,
    SEQ_PAUSED = 2,
    SEQ_STOPPING = 3
};

// LED 위치 정보 (메모리 효율성을 위해 압축)
struct __attribute__((packed)) LEDMapping {
    uint8_t gpioPin;        // GPIO 핀 번호
    uint8_t icIndex : 3;    // ULN2803A IC 인덱스 (0-7)
    uint8_t channelIndex : 3; // IC 내 채널 인덱스 (0-7)
    uint8_t ledPair : 6;    // LED 쌍 번호 (0-35)
    uint8_t color : 1;      // 색상 (0=Red, 1=Green)
    uint8_t position : 1;   // 위치 구분 (내경/외경)
};

// 시퀀스 항목 (메모리 효율성)
struct __attribute__((packed)) SequenceItem {
    uint8_t ledPair : 6;    // LED 쌍 번호 (0-35)
    uint8_t color : 1;      // 색상
    uint8_t reserved : 1;   // 예약됨
    uint16_t duration;      // 지속 시간 (ms)
    uint16_t interval;      // 다음 항목까지 간격 (ms)
};

// 성능 통계
struct PerformanceStats {
    uint32_t totalCommands;
    uint32_t successfulCommands;
    uint32_t failedCommands;
    uint32_t sequenceExecutions;
    uint32_t ledSwitchCount;
    uint32_t averageResponseTime;  // μs
    uint32_t maxResponseTime;      // μs
    uint32_t minResponseTime;      // μs
    uint32_t memoryUsage;         // bytes
    uint32_t freeHeapMin;         // bytes
};

// 명령 큐 항목
struct Command {
    enum Type : uint8_t {
        CMD_SET_LED,
        CMD_SET_MULTIPLE,
        CMD_START_SEQUENCE,
        CMD_STOP_SEQUENCE,
        CMD_PAUSE_SEQUENCE,
        CMD_CALIBRATE
    } type;
    
    union {
        struct {
            uint8_t ledPair;
            LEDColor color;
            LEDState state;
        } setLed;
        
        struct {
            uint64_t ledMask;  // 64비트 마스크 (36쌍 × 2색 = 72비트 중 64비트 사용)
            LEDState state;
        } setMultiple;
        
        struct {
            SequenceType type;
            uint16_t interval;
            uint16_t count;
            uint32_t seed;     // 랜덤 시드
        } sequence;
    } params;
    
    uint32_t timestamp;
    uint8_t priority;
};

// ================================
// OptimizedLEDController 클래스
// ================================
class OptimizedLEDController {
private:
    // 하드웨어 매핑
    LEDMapping ledMappings[MAX_LED_PAIRS * 2];  // 36쌍 × 2색 = 72개
    gpio_num_t gpioPins[MAX_LED_PAIRS * 2];
    uint8_t activeLEDCount;
    
    // LED 상태 (비트맵)
    uint8_t ledStateBuffer[LED_STATE_BUFFER_SIZE];
    volatile bool stateChanged;
    
    // 시퀀스 관리
    SequenceItem* sequenceBuffer;
    uint16_t sequenceLength;
    uint16_t currentSequenceIndex;
    SequenceState sequenceState;
    SequenceType currentSequenceType;
    
    // 타이머 관리
    esp_timer_handle_t sequenceTimer;
    esp_timer_handle_t performanceTimer;
    uint64_t lastExecutionTime;
    
    // 동기화 및 큐
    SemaphoreHandle_t ledStateMutex;
    SemaphoreHandle_t sequenceMutex;
    QueueHandle_t commandQueue;
    
    // 성능 모니터링
    PerformanceStats stats;
    uint32_t commandStartTime;
    
    // 메모리 관리
    static void* alignedAlloc(size_t size, size_t alignment = 16);
    static void alignedFree(void* ptr);
    bool initializeMemoryPool();
    void cleanupMemoryPool();
    
    // 비트맵 조작
    inline void setBit(uint8_t* buffer, uint8_t index, bool value);
    inline bool getBit(const uint8_t* buffer, uint8_t index);
    void updateGPIOFromBitmap();
    
    // 하드웨어 제어
    void initializeGPIO();
    void setGPIOFast(gpio_num_t pin, bool state);
    void setMultipleGPIO(const gpio_num_t* pins, const bool* states, uint8_t count);
    
    // 시퀀스 관리
    static void sequenceTimerCallback(void* arg);
    void executeSequenceStep();
    bool generateRandomSequence(uint16_t count, uint16_t interval);
    bool generateSequentialSequence(uint16_t count, uint16_t interval);
    void processSequenceCommand(const Command& cmd);
    
    // 명령 처리
    static void commandTaskFunction(void* parameter);
    void processCommandQueue();
    bool validateCommand(const Command& cmd);
    void executeCommand(const Command& cmd);
    
    // 성능 모니터링
    static void performanceTimerCallback(void* arg);
    void updatePerformanceStats();
    void startCommandTiming();
    void endCommandTiming(bool success);
    
public:
    OptimizedLEDController();
    ~OptimizedLEDController();
    
    // 초기화 및 해제
    bool initialize(uint8_t ledPairCount = 8);  // 기본 8쌍, 최대 36쌍
    void cleanup();
    bool isInitialized() const;
    
    // LED 매핑 설정
    bool setLEDMapping(uint8_t ledPair, LEDColor color, gpio_num_t gpioPin);
    bool loadDefaultMapping(uint8_t ledPairCount);
    bool validateMapping();
    void printMapping();
    
    // LED 제어 (논블로킹)
    bool setLED(uint8_t ledPair, LEDColor color, LEDState state, uint8_t priority = 5);
    bool setLEDPair(uint8_t ledPair, LEDState redState, LEDState greenState, uint8_t priority = 5);
    bool setMultipleLEDs(const uint8_t* ledPairs, const LEDColor* colors, 
                        const LEDState* states, uint8_t count, uint8_t priority = 5);
    bool setAllLEDs(LEDState state, uint8_t priority = 5);
    
    // LED 상태 조회
    LEDState getLEDState(uint8_t ledPair, LEDColor color);
    void getAllLEDStates(uint8_t* stateBuffer);  // 호출자가 버퍼 제공
    uint8_t getActiveLEDCount();
    
    // 시퀀스 제어
    bool startSequence(SequenceType type, uint16_t interval = DEFAULT_INTERVAL_MS, 
                      uint16_t count = 0, uint32_t seed = 0);
    bool pauseSequence();
    bool resumeSequence();
    bool stopSequence();
    SequenceState getSequenceState();
    uint16_t getSequenceProgress();  // 0-100%
    
    // 사용자 정의 시퀀스
    bool loadCustomSequence(const SequenceItem* sequence, uint16_t length);
    bool saveSequenceToFile(const char* filename);
    bool loadSequenceFromFile(const char* filename);
    
    // 캘리브레이션
    bool startCalibration();
    bool isCalibrationRunning();
    float getCalibrationProgress();  // 0.0-1.0
    
    // 성능 모니터링
    PerformanceStats getPerformanceStats();
    void resetPerformanceStats();
    bool enablePerformanceMonitoring(bool enable);
    uint32_t getMemoryUsage();
    uint32_t getFreeHeapSize();
    
    // 디버깅 및 진단
    bool runSelfTest();
    bool testLEDConnections();
    void printDiagnostics();
    void dumpLEDStates();
    
    // 설정 관리
    struct Config {
        uint16_t defaultInterval;
        uint8_t maxConcurrentLEDs;
        bool enablePerformanceLogging;
        bool enableAutoCalibration;
        uint8_t gpioStrength;  // GPIO 구동 강도
    } config;
    
    bool loadConfig();
    bool saveConfig();
    void resetToDefaults();
    
    // 콜백 등록
    typedef void (*LEDStateCallback)(uint8_t ledPair, LEDColor color, LEDState state);
    typedef void (*SequenceCallback)(SequenceState state, uint16_t progress);
    typedef void (*ErrorCallback)(int errorCode, const char* message);
    
    void setLEDStateCallback(LEDStateCallback callback);
    void setSequenceCallback(SequenceCallback callback);
    void setErrorCallback(ErrorCallback callback);
    
private:
    LEDStateCallback onLEDStateChanged;
    SequenceCallback onSequenceStateChanged;
    ErrorCallback onError;
    
    // 초기화 상태
    bool initialized;
    TaskHandle_t commandTaskHandle;
    
    // 에러 코드 정의
    static const int ERROR_NOT_INITIALIZED = 1001;
    static const int ERROR_INVALID_LED_PAIR = 1002;
    static const int ERROR_INVALID_GPIO = 1003;
    static const int ERROR_SEQUENCE_RUNNING = 1004;
    static const int ERROR_MEMORY_ALLOCATION = 1005;
    static const int ERROR_HARDWARE_FAULT = 1006;
    
    void reportError(int errorCode, const char* message);
};

// ================================
// 유틸리티 함수
// ================================

// 비트 조작 인라인 함수
inline void OptimizedLEDController::setBit(uint8_t* buffer, uint8_t index, bool value) {
    uint8_t byteIndex = index / 8;
    uint8_t bitIndex = index % 8;
    
    if (value) {
        buffer[byteIndex] |= (1 << bitIndex);
    } else {
        buffer[byteIndex] &= ~(1 << bitIndex);
    }
}

inline bool OptimizedLEDController::getBit(const uint8_t* buffer, uint8_t index) {
    uint8_t byteIndex = index / 8;
    uint8_t bitIndex = index % 8;
    return (buffer[byteIndex] >> bitIndex) & 1;
}

// 성능 측정 매크로
#define MEASURE_PERFORMANCE(controller, code) do { \
    (controller)->startCommandTiming(); \
    bool success = (code); \
    (controller)->endCommandTiming(success); \
} while(0)

// 메모리 정렬 헬퍼
template<typename T>
T* aligned_new(size_t count = 1, size_t alignment = 16) {
    void* ptr = OptimizedLEDController::alignedAlloc(sizeof(T) * count, alignment);
    return static_cast<T*>(ptr);
}

template<typename T>
void aligned_delete(T* ptr) {
    if (ptr) {
        ptr->~T();
        OptimizedLEDController::alignedFree(ptr);
    }
}

// 디버깅 매크로
#ifdef DEBUG_LED_CONTROLLER
#define LED_DEBUG(fmt, ...) Serial.printf("[LED] " fmt "\n", ##__VA_ARGS__)
#define LED_ERROR(fmt, ...) Serial.printf("[LED ERROR] " fmt "\n", ##__VA_ARGS__)
#else
#define LED_DEBUG(fmt, ...)
#define LED_ERROR(fmt, ...)
#endif

#endif // OPTIMIZED_LED_CONTROLLER_H