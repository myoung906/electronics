package com.ledvision.tester.protocol

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import timber.log.Timber
import java.util.*

/**
 * ESP32와의 JSON 통신 프로토콜을 처리하는 클래스
 * 
 * 지원하는 메시지 타입:
 * - COMMAND: Android → ESP32 명령
 * - RESPONSE: ESP32 → Android 응답  
 * - STATUS: ESP32 → Android 상태 알림
 * - HEARTBEAT: ESP32 → Android 연결 확인
 * - ERROR: ESP32 → Android 오류 알림
 */
class MessageProtocol {
    
    companion object {
        private val gson = Gson()
        private const val REQUEST_TIMEOUT = 5000L // 5초
    }
    
    // 대기 중인 요청 관리
    private val pendingRequests = mutableMapOf<String, PendingRequest>()
    
    /**
     * ESP32에 명령 전송을 위한 JSON 생성
     */
    fun createCommand(command: String, params: Any? = null): String {
        val requestId = generateRequestId()
        val commandMsg = CommandMessage(
            command = command,
            id = requestId,
            params = params,
            timestamp = System.currentTimeMillis()
        )
        
        // 응답 대기 목록에 추가
        pendingRequests[requestId] = PendingRequest(
            requestId = requestId,
            command = command,
            timestamp = System.currentTimeMillis()
        )
        
        return gson.toJson(commandMsg)
    }
    
    /**
     * 시퀀스 시작 명령
     */
    fun createStartSequenceCommand(type: Int = 0, interval: Int = 800): String {
        val params = SequenceParams(type = type, interval = interval)
        return createCommand("START_SEQUENCE", params)
    }
    
    /**
     * 시퀀스 정지 명령
     */
    fun createStopSequenceCommand(): String {
        return createCommand("STOP_SEQUENCE")
    }
    
    /**
     * LED 제어 명령
     */
    fun createSetLedCommand(pairId: Int, color: String, state: Boolean): String {
        val params = LedParams(pair = pairId, color = color, state = state)
        return createCommand("SET_LED", params)
    }
    
    /**
     * 상태 조회 명령
     */
    fun createGetStatusCommand(): String {
        return createCommand("GET_STATUS")
    }
    
    /**
     * Ping 명령
     */
    fun createPingCommand(): String {
        return createCommand("PING")
    }
    
    /**
     * 수신된 JSON 메시지 파싱
     */
    fun parseMessage(jsonString: String): ParsedMessage? {
        return try {
            val baseMessage = gson.fromJson(jsonString, BaseMessage::class.java)
            
            when (baseMessage.type?.uppercase()) {
                "RESPONSE" -> parseResponse(jsonString)
                "STATUS" -> parseStatus(jsonString) 
                "HEARTBEAT" -> parseHeartbeat(jsonString)
                "ERROR" -> parseError(jsonString)
                else -> {
                    Timber.w("알 수 없는 메시지 타입: ${baseMessage.type}")
                    null
                }
            }
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "JSON 파싱 오류: $jsonString")
            null
        }
    }
    
    /**
     * 응답 메시지 파싱
     */
    private fun parseResponse(jsonString: String): ParsedMessage.Response {
        val response = gson.fromJson(jsonString, ResponseMessage::class.java)
        
        // 대기 중인 요청과 매칭
        val pendingRequest = response.id?.let { pendingRequests.remove(it) }
        
        return ParsedMessage.Response(
            requestId = response.id,
            status = response.status,
            result = response.result,
            data = response.data,
            timestamp = response.timestamp,
            originalCommand = pendingRequest?.command
        )
    }
    
    /**
     * 상태 메시지 파싱
     */
    private fun parseStatus(jsonString: String): ParsedMessage.Status {
        val status = gson.fromJson(jsonString, StatusMessage::class.java)
        return ParsedMessage.Status(
            status = status.status,
            timestamp = status.timestamp
        )
    }
    
    /**
     * 하트비트 메시지 파싱
     */
    private fun parseHeartbeat(jsonString: String): ParsedMessage.Heartbeat {
        val heartbeat = gson.fromJson(jsonString, HeartbeatMessage::class.java)
        return ParsedMessage.Heartbeat(
            device = heartbeat.device,
            uptime = heartbeat.uptime,
            timestamp = heartbeat.timestamp
        )
    }
    
    /**
     * 오류 메시지 파싱
     */
    private fun parseError(jsonString: String): ParsedMessage.Error {
        val error = gson.fromJson(jsonString, ErrorMessage::class.java)
        
        // 해당 요청을 대기 목록에서 제거
        val pendingRequest = error.id?.let { pendingRequests.remove(it) }
        
        return ParsedMessage.Error(
            requestId = error.id,
            error = error.error,
            message = error.message,
            timestamp = error.timestamp,
            originalCommand = pendingRequest?.command
        )
    }
    
    /**
     * 타임아웃된 요청 정리
     */
    fun cleanupExpiredRequests(): List<String> {
        val currentTime = System.currentTimeMillis()
        val expiredRequests = mutableListOf<String>()
        
        val iterator = pendingRequests.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > REQUEST_TIMEOUT) {
                expiredRequests.add(entry.key)
                iterator.remove()
            }
        }
        
        if (expiredRequests.isNotEmpty()) {
            Timber.w("타임아웃된 요청 ${expiredRequests.size}개 정리")
        }
        
        return expiredRequests
    }
    
    /**
     * 대기 중인 요청 수
     */
    fun getPendingRequestCount(): Int = pendingRequests.size
    
    /**
     * 요청 ID 생성
     */
    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${Random().nextInt(1000)}"
    }
    
    // === 데이터 클래스 정의 ===
    
    /**
     * 기본 메시지 구조
     */
    data class BaseMessage(
        val type: String?
    )
    
    /**
     * 명령 메시지
     */
    data class CommandMessage(
        val command: String,
        val id: String,
        val params: Any?,
        val timestamp: Long
    )
    
    /**
     * 응답 메시지
     */
    data class ResponseMessage(
        val type: String?,
        val id: String?,
        val status: String?,
        val result: String?,
        val data: Any?,
        val timestamp: Long?
    )
    
    /**
     * 상태 메시지
     */
    data class StatusMessage(
        val type: String?,
        val status: String?,
        val timestamp: Long?
    )
    
    /**
     * 하트비트 메시지
     */
    data class HeartbeatMessage(
        val type: String?,
        val device: String?,
        val uptime: Long?,
        val timestamp: Long?
    )
    
    /**
     * 오류 메시지
     */
    data class ErrorMessage(
        val type: String?,
        val id: String?,
        val error: String?,
        val message: String?,
        val timestamp: Long?
    )
    
    /**
     * 시퀀스 매개변수
     */
    data class SequenceParams(
        val type: Int,      // 0: random, 1: sequential
        val interval: Int   // ms
    )
    
    /**
     * LED 제어 매개변수
     */
    data class LedParams(
        val pair: Int,      // LED 쌍 번호
        val color: String,  // "red" or "green"
        val state: Boolean  // true=ON, false=OFF
    )
    
    /**
     * 대기 중인 요청 정보
     */
    private data class PendingRequest(
        val requestId: String,
        val command: String,
        val timestamp: Long
    )
}

/**
 * 파싱된 메시지 결과
 */
sealed class ParsedMessage {
    data class Response(
        val requestId: String?,
        val status: String?,
        val result: String?,
        val data: Any?,
        val timestamp: Long?,
        val originalCommand: String?
    ) : ParsedMessage()
    
    data class Status(
        val status: String?,
        val timestamp: Long?
    ) : ParsedMessage()
    
    data class Heartbeat(
        val device: String?,
        val uptime: Long?,
        val timestamp: Long?
    ) : ParsedMessage()
    
    data class Error(
        val requestId: String?,
        val error: String?,
        val message: String?,
        val timestamp: Long?,
        val originalCommand: String?
    ) : ParsedMessage()
}