# ESP32 펌웨어

## 📋 개요

36쌍 LED 시각검사 시제품용 ESP32 펌웨어입니다. ULN2803A 드라이버 IC를 사용하여 72개 LED(적색/녹색 각 36개)를 제어하고, Bluetooth SPP 통신으로 Android 앱과 연동합니다.

## 🏗️ 아키텍처

### 핵심 컴포넌트

- **LEDController**: ULN2803A 기반 LED 제어 클래스
- **BluetoothManager**: Bluetooth SPP 통신 관리 클래스  
- **JSON Protocol**: 신뢰성 있는 명령/응답 시스템

### 하드웨어 매핑

```
ULN2803A IC 배치:
├── IC1 (GPIO 2,4,5,12,13,14,15,16): 내경 8쌍
├── IC2 (GPIO 17,18,19,21,22,23,25,26): 내경 4쌍 + 중간 4쌍
├── IC3 (GPIO 27,32,33,34,35,36,39,0): 중간 8쌍
├── IC4 (GPIO 1,3,6,7,8,9,10,11): 외곽 8쌍
└── IC5 (GPIO 20,24,28,29,30,31,37,38): 외곽 4쌍 + 예비
```

## 🔧 기능

### LED 제어
- 36쌍 개별 LED 제어 (적색/녹색)
- 동시 점등 방지 (한 쌍당 하나의 색상만)
- 랜덤/순차 시퀀스 지원
- 실시간 상태 모니터링

### 통신 프로토콜
- JSON 기반 명령/응답 시스템
- ACK/NACK 응답 지원
- 하트비트 및 상태 보고
- 오류 처리 및 복구

### 지원 명령어

```json
{
  "command": "START_SEQUENCE",
  "id": "req_001",
  "params": {
    "type": 0,        // 0: random, 1: sequential
    "interval": 800   // ms
  }
}
```

```json
{
  "command": "SET_LED",
  "id": "req_002", 
  "params": {
    "pair": 5,        // LED 쌍 번호 (0-35)
    "color": "red",   // "red" or "green"
    "state": true     // true=ON, false=OFF
  }
}
```

## 🛠️ 개발 환경

### 필수 도구
- **PlatformIO**: 개발 환경
- **ESP32 Arduino Core**: 펌웨어 프레임워크
- **ArduinoJson**: JSON 파싱 라이브러리

### 빌드 설정
```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200

lib_deps = 
    BluetoothSerial
    ArduinoJson
```

## 🚀 설치 및 실행

### 1. 개발 환경 설정
```bash
# PlatformIO 설치
pip install platformio

# 프로젝트 빌드
cd firmware
pio run
```

### 2. 펌웨어 업로드
```bash
# ESP32에 펌웨어 업로드
pio run --target upload

# 시리얼 모니터 시작
pio device monitor
```

### 3. Bluetooth 페어링
1. ESP32 전원 켜기
2. Android 기기에서 "LED_Vision_Tester" 검색
3. 페어링 완료 후 앱 연결

## 📊 성능 특성

### 하드웨어 최적화
- **부품 수**: 144개 → 81개 (44% 감소)
- **GPIO 사용**: 40핀 중 36핀 활용
- **전력 소비**: 각 LED 20mA, 최대 1.44A

### 통신 성능
- **Bluetooth 범위**: 최대 10m (Class 2)
- **데이터 전송률**: 최대 3Mbps (SPP)
- **응답 지연**: < 100ms

### 메모리 사용량
- **플래시**: ~200KB (1MB 중)
- **SRAM**: ~50KB (320KB 중)
- **JSON 버퍼**: 1KB (동적 할당)

## 🧪 테스트

### 하드웨어 테스트
```cpp
// 개별 LED 쌍 테스트
ledController.testPair(5);

// 전체 LED 테스트  
ledController.testAllLEDs();

// 시퀀스 테스트
ledController.testSequence();
```

### 통신 테스트
```cpp
// Bluetooth 연결 테스트
btManager.sendHeartbeat();

// JSON 명령 테스트
btManager.sendJson("{\"test\": \"message\"}");
```

## 🐛 디버깅

### 시리얼 모니터 출력
```
=== LED Vision Tester v0.1.0 ===
ESP32 펌웨어 시작
LED Controller 초기화 완료
Bluetooth 초기화 완료
Android 앱 연결 대기 중...
```

### 디버그 플래그
```cpp
#define DEBUG_ENABLED 1
#define DEBUG_LED_CONTROL 1
#define DEBUG_BLUETOOTH 1  
#define DEBUG_JSON 1
```

## 📝 API 참조

### LEDController 클래스
- `bool setLED(int pairId, int color, bool state)`: 개별 LED 제어
- `bool startSequence(int type, int interval)`: 시퀀스 시작
- `void stopSequence()`: 시퀀스 정지
- `bool isSequenceRunning()`: 시퀀스 실행 상태

### BluetoothManager 클래스  
- `bool sendMessage(const String& message)`: 메시지 송신
- `String receiveMessage()`: 메시지 수신
- `bool isConnected()`: 연결 상태 확인
- `bool sendHeartbeat()`: 하트비트 송신

## 🔍 문제 해결

### 일반적인 문제
1. **Bluetooth 연결 실패**: ESP32 재시작 또는 페어링 재설정
2. **LED 점등 안됨**: 전원 공급 및 GPIO 연결 확인
3. **JSON 파싱 오류**: 명령 형식 및 문법 확인

### 로그 분석
```
ERROR: LED 컨트롤러 초기화 실패
-> GPIO 핀 연결 상태 확인

WARNING: Bluetooth 연결되지 않음
-> 안드로이드 앱 연결 상태 확인

JSON 파싱 오류: MissingProperty
-> 필수 필드 누락 확인
```

## 📄 라이선스

MIT License - 교육 및 연구 목적 자유 사용

---

**현재 버전**: v0.1.0  
**업데이트**: 2025-08-10  
**상태**: ✅ Phase 1 완료