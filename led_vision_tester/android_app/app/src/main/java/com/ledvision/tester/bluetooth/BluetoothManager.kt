package com.ledvision.tester.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ESP32와의 Bluetooth SPP 통신을 관리하는 클래스
 * 
 * 주요 기능:
 * - ESP32 장치 검색 및 연결
 * - JSON 메시지 송수신
 * - 연결 상태 모니터링
 * - 자동 재연결 지원
 */
class BluetoothManager(private val context: Context) {
    
    companion object {
        private const val ESP32_DEVICE_NAME = "LED_Vision_Tester"
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val RECONNECT_DELAY = 3000L
        private const val HEARTBEAT_TIMEOUT = 10000L
        private const val MESSAGE_BUFFER_SIZE = 1024
    }

    // Bluetooth 어댑터 및 소켓
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // 연결 상태 관리
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _deviceInfo = MutableLiveData<DeviceInfo>()
    val deviceInfo: LiveData<DeviceInfo> = _deviceInfo
    
    // 메시지 수신 콜백
    private val _receivedMessage = MutableLiveData<String>()
    val receivedMessage: LiveData<String> = _receivedMessage
    
    // 스레드 관리
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 상태 변수
    private var isConnecting = false
    private var autoReconnect = true
    private var lastHeartbeat = 0L
    
    // 메시지 버퍼
    private val messageBuffer = StringBuilder()

    /**
     * Bluetooth 초기화
     */
    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth 어댑터를 찾을 수 없습니다")
            _connectionState.value = ConnectionState.NOT_SUPPORTED
            return false
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Timber.w("Bluetooth가 비활성화되어 있습니다")
            _connectionState.value = ConnectionState.DISABLED
            return false
        }
        
        _connectionState.value = ConnectionState.DISCONNECTED
        Timber.i("Bluetooth 초기화 완료")
        return true
    }

    /**
     * ESP32 장치 연결 시도
     */
    fun connect() {
        if (isConnecting || _connectionState.value == ConnectionState.CONNECTED) {
            Timber.w("이미 연결 중이거나 연결되어 있습니다")
            return
        }
        
        val pairedDevices = bluetoothAdapter?.bondedDevices
        val esp32Device = pairedDevices?.find { it.name == ESP32_DEVICE_NAME }
        
        if (esp32Device == null) {
            Timber.e("ESP32 장치를 찾을 수 없습니다. 페어링을 확인하세요")
            _connectionState.value = ConnectionState.DEVICE_NOT_FOUND
            return
        }
        
        connectToDevice(esp32Device)
    }

    /**
     * 특정 장치에 연결
     */
    private fun connectToDevice(device: BluetoothDevice) {
        executorService.execute {
            try {
                isConnecting = true
                mainHandler.post { _connectionState.value = ConnectionState.CONNECTING }
                
                Timber.i("ESP32 연결 시도: ${device.name} (${device.address})")
                
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                bluetoothAdapter?.cancelDiscovery()
                
                bluetoothSocket?.connect()
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                
                // 장치 정보 업데이트
                val deviceInfo = DeviceInfo(
                    name = device.name,
                    address = device.address,
                    isConnected = true,
                    signalStrength = -50 // 추정값
                )
                mainHandler.post { _deviceInfo.value = deviceInfo }
                
                mainHandler.post { _connectionState.value = ConnectionState.CONNECTED }
                Timber.i("ESP32 연결 성공")
                
                // 메시지 수신 시작
                startMessageReceiver()
                
                // 하트비트 모니터링 시작
                startHeartbeatMonitor()
                
            } catch (e: IOException) {
                Timber.e(e, "ESP32 연결 실패")
                handleConnectionError()
            } finally {
                isConnecting = false
            }
        }
    }

    /**
     * 연결 해제
     */
    fun disconnect() {
        autoReconnect = false
        
        executorService.execute {
            try {
                bluetoothSocket?.close()
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Timber.e(e, "연결 해제 중 오류")
            } finally {
                bluetoothSocket = null
                inputStream = null
                outputStream = null
                
                mainHandler.post { 
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _deviceInfo.value = _deviceInfo.value?.copy(isConnected = false)
                }
                
                Timber.i("ESP32 연결 해제 완료")
            }
        }
    }

    /**
     * JSON 메시지 전송
     */
    fun sendMessage(message: String): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED || outputStream == null) {
            Timber.w("연결되지 않음 - 메시지 전송 실패")
            return false
        }
        
        return try {
            executorService.execute {
                try {
                    outputStream?.write("$message\n".toByteArray())
                    outputStream?.flush()
                    Timber.d("메시지 전송: ${message.take(100)}...")
                } catch (e: IOException) {
                    Timber.e(e, "메시지 전송 실패")
                    handleConnectionError()
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "메시지 전송 중 예외")
            false
        }
    }

    /**
     * 메시지 수신 스레드 시작
     */
    private fun startMessageReceiver() {
        executorService.execute {
            val buffer = ByteArray(MESSAGE_BUFFER_SIZE)
            
            while (_connectionState.value == ConnectionState.CONNECTED && inputStream != null) {
                try {
                    val bytes = inputStream!!.read(buffer)
                    if (bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        processReceivedData(receivedData)
                    }
                } catch (e: IOException) {
                    Timber.e(e, "메시지 수신 중 오류")
                    handleConnectionError()
                    break
                }
            }
        }
    }

    /**
     * 수신 데이터 처리
     */
    private fun processReceivedData(data: String) {
        messageBuffer.append(data)
        
        // 완전한 메시지(개행 문자로 구분) 처리
        var newlineIndex = messageBuffer.indexOf('\n')
        while (newlineIndex != -1) {
            val message = messageBuffer.substring(0, newlineIndex).trim()
            messageBuffer.delete(0, newlineIndex + 1)
            
            if (message.isNotEmpty()) {
                // 하트비트 업데이트
                if (message.contains("\"type\":\"HEARTBEAT\"")) {
                    lastHeartbeat = System.currentTimeMillis()
                }
                
                mainHandler.post { 
                    _receivedMessage.value = message 
                    Timber.d("메시지 수신: ${message.take(100)}...")
                }
            }
            
            newlineIndex = messageBuffer.indexOf('\n')
        }
        
        // 버퍼 크기 제한
        if (messageBuffer.length > MESSAGE_BUFFER_SIZE * 2) {
            messageBuffer.clear()
            Timber.w("메시지 버퍼 오버플로우 - 버퍼 초기화")
        }
    }

    /**
     * 하트비트 모니터링 시작
     */
    private fun startHeartbeatMonitor() {
        executorService.execute {
            lastHeartbeat = System.currentTimeMillis()
            
            while (_connectionState.value == ConnectionState.CONNECTED) {
                Thread.sleep(5000)
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                    Timber.w("하트비트 타임아웃 - 연결 상태 확인")
                    handleConnectionError()
                    break
                }
            }
        }
    }

    /**
     * 연결 오류 처리
     */
    private fun handleConnectionError() {
        mainHandler.post { _connectionState.value = ConnectionState.CONNECTION_LOST }
        
        disconnect()
        
        if (autoReconnect) {
            Timber.i("자동 재연결 시도...")
            mainHandler.postDelayed({ connect() }, RECONNECT_DELAY)
        }
    }

    /**
     * 자동 재연결 설정
     */
    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
    }

    /**
     * 현재 연결 상태 확인
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        autoReconnect = false
        disconnect()
        executorService.shutdown()
    }

    /**
     * 연결 상태 열거형
     */
    enum class ConnectionState {
        NOT_SUPPORTED,      // Bluetooth 미지원
        DISABLED,           // Bluetooth 비활성화
        DISCONNECTED,       // 연결 해제
        DEVICE_NOT_FOUND,   // 장치 찾기 실패
        CONNECTING,         // 연결 중
        CONNECTED,          // 연결됨
        CONNECTION_LOST     // 연결 끊김
    }

    /**
     * 장치 정보 데이터 클래스
     */
    data class DeviceInfo(
        val name: String,
        val address: String,
        val isConnected: Boolean,
        val signalStrength: Int
    )
}