package com.ledvision.tester.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ledvision.tester.model.TestResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * 최적화된 데이터 수집 엔진
 * 
 * 기능:
 * - 실시간 데이터 분석
 * - 적응형 샘플링
 * - 메모리 효율적 데이터 관리
 * - 통계적 이상치 탐지
 * - 성능 최적화
 * 
 * @author LED Vision Tester Team
 * @version 2.0
 */
class DataCollectionEngine {
    
    companion object {
        private const val TAG = "DataCollectionEngine"
        private const val MAX_BUFFER_SIZE = 10000
        private const val MIN_RESPONSE_TIME = 50L
        private const val MAX_RESPONSE_TIME = 5000L
        private const val OUTLIER_THRESHOLD = 2.5 // Z-score 기준
        private const val ADAPTIVE_WINDOW_SIZE = 20
    }
    
    // === 데이터 버퍼 및 상태 ===
    private val dataBuffer = mutableListOf<TestDataPoint>()
    private val realtimeBuffer = ArrayDeque<TestDataPoint>(100)
    private val statisticsBuffer = ArrayDeque<TestStatistics>(50)
    
    private val _dataCollectionState = MutableLiveData<DataCollectionState>()
    val dataCollectionState: LiveData<DataCollectionState> = _dataCollectionState
    
    private val _realtimeMetrics = MutableLiveData<RealtimeMetrics>()
    val realtimeMetrics: LiveData<RealtimeMetrics> = _realtimeMetrics
    
    private val _qualityAssessment = MutableLiveData<DataQualityAssessment>()
    val qualityAssessment: LiveData<DataQualityAssessment> = _qualityAssessment
    
    // === 성능 카운터 ===
    private val totalDataPoints = AtomicInteger(0)
    private val validDataPoints = AtomicInteger(0)
    private val outlierCount = AtomicInteger(0)
    private val processingErrorCount = AtomicInteger(0)
    
    // === 코루틴 스코프 ===
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // === 적응형 매개변수 ===
    private var adaptiveSamplingRate = 1.0
    private var currentComplexityLevel = ComplexityLevel.NORMAL
    
    init {
        _dataCollectionState.value = DataCollectionState.READY
        startPerformanceMonitoring()
    }
    
    /**
     * 데이터 수집 시작
     */
    suspend fun startCollection(configuration: CollectionConfiguration) {
        try {
            _dataCollectionState.value = DataCollectionState.COLLECTING
            
            clearBuffers()
            resetCounters()
            
            // 설정에 따른 적응형 매개변수 설정
            setupAdaptiveParameters(configuration)
            
            Timber.i("데이터 수집 시작 - 설정: $configuration")
            
        } catch (e: Exception) {
            Timber.e(e, "데이터 수집 시작 실패")
            _dataCollectionState.value = DataCollectionState.ERROR
            throw e
        }
    }
    
    /**
     * 단일 데이터 포인트 처리 (최적화됨)
     */
    suspend fun processDataPoint(
        timestamp: Long,
        ledPairId: Int,
        color: String,
        responseTime: Long,
        position: Triple<Double, Double, Double>,
        isCorrect: Boolean,
        confidence: Float = 1.0f
    ) = withContext(Dispatchers.Default) {
        
        val dataPoint = TestDataPoint(
            timestamp = timestamp,
            ledPairId = ledPairId,
            color = color,
            responseTime = responseTime,
            position = position,
            isCorrect = isCorrect,
            confidence = confidence,
            sequenceId = generateSequenceId(),
            metadata = generateMetadata(timestamp, responseTime)
        )
        
        // 1. 기본 유효성 검사
        if (!validateDataPoint(dataPoint)) {
            processingErrorCount.incrementAndGet()
            return@withContext false
        }
        
        // 2. 이상치 탐지
        val outlierAnalysis = detectOutlier(dataPoint)
        if (outlierAnalysis.isOutlier && outlierAnalysis.confidence > 0.8) {
            outlierCount.incrementAndGet()
            Timber.w("이상치 탐지: $dataPoint, 이유: ${outlierAnalysis.reason}")
        }
        
        // 3. 적응형 샘플링 적용
        if (!shouldSampleDataPoint(dataPoint)) {
            return@withContext true // 유효하지만 샘플링에서 제외
        }
        
        // 4. 데이터 저장 (메모리 효율적)
        storeDataPoint(dataPoint, outlierAnalysis)
        
        // 5. 실시간 메트릭 업데이트
        updateRealtimeMetrics(dataPoint)
        
        // 6. 품질 평가 업데이트
        updateQualityAssessment()
        
        totalDataPoints.incrementAndGet()
        if (!outlierAnalysis.isOutlier) {
            validDataPoints.incrementAndGet()
        }
        
        true
    }
    
    /**
     * 배치 데이터 처리 (대량 데이터 최적화)
     */
    suspend fun processBatchData(
        dataPoints: List<RawDataPoint>
    ) = withContext(Dispatchers.Default) {
        
        val results = mutableListOf<ProcessedDataResult>()
        
        // 병렬 처리로 성능 향상
        val chunks = dataPoints.chunked(100) // 100개씩 처리
        
        chunks.forEach { chunk ->
            val chunkResults = chunk.map { rawData ->
                async {
                    processRawDataPoint(rawData)
                }
            }.awaitAll()
            
            results.addAll(chunkResults)
        }
        
        // 배치 통계 업데이트
        updateBatchStatistics(results)
        
        results
    }
    
    /**
     * 데이터 포인트 유효성 검사 (최적화됨)
     */
    private fun validateDataPoint(dataPoint: TestDataPoint): Boolean {
        return when {
            dataPoint.responseTime < MIN_RESPONSE_TIME -> {
                Timber.d("응답시간 너무 짧음: ${dataPoint.responseTime}ms")
                false
            }
            dataPoint.responseTime > MAX_RESPONSE_TIME -> {
                Timber.d("응답시간 너무 김: ${dataPoint.responseTime}ms")
                false
            }
            dataPoint.ledPairId < 0 || dataPoint.ledPairId >= 36 -> {
                Timber.d("잘못된 LED 쌍 ID: ${dataPoint.ledPairId}")
                false
            }
            dataPoint.color !in listOf("red", "green") -> {
                Timber.d("잘못된 색상: ${dataPoint.color}")
                false
            }
            dataPoint.confidence < 0.1f || dataPoint.confidence > 1.0f -> {
                Timber.d("잘못된 신뢰도: ${dataPoint.confidence}")
                false
            }
            else -> true
        }
    }
    
    /**
     * 이상치 탐지 (통계적 접근)
     */
    private fun detectOutlier(dataPoint: TestDataPoint): OutlierAnalysis {
        if (realtimeBuffer.size < 10) {
            return OutlierAnalysis(false, 0.0, "샘플 부족")
        }
        
        val recentResponseTimes = realtimeBuffer
            .takeLast(ADAPTIVE_WINDOW_SIZE)
            .map { it.responseTime.toDouble() }
        
        val mean = recentResponseTimes.average()
        val stdDev = calculateStandardDeviation(recentResponseTimes, mean)
        
        if (stdDev < 1e-6) {
            return OutlierAnalysis(false, 1.0, "편차 없음")
        }
        
        val zScore = abs(dataPoint.responseTime - mean) / stdDev
        
        return when {
            zScore > OUTLIER_THRESHOLD -> OutlierAnalysis(
                true, 
                minOf(zScore / OUTLIER_THRESHOLD, 1.0), 
                "응답시간 Z-score: ${String.format("%.2f", zScore)}"
            )
            else -> OutlierAnalysis(false, zScore / OUTLIER_THRESHOLD, "정상 범위")
        }
    }
    
    /**
     * 적응형 샘플링 결정
     */
    private fun shouldSampleDataPoint(dataPoint: TestDataPoint): Boolean {
        when (currentComplexityLevel) {
            ComplexityLevel.LOW -> return Math.random() < 0.3
            ComplexityLevel.NORMAL -> return Math.random() < adaptiveSamplingRate
            ComplexityLevel.HIGH -> return true
        }
    }
    
    /**
     * 메모리 효율적 데이터 저장
     */
    private fun storeDataPoint(dataPoint: TestDataPoint, outlierAnalysis: OutlierAnalysis) {
        // 버퍼 크기 관리
        if (dataBuffer.size >= MAX_BUFFER_SIZE) {
            dataBuffer.removeAt(0) // FIFO 방식
        }
        
        dataBuffer.add(dataPoint)
        
        // 실시간 버퍼 업데이트
        realtimeBuffer.addLast(dataPoint)
        if (realtimeBuffer.size > 100) {
            realtimeBuffer.removeFirst()
        }
    }
    
    /**
     * 실시간 메트릭 업데이트
     */
    private fun updateRealtimeMetrics(dataPoint: TestDataPoint) {
        engineScope.launch {
            val recentData = realtimeBuffer.takeLast(20)
            
            val avgResponseTime = recentData.map { it.responseTime }.average()
            val accuracy = recentData.count { it.isCorrect }.toDouble() / recentData.size
            val throughput = calculateThroughput()
            
            val metrics = RealtimeMetrics(
                averageResponseTime = avgResponseTime,
                accuracy = accuracy,
                throughput = throughput,
                bufferSize = dataBuffer.size,
                outlierRate = outlierCount.get().toDouble() / maxOf(totalDataPoints.get(), 1),
                dataQualityScore = calculateDataQualityScore(recentData)
            )
            
            _realtimeMetrics.postValue(metrics)
        }
    }
    
    /**
     * 품질 평가 업데이트
     */
    private fun updateQualityAssessment() {
        engineScope.launch {
            val assessment = DataQualityAssessment(
                completeness = calculateCompleteness(),
                consistency = calculateConsistency(),
                accuracy = calculateAccuracy(),
                timeliness = calculateTimeliness(),
                overallScore = 0.0 // 아래에서 계산
            )
            
            assessment.overallScore = (
                assessment.completeness * 0.25 +
                assessment.consistency * 0.25 +
                assessment.accuracy * 0.3 +
                assessment.timeliness * 0.2
            )
            
            _qualityAssessment.postValue(assessment)
        }
    }
    
    /**
     * 성능 모니터링 시작
     */
    private fun startPerformanceMonitoring() {
        engineScope.launch {
            while (true) {
                delay(5000) // 5초마다 모니터링
                
                // 메모리 사용량 체크
                val memoryUsage = calculateMemoryUsage()
                if (memoryUsage > 0.8) {
                    optimizeMemory()
                }
                
                // 처리 성능 체크
                val processingRate = calculateProcessingRate()
                if (processingRate < 0.5) {
                    adjustPerformanceSettings()
                }
            }
        }
    }
    
    // === 유틸리티 메서드들 ===
    
    private fun calculateStandardDeviation(values: List<Double>, mean: Double): Double {
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    private fun generateSequenceId(): String {
        return "${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
    
    private fun generateMetadata(timestamp: Long, responseTime: Long): Map<String, Any> {
        return mapOf(
            "system_load" to getSystemLoad(),
            "battery_level" to getBatteryLevel(),
            "signal_strength" to getSignalStrength(),
            "processing_delay" to calculateProcessingDelay(timestamp)
        )
    }
    
    private fun calculateThroughput(): Double {
        val currentTime = System.currentTimeMillis()
        val oneSecondAgo = currentTime - 1000
        
        return realtimeBuffer.count { it.timestamp > oneSecondAgo }.toDouble()
    }
    
    private fun calculateDataQualityScore(data: List<TestDataPoint>): Double {
        if (data.isEmpty()) return 0.0
        
        val avgConfidence = data.map { it.confidence }.average()
        val validityRate = data.count { 
            it.responseTime in MIN_RESPONSE_TIME..MAX_RESPONSE_TIME 
        }.toDouble() / data.size
        
        return (avgConfidence * 0.6 + validityRate * 0.4)
    }
    
    // 각종 계산 메서드들의 구현...
    private fun calculateCompleteness() = validDataPoints.get().toDouble() / maxOf(totalDataPoints.get(), 1)
    private fun calculateConsistency() = 1.0 - (outlierCount.get().toDouble() / maxOf(totalDataPoints.get(), 1))
    private fun calculateAccuracy() = if (realtimeBuffer.isEmpty()) 1.0 else realtimeBuffer.count { it.isCorrect }.toDouble() / realtimeBuffer.size
    private fun calculateTimeliness() = 0.95 // 구현 필요
    private fun calculateMemoryUsage() = 0.3 // 구현 필요
    private fun calculateProcessingRate() = 0.8 // 구현 필요
    private fun getSystemLoad() = 0.5
    private fun getBatteryLevel() = 85
    private fun getSignalStrength() = -65
    private fun calculateProcessingDelay(timestamp: Long) = System.currentTimeMillis() - timestamp
    
    private fun clearBuffers() {
        dataBuffer.clear()
        realtimeBuffer.clear()
        statisticsBuffer.clear()
    }
    
    private fun resetCounters() {
        totalDataPoints.set(0)
        validDataPoints.set(0)
        outlierCount.set(0)
        processingErrorCount.set(0)
    }
    
    private fun setupAdaptiveParameters(config: CollectionConfiguration) {
        adaptiveSamplingRate = config.samplingRate
        currentComplexityLevel = config.complexityLevel
    }
    
    private suspend fun processRawDataPoint(rawData: RawDataPoint): ProcessedDataResult {
        // 구현 필요
        return ProcessedDataResult(rawData.id, true, "처리 완료", System.currentTimeMillis())
    }
    
    private fun updateBatchStatistics(results: List<ProcessedDataResult>) {
        // 구현 필요
    }
    
    private fun optimizeMemory() {
        // 메모리 최적화 로직
        if (dataBuffer.size > MAX_BUFFER_SIZE / 2) {
            dataBuffer.removeAll { it.timestamp < System.currentTimeMillis() - 3600000 } // 1시간 이전 데이터 제거
        }
    }
    
    private fun adjustPerformanceSettings() {
        // 성능 조정 로직
        adaptiveSamplingRate *= 0.8 // 샘플링 비율 감소
        currentComplexityLevel = ComplexityLevel.LOW
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        engineScope.cancel()
        clearBuffers()
        resetCounters()
    }
    
    // === 데이터 클래스들 ===
    
    data class TestDataPoint(
        val timestamp: Long,
        val ledPairId: Int,
        val color: String,
        val responseTime: Long,
        val position: Triple<Double, Double, Double>,
        val isCorrect: Boolean,
        val confidence: Float,
        val sequenceId: String,
        val metadata: Map<String, Any>
    )
    
    data class OutlierAnalysis(
        val isOutlier: Boolean,
        val confidence: Double,
        val reason: String
    )
    
    data class RealtimeMetrics(
        val averageResponseTime: Double,
        val accuracy: Double,
        val throughput: Double,
        val bufferSize: Int,
        val outlierRate: Double,
        val dataQualityScore: Double
    )
    
    data class DataQualityAssessment(
        val completeness: Double,
        val consistency: Double,
        val accuracy: Double,
        val timeliness: Double,
        var overallScore: Double
    )
    
    data class CollectionConfiguration(
        val samplingRate: Double = 1.0,
        val complexityLevel: ComplexityLevel = ComplexityLevel.NORMAL,
        val enableOutlierDetection: Boolean = true,
        val maxBufferSize: Int = MAX_BUFFER_SIZE
    )
    
    data class TestStatistics(
        val timestamp: Long,
        val meanResponseTime: Double,
        val stdDevResponseTime: Double,
        val accuracy: Double,
        val dataPoints: Int
    )
    
    data class RawDataPoint(
        val id: String,
        val timestamp: Long,
        val data: Map<String, Any>
    )
    
    data class ProcessedDataResult(
        val id: String,
        val success: Boolean,
        val message: String,
        val processedAt: Long
    )
    
    enum class DataCollectionState {
        READY, COLLECTING, PAUSED, STOPPED, ERROR
    }
    
    enum class ComplexityLevel {
        LOW, NORMAL, HIGH
    }
}