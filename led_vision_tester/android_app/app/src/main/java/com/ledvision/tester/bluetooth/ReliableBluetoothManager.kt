/*
 * 강화된 Bluetooth 통신 관리자
 * 
 * 기능:
 * - 자동 재연결
 * - 연결 품질 모니터링  
 * - 메시지 재전송
 * - 오류 복구
 * - 연결 상태 추적
 * - 백그라운드 연결 유지
 * 
 * 작성일: 2025-08-10
 * Phase 2 Week 6
 */

package com.ledvision.tester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayDeque

/**
 * 연결 상태 열거형
 */
enum class ConnectionState {
    DISCONNECTED,    // 연결 해제됨
    CONNECTING,      // 연결 중
    CONNECTED,       // 연결됨
    RECONNECTING,    // 재연결 중
    FAILED,          // 연결 실패
    LOST            // 연결 끊어짐
}

/**
 * 연결 품질 정보
 */
data class ConnectionQuality(
    val signalStrength: Int = -1,      // RSSI 값
    val latency: Long = 0L,            // 응답 지연 시간 (ms)
    val packetLoss: Float = 0f,        // 패킷 손실률 (%)
    val errorRate: Float = 0f,         // 오류율 (%)
    val throughput: Long = 0L          // 처리량 (bytes/sec)
)

/**
 * 메시지 전송 결과
 */
data class MessageResult(
    val success: Boolean,
    val messageId: String,
    val error: String? = null,
    val responseTime: Long = 0L
)

/**
 * 대기 중인 메시지
 */
private data class PendingMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val callback: ((MessageResult) -> Unit)? = null
)

/**
 * 강화된 Bluetooth 관리자
 */
class ReliableBluetoothManager(private val context: Context) {
    
    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val HEARTBEAT_INTERVAL_MS = 5000L
        private const val MESSAGE_TIMEOUT_MS = 10000L
        private const val QUALITY_CHECK_INTERVAL_MS = 30000L
        private const val BUFFER_SIZE = 1024
    }
    
    // Bluetooth 어댑터 및 소켓
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // 연결 대상 장치
    private var targetDevice: BluetoothDevice? = null
    private var targetDeviceAddress: String? = null
    
    // 상태 관리
    private val _connectionState = MutableLiveData<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _connectionQuality = MutableLiveData<ConnectionQuality>()
    val connectionQuality: LiveData<ConnectionQuality> = _connectionQuality
    
    private val isConnecting = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    
    // 메시지 관리
    private val pendingMessages = ConcurrentHashMap<String, PendingMessage>()
    private val messageQueue = ArrayDeque<PendingMessage>()
    private val messageIdCounter = AtomicInteger(0)
    
    // 코루틴 및 핸들러
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 통계 정보
    private var totalMessagesSent = AtomicInteger(0)
    private var totalMessagesReceived = AtomicInteger(0)
    private var totalErrors = AtomicInteger(0)
    private var connectionStartTime = 0L
    private var lastHeartbeatTime = 0L
    
    // 콜백
    private var onMessageReceived: ((String) -> Unit)? = null
    private var onConnectionStateChanged: ((ConnectionState) -> Unit)? = null
    private var onError: ((String, Throwable?) -> Unit)? = null
    
    // Bluetooth 상태 감지 리시버
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    handleBluetoothStateChange(state)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.address == targetDeviceAddress) {
                        handleConnectionLost()
                    }
                }
            }
        }
    }
    
    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        registerBluetoothReceiver()
        startQualityMonitoring()
        startHeartbeat()
    }
    
    /**
     * 초기화 및 권한 확인
     */
    fun initialize(): Boolean {
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth not supported on this device")
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Timber.w("Bluetooth is not enabled")
            return false
        }
        
        if (!checkPermissions()) {
            Timber.e("Bluetooth permissions not granted")
            return false
        }
        
        return true
    }
    
    /**
     * 권한 확인
     */
    private fun checkPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 장치 연결 시작
     */
    fun connectToDevice(deviceAddress: String, autoReconnect: Boolean = true) {
        if (isConnecting.get()) {
            Timber.w("Connection already in progress")
            return
        }
        
        targetDeviceAddress = deviceAddress
        targetDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        
        if (targetDevice == null) {
            reportError("Invalid device address: $deviceAddress")
            return
        }
        
        coroutineScope.launch {
            performConnection(autoReconnect)
        }
    }
    
    /**
     * 실제 연결 수행
     */
    private suspend fun performConnection(autoReconnect: Boolean = true) {
        if (!isConnecting.compareAndSet(false, true)) {
            return
        }
        
        try {
            updateConnectionState(ConnectionState.CONNECTING)
            
            // 기존 연결 정리
            closeConnection()
            
            // 소켓 생성 및 연결
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                bluetoothSocket = targetDevice?.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                
                // 연결 시도 (타임아웃 적용)
                withTimeout(30000L) { // 30초 타임아웃
                    bluetoothSocket?.connect()
                }
                
                // 스트림 초기화
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                
                // 연결 성공
                connectionStartTime = System.currentTimeMillis()
                lastHeartbeatTime = connectionStartTime
                reconnectAttempts.set(0)
                
                updateConnectionState(ConnectionState.CONNECTED)
                
                // 수신 스레드 시작
                startReceiveLoop()
                
                Timber.i("Successfully connected to ${targetDevice?.name}")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Connection failed")
            handleConnectionFailure(e, autoReconnect)
        } finally {
            isConnecting.set(false)
        }
    }
    
    /**
     * 메시지 전송 (비동기, 재시도 지원)
     */
    fun sendMessage(
        message: String,
        requiresResponse: Boolean = false,
        maxRetries: Int = 3,
        callback: ((MessageResult) -> Unit)? = null
    ): String {
        val messageId = generateMessageId()
        val pendingMessage = PendingMessage(
            id = messageId,
            content = message,
            timestamp = System.currentTimeMillis(),
            maxRetries = maxRetries,
            callback = callback
        )
        
        coroutineScope.launch {
            sendMessageInternal(pendingMessage)
        }
        
        return messageId
    }
    
    /**
     * 내부 메시지 전송 로직
     */
    private suspend fun sendMessageInternal(pendingMessage: PendingMessage) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            val result = MessageResult(false, pendingMessage.id, "Not connected")
            pendingMessage.callback?.invoke(result)
            return
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            outputStream?.write((pendingMessage.content + "\n").toByteArray())
            outputStream?.flush()
            
            totalMessagesSent.incrementAndGet()
            val responseTime = System.currentTimeMillis() - startTime
            
            val result = MessageResult(true, pendingMessage.id, null, responseTime)
            pendingMessage.callback?.invoke(result)
            
            Timber.d("Message sent successfully: ${pendingMessage.id}")
            
        } catch (e: IOException) {
            Timber.e(e, "Failed to send message: ${pendingMessage.id}")
            
            // 재시도 로직
            if (pendingMessage.retryCount < pendingMessage.maxRetries) {
                val retryMessage = pendingMessage.copy(retryCount = pendingMessage.retryCount + 1)
                delay(1000L * (pendingMessage.retryCount + 1)) // 지수 백오프
                sendMessageInternal(retryMessage)
            } else {
                val result = MessageResult(false, pendingMessage.id, e.message)
                pendingMessage.callback?.invoke(result)
                totalErrors.incrementAndGet()
            }
        }
    }
    
    /**
     * 수신 루프 시작
     */
    private fun startReceiveLoop() {
        coroutineScope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            val messageBuffer = StringBuilder()
            
            try {
                while (_connectionState.value == ConnectionState.CONNECTED && inputStream != null) {
                    val bytesRead = inputStream!!.read(buffer)
                    if (bytesRead > 0) {
                        val receivedData = String(buffer, 0, bytesRead)
                        messageBuffer.append(receivedData)
                        
                        // 완성된 메시지 처리 (개행으로 구분)
                        processReceivedData(messageBuffer)
                        
                        totalMessagesReceived.incrementAndGet()
                        lastHeartbeatTime = System.currentTimeMillis()
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Receive loop interrupted")
                handleConnectionLost()
            }
        }
    }
    
    /**
     * 수신 데이터 처리
     */
    private fun processReceivedData(messageBuffer: StringBuilder) {
        while (messageBuffer.contains('\n')) {
            val newlineIndex = messageBuffer.indexOf('\n')
            val completeMessage = messageBuffer.substring(0, newlineIndex).trim()
            messageBuffer.delete(0, newlineIndex + 1)
            
            if (completeMessage.isNotEmpty()) {
                mainHandler.post {
                    onMessageReceived?.invoke(completeMessage)
                }
                
                Timber.d("Message received: $completeMessage")
            }
        }
    }
    
    /**
     * 자동 재연결 로직
     */
    private fun startAutoReconnect() {
        if (_connectionState.value == ConnectionState.RECONNECTING) {
            return // 이미 재연결 중
        }
        
        val currentAttempts = reconnectAttempts.incrementAndGet()
        if (currentAttempts > MAX_RECONNECT_ATTEMPTS) {
            updateConnectionState(ConnectionState.FAILED)
            Timber.e("Max reconnection attempts reached")
            return
        }
        
        updateConnectionState(ConnectionState.RECONNECTING)
        
        coroutineScope.launch {
            delay(RECONNECT_DELAY_MS * currentAttempts) // 점진적 지연
            
            targetDeviceAddress?.let { address ->
                Timber.i("Attempting reconnection $currentAttempts/$MAX_RECONNECT_ATTEMPTS")
                performConnection(autoReconnect = true)
            }
        }
    }
    
    /**
     * 연결 품질 모니터링
     */
    private fun startQualityMonitoring() {
        coroutineScope.launch {
            while (true) {
                delay(QUALITY_CHECK_INTERVAL_MS)
                
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    updateConnectionQuality()
                }
            }
        }
    }
    
    /**
     * 연결 품질 업데이트
     */
    private fun updateConnectionQuality() {
        val currentTime = System.currentTimeMillis()
        val connectionDuration = currentTime - connectionStartTime
        
        if (connectionDuration == 0L) return
        
        val quality = ConnectionQuality(
            signalStrength = -50, // 실제 RSSI 측정 필요
            latency = calculateAverageLatency(),
            packetLoss = calculatePacketLoss(),
            errorRate = (totalErrors.get().toFloat() / totalMessagesSent.get().coerceAtLeast(1)) * 100f,
            throughput = (totalMessagesReceived.get() * 1000L / connectionDuration)
        )
        
        _connectionQuality.postValue(quality)
    }
    
    /**
     * 하트비트 전송
     */
    private fun startHeartbeat() {
        coroutineScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    sendHeartbeat()
                    checkConnectionHealth()
                }
            }
        }
    }
    
    /**
     * 하트비트 메시지 전송
     */
    private fun sendHeartbeat() {
        val heartbeatMessage = """{"type":"HEARTBEAT","timestamp":${System.currentTimeMillis()}}"""
        
        sendMessage(heartbeatMessage, requiresResponse = false) { result ->
            if (!result.success) {
                Timber.w("Heartbeat failed: ${result.error}")
            }
        }
    }
    
    /**
     * 연결 상태 확인
     */
    private fun checkConnectionHealth() {
        val timeSinceLastActivity = System.currentTimeMillis() - lastHeartbeatTime
        
        if (timeSinceLastActivity > HEARTBEAT_INTERVAL_MS * 3) {
            Timber.w("Connection seems unhealthy, initiating reconnection")
            handleConnectionLost()
        }
    }
    
    /**
     * 연결 상실 처리
     */
    private fun handleConnectionLost() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            updateConnectionState(ConnectionState.LOST)
            closeConnection()
            startAutoReconnect()
        }
    }
    
    /**
     * 연결 실패 처리
     */
    private fun handleConnectionFailure(error: Throwable, autoReconnect: Boolean) {
        updateConnectionState(ConnectionState.FAILED)
        reportError("Connection failed", error)
        
        if (autoReconnect) {
            startAutoReconnect()
        }
    }
    
    /**
     * Bluetooth 상태 변경 처리
     */
    private fun handleBluetoothStateChange(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                Timber.w("Bluetooth turned off")
                closeConnection()
                updateConnectionState(ConnectionState.DISCONNECTED)
            }
            BluetoothAdapter.STATE_ON -> {
                Timber.i("Bluetooth turned on")
                // 자동 재연결 시도
                targetDeviceAddress?.let { address ->
                    connectToDevice(address)
                }
            }
        }
    }
    
    /**
     * 연결 해제
     */
    fun disconnect() {
        coroutineScope.launch {
            closeConnection()
            updateConnectionState(ConnectionState.DISCONNECTED)
        }
    }
    
    /**
     * 연결 정리
     */
    private fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Timber.e(e, "Error closing connection")
        } finally {
            inputStream = null
            outputStream = null
            bluetoothSocket = null
        }
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // 리시버가 등록되지 않은 경우
        }
        
        coroutineScope.cancel()
        closeConnection()
        pendingMessages.clear()
        messageQueue.clear()
    }
    
    // ================================
    // 유틸리티 메소드들
    // ================================
    
    private fun generateMessageId(): String = "msg_${messageIdCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (_connectionState.value != newState) {
            _connectionState.postValue(newState)
            onConnectionStateChanged?.invoke(newState)
            Timber.i("Connection state changed to: $newState")
        }
    }
    
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }
    
    private fun reportError(message: String, throwable: Throwable? = null) {
        Timber.e(throwable, message)
        onError?.invoke(message, throwable)
        totalErrors.incrementAndGet()
    }
    
    private fun calculateAverageLatency(): Long {
        // 실제 구현에서는 최근 응답 시간들의 평균 계산
        return 50L // 임시값
    }
    
    private fun calculatePacketLoss(): Float {
        // 실제 구현에서는 전송된 메시지 대비 응답 받은 메시지 비율 계산
        return 0.5f // 임시값
    }
    
    // ================================
    // 콜백 등록 메소드들
    // ================================
    
    fun setOnMessageReceivedListener(listener: (String) -> Unit) {
        onMessageReceived = listener
    }
    
    fun setOnConnectionStateChangedListener(listener: (ConnectionState) -> Unit) {
        onConnectionStateChanged = listener
    }
    
    fun setOnErrorListener(listener: (String, Throwable?) -> Unit) {
        onError = listener
    }
    
    // ================================
    // 공개 정보 접근 메소드들  
    // ================================
    
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
    
    fun getConnectionStatistics(): Map<String, Any> = mapOf(
        "totalMessagesSent" to totalMessagesSent.get(),
        "totalMessagesReceived" to totalMessagesReceived.get(),
        "totalErrors" to totalErrors.get(),
        "connectionDuration" to (System.currentTimeMillis() - connectionStartTime),
        "reconnectAttempts" to reconnectAttempts.get()
    )
    
    fun getCurrentDevice(): BluetoothDevice? = targetDevice
}