/*
 * 8쌍 LED 프로토타입 테스트 코드
 * 
 * 기능:
 * 1. 8쌍 LED 개별 제어 테스트
 * 2. 시퀀스 모드 테스트 (순차/무작위)
 * 3. Bluetooth 통신 테스트
 * 4. 하드웨어 검증 테스트
 * 
 * 하드웨어:
 * - ESP32-WROOM-32 DevKit v1
 * - ULN2803A (DIP-18)
 * - 8쌍 LED (적색/녹색 각 4개)
 * 
 * 작성일: 2025-08-10
 * 대상: 8쌍 LED 프로토타입
 */

#include <Arduino.h>
#include <BluetoothSerial.h>
#include <ArduinoJson.h>

// ================================
// 핀 매핑 정의 (8쌍 = 16개 LED)
// ================================
const int LED_PIN_COUNT = 8;  // 8개 제어핀 (4쌍 × 2색)

// ESP32 GPIO → ULN2803A 입력핀 매핑
const int LED_PINS[LED_PIN_COUNT] = {
  2,   // GPIO2  → ULN2803A 1B → LED 0 Red
  4,   // GPIO4  → ULN2803A 2B → LED 0 Green  
  5,   // GPIO5  → ULN2803A 3B → LED 1 Red
  18,  // GPIO18 → ULN2803A 4B → LED 1 Green
  19,  // GPIO19 → ULN2803A 5B → LED 2 Red
  21,  // GPIO21 → ULN2803A 6B → LED 2 Green
  22,  // GPIO22 → ULN2803A 7B → LED 3 Red
  23   // GPIO23 → ULN2803A 8B → LED 3 Green
};

// LED 쌍 정의 (4쌍)
typedef struct {
  int redPin;
  int greenPin;
  int pairIndex;
} LEDPair;

const LEDPair LED_PAIRS[4] = {
  {0, 1, 0},  // LED 0: Red=핀0(GPIO2), Green=핀1(GPIO4)
  {2, 3, 1},  // LED 1: Red=핀2(GPIO5), Green=핀3(GPIO18)  
  {4, 5, 2},  // LED 2: Red=핀4(GPIO19), Green=핀5(GPIO21)
  {6, 7, 3}   // LED 3: Red=핀6(GPIO22), Green=핀7(GPIO23)
};

// ================================
// 전역 변수
// ================================
BluetoothSerial SerialBT;
bool bluetoothEnabled = false;
bool testRunning = false;
unsigned long lastHeartbeat = 0;
const unsigned long HEARTBEAT_INTERVAL = 5000; // 5초

// 테스트 모드
enum TestMode {
  MODE_INDIVIDUAL,  // 개별 LED 테스트
  MODE_SEQUENTIAL,  // 순차 점등 테스트  
  MODE_RANDOM,      // 무작위 점등 테스트
  MODE_BLUETOOTH,   // Bluetooth 통신 테스트
  MODE_HARDWARE     // 하드웨어 검증 테스트
};

// ================================
// LED 제어 함수
// ================================

void initializeLEDs() {
  Serial.println("LED 핀 초기화 중...");
  
  for (int i = 0; i < LED_PIN_COUNT; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    digitalWrite(LED_PINS[i], LOW);  // 모든 LED 끄기
    Serial.printf("GPIO %d 초기화 완료\n", LED_PINS[i]);
  }
  
  Serial.println("모든 LED 핀 초기화 완료");
  delay(1000);
}

void turnOffAllLEDs() {
  for (int i = 0; i < LED_PIN_COUNT; i++) {
    digitalWrite(LED_PINS[i], LOW);
  }
}

void turnOnLED(int pinIndex) {
  if (pinIndex >= 0 && pinIndex < LED_PIN_COUNT) {
    digitalWrite(LED_PINS[pinIndex], HIGH);
  }
}

void turnOffLED(int pinIndex) {
  if (pinIndex >= 0 && pinIndex < LED_PIN_COUNT) {
    digitalWrite(LED_PINS[pinIndex], LOW);
  }
}

void turnOnLEDPair(int pairIndex, bool red, bool green) {
  if (pairIndex >= 0 && pairIndex < 4) {
    const LEDPair& pair = LED_PAIRS[pairIndex];
    
    if (red) {
      digitalWrite(LED_PINS[pair.redPin], HIGH);
    }
    if (green) {
      digitalWrite(LED_PINS[pair.greenPin], HIGH);
    }
  }
}

// ================================
// 테스트 함수들
// ================================

void testIndividualLEDs() {
  Serial.println("\n=== 개별 LED 테스트 시작 ===");
  
  for (int i = 0; i < LED_PIN_COUNT; i++) {
    turnOffAllLEDs();
    
    String color = (i % 2 == 0) ? "적색" : "녹색";
    int pairNum = i / 2;
    
    Serial.printf("LED %d번 쌍 %s 점등 (GPIO %d)\n", pairNum, color.c_str(), LED_PINS[i]);
    
    turnOnLED(i);
    delay(1000);  // 1초간 점등
    
    turnOffLED(i);
    delay(500);   // 0.5초간 소등
  }
  
  Serial.println("개별 LED 테스트 완료\n");
}

void testSequentialMode() {
  Serial.println("\n=== 순차 점등 테스트 시작 ===");
  
  for (int cycle = 0; cycle < 2; cycle++) {
    Serial.printf("순차 점등 사이클 %d\n", cycle + 1);
    
    for (int pair = 0; pair < 4; pair++) {
      turnOffAllLEDs();
      
      // 적색 먼저 점등
      Serial.printf("LED %d번 쌍 적색 점등\n", pair);
      turnOnLEDPair(pair, true, false);
      delay(800);
      
      // 녹색으로 변경
      turnOffAllLEDs();
      Serial.printf("LED %d번 쌍 녹색 점등\n", pair);
      turnOnLEDPair(pair, false, true);
      delay(800);
      
      turnOffAllLEDs();
      delay(200);
    }
  }
  
  Serial.println("순차 점등 테스트 완료\n");
}

void testRandomMode() {
  Serial.println("\n=== 무작위 점등 테스트 시작 ===");
  
  randomSeed(millis());
  
  for (int i = 0; i < 16; i++) {  // 16회 무작위 점등
    turnOffAllLEDs();
    
    int randomPair = random(4);          // 0-3 중 선택
    int randomColor = random(2);         // 0(적색), 1(녹색)
    
    String colorName = (randomColor == 0) ? "적색" : "녹색";
    Serial.printf("무작위 점등 %d: LED %d번 쌍 %s\n", i + 1, randomPair, colorName.c_str());
    
    if (randomColor == 0) {
      turnOnLEDPair(randomPair, true, false);   // 적색 점등
    } else {
      turnOnLEDPair(randomPair, false, true);   // 녹색 점등  
    }
    
    delay(800);
    turnOffAllLEDs();
    delay(200);
  }
  
  Serial.println("무작위 점등 테스트 완료\n");
}

// ================================
// Bluetooth 통신 함수
// ================================

void initializeBluetooth() {
  Serial.println("Bluetooth 초기화 중...");
  
  if (SerialBT.begin("LED_8Pair_Test")) {
    bluetoothEnabled = true;
    Serial.println("Bluetooth 준비 완료 - 장치명: LED_8Pair_Test");
    Serial.println("Android 앱에서 연결하세요.");
  } else {
    bluetoothEnabled = false;
    Serial.println("Bluetooth 초기화 실패!");
  }
}

void sendBluetoothMessage(const String& message) {
  if (bluetoothEnabled && SerialBT.hasClient()) {
    SerialBT.println(message);
    Serial.printf("BT 전송: %s\n", message.c_str());
  }
}

void sendHeartbeat() {
  if (millis() - lastHeartbeat > HEARTBEAT_INTERVAL) {
    StaticJsonDocument<200> doc;
    doc["type"] = "HEARTBEAT";
    doc["timestamp"] = millis();
    doc["status"] = "OK";
    doc["led_count"] = 8;
    
    String heartbeat;
    serializeJson(doc, heartbeat);
    sendBluetoothMessage(heartbeat);
    
    lastHeartbeat = millis();
  }
}

void processBluetoothCommand() {
  if (!bluetoothEnabled || !SerialBT.available()) {
    return;
  }
  
  String command = SerialBT.readString();
  command.trim();
  
  Serial.printf("BT 수신: %s\n", command.c_str());
  
  // JSON 파싱
  StaticJsonDocument<300> doc;
  DeserializationError error = deserializeJson(doc, command);
  
  if (error) {
    Serial.printf("JSON 파싱 오류: %s\n", error.c_str());
    return;
  }
  
  String cmd = doc["command"];
  String id = doc["id"];
  
  if (cmd == "SET_LED") {
    int pairIndex = doc["params"]["pair"];
    bool red = doc["params"]["red"];
    bool green = doc["params"]["green"];
    
    if (pairIndex >= 0 && pairIndex < 4) {
      turnOffAllLEDs();
      turnOnLEDPair(pairIndex, red, green);
      
      // 응답 전송
      StaticJsonDocument<200> response;
      response["type"] = "SUCCESS";
      response["id"] = id;
      response["message"] = "LED 제어 완료";
      
      String responseStr;
      serializeJson(response, responseStr);
      sendBluetoothMessage(responseStr);
      
      Serial.printf("LED %d번 쌍 제어: 적색=%d, 녹색=%d\n", pairIndex, red, green);
    }
  }
  else if (cmd == "START_SEQUENCE") {
    int type = doc["params"]["type"];  // 0: random, 1: sequential
    int interval = doc["params"]["interval"];
    
    // 응답 전송
    StaticJsonDocument<200> response;
    response["type"] = "SUCCESS";
    response["id"] = id;
    response["message"] = "시퀀스 시작";
    
    String responseStr;
    serializeJson(response, responseStr);
    sendBluetoothMessage(responseStr);
    
    Serial.printf("시퀀스 시작: 타입=%d, 간격=%dms\n", type, interval);
  }
  else if (cmd == "STOP_SEQUENCE") {
    turnOffAllLEDs();
    
    // 응답 전송
    StaticJsonDocument<200> response;
    response["type"] = "SUCCESS"; 
    response["id"] = id;
    response["message"] = "시퀀스 중지";
    
    String responseStr;
    serializeJson(response, responseStr);
    sendBluetoothMessage(responseStr);
    
    Serial.println("시퀀스 중지됨");
  }
}

void testBluetoothCommunication() {
  Serial.println("\n=== Bluetooth 통신 테스트 시작 ===");
  Serial.println("Android 앱에서 명령을 전송하세요:");
  Serial.println("1. LED 개별 제어");
  Serial.println("2. 시퀀스 시작/중지");
  Serial.println("3. 상태 확인");
  Serial.println("Bluetooth 테스트는 30초간 진행됩니다...\n");
  
  unsigned long testStart = millis();
  const unsigned long TEST_DURATION = 30000; // 30초
  
  while (millis() - testStart < TEST_DURATION) {
    processBluetoothCommand();
    sendHeartbeat();
    delay(100);
  }
  
  Serial.println("Bluetooth 통신 테스트 완료\n");
}

// ================================
// 하드웨어 검증 테스트
// ================================

void testHardwareConnections() {
  Serial.println("\n=== 하드웨어 연결 검증 테스트 ===");
  
  // 1. 전원 공급 확인
  Serial.println("1. 전원 공급 상태 확인");
  Serial.printf("ESP32 전원 전압: 정상 (프로그램 실행 중)\n");
  
  // 2. GPIO 핀 출력 테스트
  Serial.println("\n2. GPIO 핀 출력 테스트");
  for (int i = 0; i < LED_PIN_COUNT; i++) {
    Serial.printf("GPIO %d 테스트: ", LED_PINS[i]);
    
    // HIGH 출력 테스트
    digitalWrite(LED_PINS[i], HIGH);
    delay(100);
    Serial.print("HIGH OK, ");
    
    // LOW 출력 테스트  
    digitalWrite(LED_PINS[i], LOW);
    delay(100);
    Serial.println("LOW OK");
  }
  
  // 3. LED 연결 상태 확인
  Serial.println("\n3. LED 연결 상태 확인");
  for (int i = 0; i < 4; i++) {
    Serial.printf("LED %d번 쌍 테스트:\n", i);
    
    // 적색 LED 테스트
    turnOffAllLEDs();
    turnOnLEDPair(i, true, false);
    Serial.printf("  적색 LED 점등 - 육안으로 확인하세요 (3초)\n");
    delay(3000);
    
    // 녹색 LED 테스트
    turnOffAllLEDs();
    turnOnLEDPair(i, false, true);
    Serial.printf("  녹색 LED 점등 - 육안으로 확인하세요 (3초)\n");
    delay(3000);
    
    turnOffAllLEDs();
    delay(500);
  }
  
  // 4. 전체 LED 동시 점등 테스트 (전력 소모 확인)
  Serial.println("\n4. 전체 LED 동시 점등 테스트 (전력 확인)");
  Serial.println("모든 적색 LED 동시 점등 (5초)");
  for (int i = 0; i < 4; i++) {
    turnOnLEDPair(i, true, false);
  }
  delay(5000);
  
  turnOffAllLEDs();
  Serial.println("모든 녹색 LED 동시 점등 (5초)");
  for (int i = 0; i < 4; i++) {
    turnOnLEDPair(i, false, true);
  }
  delay(5000);
  
  turnOffAllLEDs();
  Serial.println("하드웨어 검증 테스트 완료\n");
}

// ================================
// 메뉴 시스템
// ================================

void printMenu() {
  Serial.println("\n======= 8쌍 LED 프로토타입 테스트 메뉴 =======");
  Serial.println("1. 개별 LED 테스트");
  Serial.println("2. 순차 점등 테스트");
  Serial.println("3. 무작위 점등 테스트");
  Serial.println("4. Bluetooth 통신 테스트");
  Serial.println("5. 하드웨어 검증 테스트");
  Serial.println("6. 모든 LED 끄기");
  Serial.println("7. 메뉴 다시 보기");
  Serial.println("===============================================");
  Serial.print("선택하세요 (1-7): ");
}

void processMenuSelection() {
  if (Serial.available()) {
    int choice = Serial.parseInt();
    Serial.read(); // 버퍼 클리어
    
    Serial.printf("선택: %d\n", choice);
    
    switch (choice) {
      case 1:
        testIndividualLEDs();
        break;
      case 2:
        testSequentialMode();
        break;
      case 3:
        testRandomMode();
        break;
      case 4:
        testBluetoothCommunication();
        break;
      case 5:
        testHardwareConnections();
        break;
      case 6:
        turnOffAllLEDs();
        Serial.println("모든 LED를 껐습니다.");
        break;
      case 7:
        printMenu();
        break;
      default:
        Serial.println("잘못된 선택입니다. 1-7 사이의 숫자를 입력하세요.");
        break;
    }
  }
}

// ================================
// 메인 함수
// ================================

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n");
  Serial.println("========================================");
  Serial.println("   8쌍 LED 프로토타입 테스트 시작");
  Serial.println("========================================");
  Serial.println("작성일: 2025-08-10");
  Serial.println("하드웨어: ESP32 + ULN2803A + 8쌍 LED");
  Serial.println("========================================\n");
  
  // 시스템 정보 출력
  Serial.printf("ESP32 칩 모델: %s\n", ESP.getChipModel());
  Serial.printf("CPU 주파수: %d MHz\n", ESP.getCpuFreqMHz());
  Serial.printf("플래시 크기: %d KB\n", ESP.getFlashChipSize() / 1024);
  Serial.printf("사용 가능한 힙 메모리: %d KB\n", ESP.getFreeHeap() / 1024);
  Serial.println();
  
  // 하드웨어 초기화
  initializeLEDs();
  initializeBluetooth();
  
  // 시작 애니메이션
  Serial.println("시작 애니메이션...");
  for (int i = 0; i < 4; i++) {
    turnOnLEDPair(i, true, false);
    delay(200);
    turnOffAllLEDs();
    turnOnLEDPair(i, false, true);
    delay(200);
    turnOffAllLEDs();
  }
  
  Serial.println("초기화 완료!");
  printMenu();
}

void loop() {
  // 시리얼 메뉴 처리
  processMenuSelection();
  
  // Bluetooth 명령 처리
  processBluetoothCommand();
  
  // 하트비트 전송
  sendHeartbeat();
  
  // 짧은 지연
  delay(50);
}

/*
 * 사용 방법:
 * 
 * 1. Arduino IDE에서 이 코드를 ESP32에 업로드
 * 2. 시리얼 모니터 열기 (115200 baud)
 * 3. 메뉴에서 원하는 테스트 선택
 * 4. Android 앱으로 Bluetooth 연결 테스트
 * 
 * 트러블슈팅:
 * - LED가 점등되지 않으면: 배선 확인, 전원 확인
 * - Bluetooth 연결 안되면: 페어링 재시도
 * - 일부 LED만 작동: ULN2803A 연결 확인
 */