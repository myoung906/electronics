# Android 앱 MVP

## 📋 개요

36쌍 LED 시각검사 시제품용 Android 애플리케이션 MVP입니다. ESP32와 Bluetooth SPP 통신으로 연결하여 LED 제어, 검사 데이터 수집, 결과 분석 기능을 제공합니다.

## 🏗️ 아키텍처

### MVVM 패턴
- **Model**: PatientInfo, TestResult 데이터 클래스
- **View**: Activity와 XML 레이아웃
- **ViewModel**: LiveData 기반 상태 관리
- **Repository**: SQLite 데이터베이스 접근

### 핵심 컴포넌트
- **BluetoothManager**: ESP32와의 SPP 통신 관리
- **MessageProtocol**: JSON 기반 명령/응답 처리
- **PatientRepository**: 환자 정보 및 검사 결과 저장
- **ExcelExporter**: 검사 결과 Excel 내보내기

## 🔧 주요 기능

### 1. Bluetooth 통신
- ESP32 자동 검색 및 연결
- JSON 프로토콜 기반 명령 전송
- 실시간 상태 모니터링
- 자동 재연결 지원

### 2. LED 제어
- 36쌍 LED 개별 제어
- 무작위/순차 시퀀스 지원
- 실시간 진행률 표시
- 수동 LED 테스트

### 3. 환자 정보 관리
- 환자 개인정보 입력/수정
- 검사 설정 관리
- 데이터 유효성 검증
- SQLite 기반 로컬 저장

### 4. 검사 데이터 수집
- 실시간 응답 데이터 기록
- LED 위치별 성능 분석
- 색상별 정확도 계산
- 시간 기반 통계 생성

### 5. 결과 분석 및 내보내기
- 다양한 형태의 보고서 생성
- Excel 파일 내보내기
- 파일 공유 기능
- 통계 분석 및 시각화

## 🛠️ 기술 스택

### 개발 환경
- **언어**: Kotlin
- **최소 SDK**: API 21 (Android 5.0)
- **타겟 SDK**: API 34 (Android 14)
- **빌드 시스템**: Gradle

### 주요 라이브러리
```kotlin
// 핵심 Android
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'androidx.activity:activity-ktx:1.8.2'

// UI 컴포넌트
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

// 데이터 처리
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'org.apache.poi:poi-ooxml:5.2.4'

// 로깅 및 권한
implementation 'com.jakewharton.timber:timber:5.0.1'
implementation 'pub.devrel:easypermissions:3.0.0'
```

## 📱 화면 구성

### 1. 메인 화면 (MainActivity)
- ESP32 연결 상태 표시
- LED 시퀀스 제어 패널
- 실시간 LED 상태 모니터
- 검사 진행률 표시

### 2. 환자 정보 (PatientInfoActivity)
- 환자 개인정보 입력
- 검사 설정 관리
- 데이터 검증 및 저장

### 3. 검사 결과 (TestResultsActivity)
- 검사 결과 목록 표시
- 상세 분석 보고서
- Excel 내보내기 기능

### 4. 설정 (SettingsActivity)
- 앱 환경 설정
- Bluetooth 연결 관리
- 데이터 백업/복원

## 🔄 통신 프로토콜

### JSON 명령 형식
```json
{
  "command": "START_SEQUENCE",
  "id": "req_12345",
  "params": {
    "type": 0,        // 0: random, 1: sequential
    "interval": 800   // ms
  },
  "timestamp": 1634567890123
}
```

### 응답 처리
- **SUCCESS**: 명령 성공 응답
- **ERROR**: 오류 메시지 및 코드
- **STATUS**: 실시간 상태 알림
- **HEARTBEAT**: 연결 유지 확인

## 📊 데이터베이스 구조

### 환자 테이블 (patients)
- 기본 정보: 이름, ID, 나이, 성별
- 의료 정보: 시력, 담당의, 임상소견
- 검사 설정: 타입, 기간, 옵션

### 검사 결과 테이블 (test_results)
- 검사 메타데이터: 시작/종료 시간, 타입
- 성과 지표: 완료율, 정확도, 응답시간
- 상태 관리: 진행 중, 완료, 취소

### 데이터 포인트 테이블 (test_data_points)
- LED 정보: 쌍 번호, 색상, 위치
- 응답 데이터: 자극/응답 시간, 정답 여부

## 🚀 설치 및 실행

### 1. 개발 환경 설정
```bash
# Android Studio 설치 및 SDK 설정
# Kotlin 플러그인 활성화
# Gradle 동기화
```

### 2. 프로젝트 빌드
```bash
cd android_app
./gradlew assembleDebug
```

### 3. 디바이스 설치
```bash
./gradlew installDebug
```

### 4. ESP32 페어링
1. ESP32 전원 켜기
2. Android 설정에서 Bluetooth 검색
3. "LED_Vision_Tester" 페어링
4. 앱에서 연결 버튼 클릭

## 🧪 테스트

### 단위 테스트
```bash
./gradlew test
```

### UI 테스트
```bash
./gradlew connectedAndroidTest
```

### Bluetooth 통신 테스트
1. ESP32 시뮬레이터 모드 실행
2. JSON 명령 전송 확인
3. 응답 파싱 검증

## 📋 주요 클래스

### BluetoothManager
```kotlin
class BluetoothManager(context: Context) {
    fun initialize(): Boolean
    fun connect()
    fun disconnect()
    fun sendMessage(message: String): Boolean
    fun isConnected(): Boolean
}
```

### MessageProtocol
```kotlin
class MessageProtocol {
    fun createStartSequenceCommand(): String
    fun parseMessage(json: String): ParsedMessage?
    fun cleanupExpiredRequests()
}
```

### PatientRepository
```kotlin
class PatientRepository(context: Context) {
    suspend fun insertPatient(patient: PatientInfo): Boolean
    suspend fun getPatientById(id: String): PatientInfo?
    suspend fun getAllPatients(): List<PatientInfo>
}
```

### ExcelExporter
```kotlin
class ExcelExporter(context: Context) {
    fun exportPatientTestResult(): ExportResult
    fun shareExcelFile(file: File): Intent
}
```

## 🔧 설정

### Bluetooth 권한 (API 31+)
```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

### 파일 저장 권한
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<provider android:name="androidx.core.content.FileProvider" />
```

### ProGuard 설정
```proguard
-keep class com.ledvision.tester.model.** { *; }
-dontwarn org.apache.poi.**
```

## 🚨 문제 해결

### 일반적인 문제
1. **Bluetooth 연결 실패**: 권한 및 페어링 상태 확인
2. **JSON 파싱 오류**: 명령 형식 및 인코딩 확인
3. **Excel 내보내기 실패**: 저장소 권한 및 용량 확인

### 디버그 로그
```kotlin
// Timber 로그 확인
adb logcat -s LEDVisionTester
```

## 📈 성능 지표

### 메모리 사용량
- **앱 크기**: ~15MB
- **런타임 메모리**: ~50MB
- **데이터베이스**: ~1MB (환자 100명 기준)

### 통신 성능
- **연결 시간**: ~3초
- **명령 응답**: <100ms
- **데이터 전송**: ~1KB/s

## 🔄 업데이트 계획

### v0.2.0 (계획)
- 실시간 그래프 시각화
- 클라우드 동기화
- 다국어 지원

### v0.3.0 (계획)
- AI 기반 결과 분석
- 웨어러블 디바이스 연동
- 원격 모니터링

## 📄 라이선스

MIT License - 교육 및 연구 목적 자유 사용

---

**현재 버전**: v0.1.0  
**업데이트**: 2025-08-10  
**상태**: ✅ MVP 완료