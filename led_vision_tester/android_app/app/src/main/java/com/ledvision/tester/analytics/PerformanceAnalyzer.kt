package com.ledvision.tester.analytics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * 성능 분석기
 * 
 * 기능:
 * - 실시간 성능 모니터링
 * - 시스템 리소스 추적
 * - 성능 병목 지점 탐지
 * - 최적화 권장사항 제공
 * - 성능 트렌드 분석
 * 
 * @author LED Vision Tester Team
 * @version 1.0
 */
class PerformanceAnalyzer {
    
    companion object {
        private const val TAG = "PerformanceAnalyzer"
        private const val ANALYSIS_INTERVAL = 1000L // 1초
        private const val PERFORMANCE_BUFFER_SIZE = 300 // 5분 데이터
        private const val CPU_THRESHOLD_HIGH = 80.0
        private const val MEMORY_THRESHOLD_HIGH = 85.0
        private const val LATENCY_THRESHOLD_HIGH = 100.0 // ms
    }
    
    // === 성능 메트릭 저장소 ===
    private val performanceHistory = ArrayDeque<PerformanceSnapshot>(PERFORMANCE_BUFFER_SIZE)
    private val bottleneckHistory = mutableListOf<BottleneckEvent>()
    
    // === LiveData for UI ===
    private val _currentPerformance = MutableLiveData<PerformanceMetrics>()
    val currentPerformance: LiveData<PerformanceMetrics> = _currentPerformance
    
    private val _performanceTrends = MutableLiveData<PerformanceTrends>()
    val performanceTrends: LiveData<PerformanceTrends> = _performanceTrends
    
    private val _optimizationSuggestions = MutableLiveData<List<OptimizationSuggestion>>()
    val optimizationSuggestions: LiveData<List<OptimizationSuggestion>> = _optimizationSuggestions
    
    private val _systemHealth = MutableLiveData<SystemHealthStatus>()
    val systemHealth: LiveData<SystemHealthStatus> = _systemHealth
    
    // === 성능 카운터 ===
    private val totalOperations = AtomicLong(0)
    private val successfulOperations = AtomicLong(0)
    private val totalLatency = AtomicLong(0)
    private val peakMemoryUsage = AtomicLong(0)
    
    // === 분석 스코프 ===
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // === 시작 시간 ===
    private val startTime = System.currentTimeMillis()
    
    init {
        startPerformanceMonitoring()
    }
    
    /**
     * 성능 모니터링 시작
     */
    private fun startPerformanceMonitoring() {
        analysisScope.launch {
            while (true) {
                try {
                    val snapshot = capturePerformanceSnapshot()
                    processPerformanceSnapshot(snapshot)
                    
                    delay(ANALYSIS_INTERVAL)
                } catch (e: Exception) {
                    Timber.e(e, "성능 모니터링 중 오류")
                    delay(ANALYSIS_INTERVAL * 2) // 오류 시 잠시 대기
                }
            }
        }
    }
    
    /**
     * 성능 스냅샷 캡처
     */
    private fun capturePerformanceSnapshot(): PerformanceSnapshot {
        val runtime = Runtime.getRuntime()
        val currentTime = System.currentTimeMillis()
        
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toDouble() / maxMemory * 100)
        
        // CPU 사용률 추정 (실제 구현에서는 더 정확한 방법 사용)
        val cpuUsagePercent = estimateCpuUsage()
        
        // 지연시간 계산
        val averageLatency = if (totalOperations.get() > 0) {
            totalLatency.get().toDouble() / totalOperations.get()
        } else 0.0
        
        // 처리량 계산
        val throughput = calculateThroughput()
        
        return PerformanceSnapshot(
            timestamp = currentTime,
            cpuUsagePercent = cpuUsagePercent,
            memoryUsagePercent = memoryUsagePercent,
            memoryUsedBytes = usedMemory,
            memoryMaxBytes = maxMemory,
            averageLatency = averageLatency,
            throughput = throughput,
            activeOperations = getCurrentActiveOperations(),
            errorRate = calculateErrorRate()
        )
    }
    
    /**
     * 성능 스냅샷 처리
     */
    private suspend fun processPerformanceSnapshot(snapshot: PerformanceSnapshot) {
        // 1. 히스토리에 추가
        addToHistory(snapshot)
        
        // 2. 현재 성능 메트릭 업데이트
        updateCurrentPerformance(snapshot)
        
        // 3. 트렌드 분석
        analyzeTrends()
        
        // 4. 병목지점 탐지
        detectBottlenecks(snapshot)
        
        // 5. 시스템 상태 평가
        assessSystemHealth(snapshot)
        
        // 6. 최적화 제안 생성
        generateOptimizationSuggestions()
    }
    
    /**
     * 히스토리에 추가 (메모리 효율적)
     */
    private fun addToHistory(snapshot: PerformanceSnapshot) {
        if (performanceHistory.size >= PERFORMANCE_BUFFER_SIZE) {
            performanceHistory.removeFirst()
        }
        performanceHistory.addLast(snapshot)
        
        // 피크 메모리 사용량 업데이트
        if (snapshot.memoryUsedBytes > peakMemoryUsage.get()) {
            peakMemoryUsage.set(snapshot.memoryUsedBytes)
        }
    }
    
    /**
     * 현재 성능 메트릭 업데이트
     */
    private fun updateCurrentPerformance(snapshot: PerformanceSnapshot) {
        val uptime = System.currentTimeMillis() - startTime
        val successRate = if (totalOperations.get() > 0) {
            successfulOperations.get().toDouble() / totalOperations.get() * 100
        } else 100.0
        
        val metrics = PerformanceMetrics(
            timestamp = snapshot.timestamp,
            cpuUsage = snapshot.cpuUsagePercent,
            memoryUsage = snapshot.memoryUsagePercent,
            memoryUsedMB = snapshot.memoryUsedBytes / (1024 * 1024),
            memoryMaxMB = snapshot.memoryMaxBytes / (1024 * 1024),
            averageLatency = snapshot.averageLatency,
            throughput = snapshot.throughput,
            successRate = successRate,
            uptime = uptime,
            activeOperations = snapshot.activeOperations,
            peakMemoryMB = peakMemoryUsage.get() / (1024 * 1024)
        )
        
        _currentPerformance.postValue(metrics)
    }
    
    /**
     * 트렌드 분석
     */
    private fun analyzeTrends() {
        if (performanceHistory.size < 10) return
        
        val recentSnapshots = performanceHistory.takeLast(60) // 최근 1분
        val olderSnapshots = performanceHistory.takeLast(120).take(60) // 이전 1분
        
        val cpuTrend = calculateTrend(
            recentSnapshots.map { it.cpuUsagePercent },
            olderSnapshots.map { it.cpuUsagePercent }
        )
        
        val memoryTrend = calculateTrend(
            recentSnapshots.map { it.memoryUsagePercent },
            olderSnapshots.map { it.memoryUsagePercent }
        )
        
        val latencyTrend = calculateTrend(
            recentSnapshots.map { it.averageLatency },
            olderSnapshots.map { it.averageLatency }
        )
        
        val throughputTrend = calculateTrend(
            recentSnapshots.map { it.throughput },
            olderSnapshots.map { it.throughput }
        )
        
        val trends = PerformanceTrends(
            cpuTrend = cpuTrend,
            memoryTrend = memoryTrend,
            latencyTrend = latencyTrend,
            throughputTrend = throughputTrend,
            overallHealthTrend = calculateOverallTrend(cpuTrend, memoryTrend, latencyTrend),
            trendConfidence = calculateTrendConfidence(recentSnapshots.size)
        )
        
        _performanceTrends.postValue(trends)
    }
    
    /**
     * 병목지점 탐지
     */
    private fun detectBottlenecks(snapshot: PerformanceSnapshot) {
        val bottlenecks = mutableListOf<BottleneckEvent>()
        
        // CPU 병목
        if (snapshot.cpuUsagePercent > CPU_THRESHOLD_HIGH) {
            bottlenecks.add(BottleneckEvent(
                timestamp = snapshot.timestamp,
                type = BottleneckType.CPU,
                severity = calculateSeverity(snapshot.cpuUsagePercent, CPU_THRESHOLD_HIGH),
                description = "CPU 사용률이 ${String.format("%.1f", snapshot.cpuUsagePercent)}%로 높습니다",
                impact = "응답시간 증가 및 전체 성능 저하",
                recommendation = "백그라운드 프로세스 확인 또는 처리 로직 최적화"
            ))
        }
        
        // 메모리 병목
        if (snapshot.memoryUsagePercent > MEMORY_THRESHOLD_HIGH) {
            bottlenecks.add(BottleneckEvent(
                timestamp = snapshot.timestamp,
                type = BottleneckType.MEMORY,
                severity = calculateSeverity(snapshot.memoryUsagePercent, MEMORY_THRESHOLD_HIGH),
                description = "메모리 사용률이 ${String.format("%.1f", snapshot.memoryUsagePercent)}%로 높습니다",
                impact = "OutOfMemoryError 위험 및 GC 압박",
                recommendation = "메모리 누수 확인 또는 버퍼 크기 조정"
            ))
        }
        
        // 지연시간 병목
        if (snapshot.averageLatency > LATENCY_THRESHOLD_HIGH) {
            bottlenecks.add(BottleneckEvent(
                timestamp = snapshot.timestamp,
                type = BottleneckType.LATENCY,
                severity = calculateSeverity(snapshot.averageLatency, LATENCY_THRESHOLD_HIGH),
                description = "평균 지연시간이 ${String.format("%.1f", snapshot.averageLatency)}ms로 높습니다",
                impact = "사용자 경험 저하 및 실시간성 감소",
                recommendation = "네트워크 연결 확인 또는 알고리즘 최적화"
            ))
        }
        
        // 처리량 병목 (처리량이 급격히 감소한 경우)
        val recentThroughput = performanceHistory.takeLast(10).map { it.throughput }
        if (recentThroughput.size >= 5) {
            val avgThroughput = recentThroughput.average()
            val expectedThroughput = calculateExpectedThroughput()
            
            if (avgThroughput < expectedThroughput * 0.5) {
                bottlenecks.add(BottleneckEvent(
                    timestamp = snapshot.timestamp,
                    type = BottleneckType.THROUGHPUT,
                    severity = BottleneckSeverity.HIGH,
                    description = "처리량이 예상치의 50% 이하로 감소했습니다",
                    impact = "전체 시스템 처리 능력 저하",
                    recommendation = "동시 처리 최적화 또는 리소스 할당 재검토"
                ))
            }
        }
        
        // 병목지점이 발견된 경우 히스토리에 기록
        if (bottlenecks.isNotEmpty()) {
            bottleneckHistory.addAll(bottlenecks)
            
            // 최근 100개만 유지
            if (bottleneckHistory.size > 100) {
                bottleneckHistory.removeAll(bottleneckHistory.take(bottleneckHistory.size - 100))
            }
            
            Timber.w("성능 병목 탐지: ${bottlenecks.size}개")
        }
    }
    
    /**
     * 시스템 상태 평가
     */
    private fun assessSystemHealth(snapshot: PerformanceSnapshot) {
        val healthScore = calculateHealthScore(snapshot)
        val status = when {
            healthScore >= 90 -> SystemHealthLevel.EXCELLENT
            healthScore >= 75 -> SystemHealthLevel.GOOD
            healthScore >= 50 -> SystemHealthLevel.FAIR
            healthScore >= 25 -> SystemHealthLevel.POOR
            else -> SystemHealthLevel.CRITICAL
        }
        
        val recentBottlenecks = bottleneckHistory
            .filter { it.timestamp > System.currentTimeMillis() - 300000 } // 최근 5분
        
        val criticalIssues = recentBottlenecks
            .filter { it.severity == BottleneckSeverity.CRITICAL }
            .map { it.description }
        
        val warnings = recentBottlenecks
            .filter { it.severity == BottleneckSeverity.HIGH }
            .map { it.description }
        
        val systemHealth = SystemHealthStatus(
            level = status,
            score = healthScore,
            criticalIssues = criticalIssues,
            warnings = warnings,
            uptime = System.currentTimeMillis() - startTime,
            lastUpdated = System.currentTimeMillis()
        )
        
        _systemHealth.postValue(systemHealth)
    }
    
    /**
     * 최적화 제안 생성
     */
    private fun generateOptimizationSuggestions() {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        
        if (performanceHistory.size < 30) return
        
        val recentSnapshots = performanceHistory.takeLast(30)
        
        // 메모리 최적화 제안
        val avgMemoryUsage = recentSnapshots.map { it.memoryUsagePercent }.average()
        if (avgMemoryUsage > 70) {
            suggestions.add(OptimizationSuggestion(
                type = OptimizationType.MEMORY,
                priority = if (avgMemoryUsage > 85) OptimizationPriority.HIGH else OptimizationPriority.MEDIUM,
                title = "메모리 사용량 최적화",
                description = "메모리 사용률이 ${String.format("%.1f", avgMemoryUsage)}%로 높습니다",
                actions = listOf(
                    "데이터 버퍼 크기 조정",
                    "불필요한 객체 참조 해제",
                    "이미지 캐시 정리",
                    "메모리 누수 검사"
                ),
                estimatedImpact = "메모리 사용량 20-30% 감소 예상"
            ))
        }
        
        // CPU 최적화 제안
        val avgCpuUsage = recentSnapshots.map { it.cpuUsagePercent }.average()
        if (avgCpuUsage > 60) {
            suggestions.add(OptimizationSuggestion(
                type = OptimizationType.CPU,
                priority = if (avgCpuUsage > 80) OptimizationPriority.HIGH else OptimizationPriority.MEDIUM,
                title = "CPU 성능 최적화",
                description = "CPU 사용률이 ${String.format("%.1f", avgCpuUsage)}%로 높습니다",
                actions = listOf(
                    "알고리즘 복잡도 개선",
                    "불필요한 연산 제거",
                    "비동기 처리 적용",
                    "캐싱 전략 도입"
                ),
                estimatedImpact = "CPU 사용량 15-25% 감소 예상"
            ))
        }
        
        // 지연시간 최적화 제안
        val avgLatency = recentSnapshots.map { it.averageLatency }.average()
        if (avgLatency > 50) {
            suggestions.add(OptimizationSuggestion(
                type = OptimizationType.LATENCY,
                priority = if (avgLatency > 100) OptimizationPriority.HIGH else OptimizationPriority.MEDIUM,
                title = "응답시간 최적화",
                description = "평균 응답시간이 ${String.format("%.1f", avgLatency)}ms입니다",
                actions = listOf(
                    "네트워크 연결 안정성 개선",
                    "데이터 처리 파이프라인 최적화",
                    "I/O 작업 비동기화",
                    "로컬 캐싱 강화"
                ),
                estimatedImpact = "응답시간 30-50% 개선 예상"
            ))
        }
        
        // 처리량 최적화 제안
        val avgThroughput = recentSnapshots.map { it.throughput }.average()
        val expectedThroughput = calculateExpectedThroughput()
        
        if (avgThroughput < expectedThroughput * 0.8) {
            suggestions.add(OptimizationSuggestion(
                type = OptimizationType.THROUGHPUT,
                priority = OptimizationPriority.MEDIUM,
                title = "처리량 향상",
                description = "현재 처리량이 예상치의 ${String.format("%.1f", avgThroughput / expectedThroughput * 100)}%입니다",
                actions = listOf(
                    "병렬 처리 확대",
                    "배치 처리 크기 조정",
                    "리소스 할당 최적화",
                    "큐 관리 전략 개선"
                ),
                estimatedImpact = "처리량 25-40% 향상 예상"
            ))
        }
        
        _optimizationSuggestions.postValue(suggestions)
    }
    
    // === 유틸리티 메서드들 ===
    
    /**
     * 작업 완료 기록
     */
    fun recordOperation(latencyMs: Long, success: Boolean) {
        totalOperations.incrementAndGet()
        totalLatency.addAndGet(latencyMs)
        
        if (success) {
            successfulOperations.incrementAndGet()
        }
    }
    
    /**
     * 트렌드 계산
     */
    private fun calculateTrend(recent: List<Double>, older: List<Double>): TrendDirection {
        if (recent.isEmpty() || older.isEmpty()) return TrendDirection.STABLE
        
        val recentAvg = recent.average()
        val olderAvg = older.average()
        
        val changePercent = (recentAvg - olderAvg) / olderAvg * 100
        
        return when {
            changePercent > 10 -> TrendDirection.INCREASING
            changePercent < -10 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculateOverallTrend(cpu: TrendDirection, memory: TrendDirection, latency: TrendDirection): TrendDirection {
        val trends = listOf(cpu, memory, latency)
        return when {
            trends.count { it == TrendDirection.INCREASING } >= 2 -> TrendDirection.INCREASING
            trends.count { it == TrendDirection.DECREASING } >= 2 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculateTrendConfidence(sampleSize: Int): Double {
        return minOf(sampleSize / 30.0, 1.0)
    }
    
    private fun calculateSeverity(value: Double, threshold: Double): BottleneckSeverity {
        val ratio = value / threshold
        return when {
            ratio >= 1.5 -> BottleneckSeverity.CRITICAL
            ratio >= 1.2 -> BottleneckSeverity.HIGH
            ratio >= 1.0 -> BottleneckSeverity.MEDIUM
            else -> BottleneckSeverity.LOW
        }
    }
    
    private fun calculateHealthScore(snapshot: PerformanceSnapshot): Double {
        val cpuScore = maxOf(0.0, 100 - snapshot.cpuUsagePercent)
        val memoryScore = maxOf(0.0, 100 - snapshot.memoryUsagePercent)
        val latencyScore = maxOf(0.0, 100 - (snapshot.averageLatency / 2))
        val errorScore = maxOf(0.0, 100 - (calculateErrorRate() * 100))
        
        return (cpuScore * 0.25 + memoryScore * 0.25 + latencyScore * 0.25 + errorScore * 0.25)
    }
    
    // 각종 추정 및 계산 메서드들
    private fun estimateCpuUsage(): Double = Math.random() * 50 + 20 // 실제 구현 필요
    private fun calculateThroughput(): Double = Math.random() * 100 + 50 // 실제 구현 필요
    private fun getCurrentActiveOperations(): Int = (Math.random() * 10).toInt() // 실제 구현 필요
    private fun calculateErrorRate(): Double = Math.random() * 0.05 // 실제 구현 필요
    private fun calculateExpectedThroughput(): Double = 100.0 // 실제 구현 필요
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        analysisScope.cancel()
        performanceHistory.clear()
        bottleneckHistory.clear()
    }
    
    // === 데이터 클래스들 ===
    
    data class PerformanceSnapshot(
        val timestamp: Long,
        val cpuUsagePercent: Double,
        val memoryUsagePercent: Double,
        val memoryUsedBytes: Long,
        val memoryMaxBytes: Long,
        val averageLatency: Double,
        val throughput: Double,
        val activeOperations: Int,
        val errorRate: Double
    )
    
    data class PerformanceMetrics(
        val timestamp: Long,
        val cpuUsage: Double,
        val memoryUsage: Double,
        val memoryUsedMB: Long,
        val memoryMaxMB: Long,
        val averageLatency: Double,
        val throughput: Double,
        val successRate: Double,
        val uptime: Long,
        val activeOperations: Int,
        val peakMemoryMB: Long
    )
    
    data class PerformanceTrends(
        val cpuTrend: TrendDirection,
        val memoryTrend: TrendDirection,
        val latencyTrend: TrendDirection,
        val throughputTrend: TrendDirection,
        val overallHealthTrend: TrendDirection,
        val trendConfidence: Double
    )
    
    data class BottleneckEvent(
        val timestamp: Long,
        val type: BottleneckType,
        val severity: BottleneckSeverity,
        val description: String,
        val impact: String,
        val recommendation: String
    )
    
    data class OptimizationSuggestion(
        val type: OptimizationType,
        val priority: OptimizationPriority,
        val title: String,
        val description: String,
        val actions: List<String>,
        val estimatedImpact: String
    )
    
    data class SystemHealthStatus(
        val level: SystemHealthLevel,
        val score: Double,
        val criticalIssues: List<String>,
        val warnings: List<String>,
        val uptime: Long,
        val lastUpdated: Long
    )
    
    // === Enum 클래스들 ===
    
    enum class TrendDirection { INCREASING, DECREASING, STABLE }
    
    enum class BottleneckType { CPU, MEMORY, LATENCY, THROUGHPUT, NETWORK }
    
    enum class BottleneckSeverity { LOW, MEDIUM, HIGH, CRITICAL }
    
    enum class OptimizationType { CPU, MEMORY, LATENCY, THROUGHPUT, GENERAL }
    
    enum class OptimizationPriority { LOW, MEDIUM, HIGH, CRITICAL }
    
    enum class SystemHealthLevel { EXCELLENT, GOOD, FAIR, POOR, CRITICAL }
}