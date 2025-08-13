package com.ledvision.tester.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ledvision.tester.bluetooth.BluetoothManager
import com.ledvision.tester.protocol.MessageProtocol
import com.ledvision.tester.protocol.ParsedMessage
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 메인 액티비티의 ViewModel
 * 
 * 주요 역할:
 * - Bluetooth 연결 관리
 * - ESP32와의 통신 제어
 * - LED 시퀀스 제어
 * - UI 상태 관리
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bluetoothManager = BluetoothManager(application)
    private val messageProtocol = MessageProtocol()
    
    // === LiveData 정의 ===
    
    // 연결 상태
    private val _connectionState = MutableLiveData<BluetoothManager.ConnectionState>()
    val connectionState: LiveData<BluetoothManager.ConnectionState> = _connectionState
    
    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected
    
    // 장치 정보
    private val _deviceInfo = MutableLiveData<BluetoothManager.DeviceInfo?>()
    val deviceInfo: LiveData<BluetoothManager.DeviceInfo?> = _deviceInfo
    
    // 시퀀스 상태
    private val _isSequenceRunning = MutableLiveData<Boolean>()
    val isSequenceRunning: LiveData<Boolean> = _isSequenceRunning
    
    private val _sequenceProgress = MutableLiveData<Int>()
    val sequenceProgress: LiveData<Int> = _sequenceProgress
    
    private val _currentLedPair = MutableLiveData<Int?>()
    val currentLedPair: LiveData<Int?> = _currentLedPair
    
    // 메시지 및 상태
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // 검사 데이터
    private val _testData = MutableLiveData<List<TestDataPoint>>()
    val testData: LiveData<List<TestDataPoint>> = _testData
    
    // === 내부 상태 변수 ===
    private val currentTestData = mutableListOf<TestDataPoint>()
    private var currentSequenceType = 0
    private var currentSequenceInterval = 800
    
    init {
        setupObservers()
        _connectionState.value = BluetoothManager.ConnectionState.DISCONNECTED
        _isConnected.value = false
        _isSequenceRunning.value = false
        _sequenceProgress.value = 0
        _statusMessage.value = "초기화 완료"
    }
    
    /**
     * 옵저버 설정
     */
    private fun setupObservers() {
        // Bluetooth 연결 상태 관찰
        bluetoothManager.connectionState.observeForever { state ->
            _connectionState.value = state
            _isConnected.value = (state == BluetoothManager.ConnectionState.CONNECTED)
            
            // 연결 해제 시 시퀀스 정지
            if (state != BluetoothManager.ConnectionState.CONNECTED) {
                _isSequenceRunning.value = false
                _sequenceProgress.value = 0
                _currentLedPair.value = null
            }
        }
        
        // 장치 정보 관찰
        bluetoothManager.deviceInfo.observeForever { deviceInfo ->
            _deviceInfo.value = deviceInfo
        }
        
        // 메시지 수신 관찰
        bluetoothManager.receivedMessage.observeForever { message ->
            processReceivedMessage(message)
        }
    }
    
    /**
     * Bluetooth 초기화
     */
    fun initializeBluetooth() {
        viewModelScope.launch {
            try {
                val success = bluetoothManager.initialize()
                if (success) {
                    _statusMessage.value = "Bluetooth 초기화 완료"
                    Timber.i("Bluetooth 초기화 성공")
                } else {
                    _errorMessage.value = "Bluetooth 초기화 실패"
                    Timber.e("Bluetooth 초기화 실패")
                }
            } catch (e: Exception) {
                Timber.e(e, "Bluetooth 초기화 중 예외")
                _errorMessage.value = "Bluetooth 초기화 오류: ${e.message}"
            }
        }
    }
    
    /**
     * ESP32 연결
     */
    fun connect() {
        viewModelScope.launch {
            try {
                bluetoothManager.connect()
                _statusMessage.value = "ESP32 연결 시도 중..."
                Timber.i("ESP32 연결 시도")
            } catch (e: Exception) {
                Timber.e(e, "연결 시도 중 예외")
                _errorMessage.value = "연결 오류: ${e.message}"
            }
        }
    }
    
    /**
     * ESP32 연결 해제
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                bluetoothManager.disconnect()
                _statusMessage.value = "ESP32 연결 해제"
                Timber.i("ESP32 연결 해제")
            } catch (e: Exception) {
                Timber.e(e, "연결 해제 중 예외")
                _errorMessage.value = "연결 해제 오류: ${e.message}"
            }
        }
    }
    
    /**
     * LED 시퀀스 시작
     */
    fun startSequence(type: Int = 0, interval: Int = 800) {
        if (_isConnected.value != true) {
            _errorMessage.value = "ESP32가 연결되지 않았습니다"
            return
        }
        
        viewModelScope.launch {
            try {
                currentSequenceType = type
                currentSequenceInterval = interval
                currentTestData.clear()
                
                val command = messageProtocol.createStartSequenceCommand(type, interval)
                val success = bluetoothManager.sendMessage(command)
                
                if (success) {
                    _statusMessage.value = "시퀀스 시작 명령 전송"
                    Timber.i("시퀀스 시작: type=$type, interval=${interval}ms")
                } else {
                    _errorMessage.value = "시퀀스 시작 명령 전송 실패"
                }
            } catch (e: Exception) {
                Timber.e(e, "시퀀스 시작 중 예외")
                _errorMessage.value = "시퀀스 시작 오류: ${e.message}"
            }
        }
    }
    
    /**
     * LED 시퀀스 정지
     */
    fun stopSequence() {
        if (_isConnected.value != true) {
            _errorMessage.value = "ESP32가 연결되지 않았습니다"
            return
        }
        
        viewModelScope.launch {
            try {
                val command = messageProtocol.createStopSequenceCommand()
                val success = bluetoothManager.sendMessage(command)
                
                if (success) {
                    _statusMessage.value = "시퀀스 정지 명령 전송"
                    Timber.i("시퀀스 정지")
                } else {
                    _errorMessage.value = "시퀀스 정지 명령 전송 실패"
                }
            } catch (e: Exception) {
                Timber.e(e, "시퀀스 정지 중 예외")
                _errorMessage.value = "시퀀스 정지 오류: ${e.message}"
            }
        }
    }
    
    /**
     * 개별 LED 제어
     */
    fun setLed(pairId: Int, color: String, state: Boolean) {
        if (_isConnected.value != true) {
            _errorMessage.value = "ESP32가 연결되지 않았습니다"
            return
        }
        
        viewModelScope.launch {
            try {
                val command = messageProtocol.createSetLedCommand(pairId, color, state)
                val success = bluetoothManager.sendMessage(command)
                
                if (success) {
                    _statusMessage.value = "LED ${pairId + 1} $color ${if (state) "켜기" else "끄기"}"
                    Timber.i("LED 제어: pair=$pairId, color=$color, state=$state")
                } else {
                    _errorMessage.value = "LED 제어 명령 전송 실패"
                }
            } catch (e: Exception) {
                Timber.e(e, "LED 제어 중 예외")
                _errorMessage.value = "LED 제어 오류: ${e.message}"
            }
        }
    }
    
    /**
     * ESP32 상태 조회
     */
    fun getStatus() {
        if (_isConnected.value != true) {
            _errorMessage.value = "ESP32가 연결되지 않았습니다"
            return
        }
        
        viewModelScope.launch {
            try {
                val command = messageProtocol.createGetStatusCommand()
                val success = bluetoothManager.sendMessage(command)
                
                if (success) {
                    Timber.i("상태 조회 요청 전송")
                } else {
                    _errorMessage.value = "상태 조회 명령 전송 실패"
                }
            } catch (e: Exception) {
                Timber.e(e, "상태 조회 중 예외")
                _errorMessage.value = "상태 조회 오류: ${e.message}"
            }
        }
    }
    
    /**
     * 수신 메시지 처리
     */
    private fun processReceivedMessage(messageJson: String) {
        try {
            val parsedMessage = messageProtocol.parseMessage(messageJson)
            
            when (parsedMessage) {
                is ParsedMessage.Response -> handleResponse(parsedMessage)
                is ParsedMessage.Status -> handleStatus(parsedMessage)
                is ParsedMessage.Heartbeat -> handleHeartbeat(parsedMessage)
                is ParsedMessage.Error -> handleError(parsedMessage)
                null -> Timber.w("메시지 파싱 실패: $messageJson")
            }
        } catch (e: Exception) {
            Timber.e(e, "메시지 처리 중 예외")
            _errorMessage.value = "메시지 처리 오류: ${e.message}"
        }
    }
    
    /**
     * 응답 메시지 처리
     */
    private fun handleResponse(response: ParsedMessage.Response) {
        when (response.originalCommand) {
            "START_SEQUENCE" -> {
                if (response.status == "success") {
                    _isSequenceRunning.value = true
                    _statusMessage.value = "시퀀스 시작됨"
                } else {
                    _errorMessage.value = "시퀀스 시작 실패: ${response.result}"
                }
            }
            "STOP_SEQUENCE" -> {
                if (response.status == "success") {
                    _isSequenceRunning.value = false
                    _sequenceProgress.value = 0
                    _currentLedPair.value = null
                    _statusMessage.value = "시퀀스 정지됨"
                    
                    // 검사 데이터 업데이트
                    _testData.value = currentTestData.toList()
                }
            }
            "GET_STATUS" -> {
                // 상태 데이터 처리
                _statusMessage.value = "상태 업데이트됨"
            }
            "PING" -> {
                if (response.result == "PONG") {
                    _statusMessage.value = "ESP32 응답 정상"
                }
            }
        }
        
        Timber.d("응답 처리: ${response.originalCommand} - ${response.status}")
    }
    
    /**
     * 상태 메시지 처리
     */
    private fun handleStatus(status: ParsedMessage.Status) {
        when (status.status) {
            "CONNECTED" -> _statusMessage.value = "ESP32 연결 완료"
            "SEQUENCE_STARTED" -> {
                _isSequenceRunning.value = true
                _statusMessage.value = "시퀀스 시작"
            }
            "SEQUENCE_STOPPED" -> {
                _isSequenceRunning.value = false
                _statusMessage.value = "시퀀스 정지"
            }
        }
        
        Timber.d("상태 메시지: ${status.status}")
    }
    
    /**
     * 하트비트 처리
     */
    private fun handleHeartbeat(heartbeat: ParsedMessage.Heartbeat) {
        // 연결 유지 확인
        _statusMessage.value = "ESP32 연결 상태 양호"
        Timber.v("하트비트 수신: uptime=${heartbeat.uptime}")
    }
    
    /**
     * 오류 메시지 처리
     */
    private fun handleError(error: ParsedMessage.Error) {
        _errorMessage.value = "ESP32 오류: ${error.error} - ${error.message}"
        
        // 관련 명령의 상태 업데이트
        when (error.originalCommand) {
            "START_SEQUENCE" -> _isSequenceRunning.value = false
        }
        
        Timber.e("ESP32 오류: ${error.error} - ${error.message}")
    }
    
    /**
     * 검사 데이터 기록
     */
    fun recordTestData(ledPairId: Int, color: String, responseTime: Long, isCorrect: Boolean) {
        val dataPoint = TestDataPoint(
            timestamp = System.currentTimeMillis(),
            ledPairId = ledPairId,
            color = color,
            responseTime = responseTime,
            isCorrect = isCorrect
        )
        
        currentTestData.add(dataPoint)
        Timber.d("검사 데이터 기록: pair=$ledPairId, time=${responseTime}ms, correct=$isCorrect")
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        bluetoothManager.cleanup()
        messageProtocol.cleanupExpiredRequests()
    }
    
    /**
     * 검사 데이터 포인트 클래스
     */
    data class TestDataPoint(
        val timestamp: Long,
        val ledPairId: Int,
        val color: String,
        val responseTime: Long,
        val isCorrect: Boolean
    )
}