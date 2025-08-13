package com.ledvision.tester.model

import java.util.*

/**
 * 검사 결과 데이터 클래스
 */
data class TestResult(
    val id: Long,
    val patientId: Long,
    val startTime: Date,
    val endTime: Date?,
    val sequenceType: Int, // 0: random, 1: sequential
    val sequenceInterval: Int, // ms
    val totalPairs: Int,
    val completedPairs: Int,
    val correctResponses: Int,
    val averageResponseTime: Long, // ms
    val status: String, // "IN_PROGRESS", "COMPLETED", "CANCELLED"
    val dataPoints: List<TestDataPoint>
) {
    
    /**
     * 검사 지속 시간 계산 (초)
     */
    fun getTestDurationSeconds(): Long {
        return if (endTime != null) {
            (endTime.time - startTime.time) / 1000
        } else {
            (System.currentTimeMillis() - startTime.time) / 1000
        }
    }
    
    /**
     * 정확도 계산 (%)
     */
    fun getAccuracyPercentage(): Double {
        return if (completedPairs > 0) {
            (correctResponses.toDouble() / completedPairs.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * 완료율 계산 (%)
     */
    fun getCompletionPercentage(): Double {
        return if (totalPairs > 0) {
            (completedPairs.toDouble() / totalPairs.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * 검사 결과 요약
     */
    fun getSummary(): String {
        val duration = getTestDurationSeconds()
        val accuracy = getAccuracyPercentage()
        val completion = getCompletionPercentage()
        
        return buildString {
            append("검사 시간: ${duration}초\n")
            append("완료율: ${"%.1f".format(completion)}% (${completedPairs}/${totalPairs})\n")
            append("정확도: ${"%.1f".format(accuracy)}% (${correctResponses}/${completedPairs})\n")
            append("평균 응답시간: ${averageResponseTime}ms")
        }
    }
    
    /**
     * 색상별 성능 분석
     */
    fun getColorPerformance(): Map<String, ColorPerformance> {
        val redPoints = dataPoints.filter { it.color == "red" }
        val greenPoints = dataPoints.filter { it.color == "green" }
        
        return mapOf(
            "red" to ColorPerformance(redPoints),
            "green" to ColorPerformance(greenPoints)
        )
    }
    
    /**
     * 위치별 성능 분석 (내경/중간/외곽)
     */
    fun getPositionPerformance(): Map<String, PositionPerformance> {
        val innerPoints = dataPoints.filter { it.ledPairId < 12 } // 내경
        val middlePoints = dataPoints.filter { it.ledPairId in 12..23 } // 중간
        val outerPoints = dataPoints.filter { it.ledPairId >= 24 } // 외곽
        
        return mapOf(
            "inner" to PositionPerformance("내경", innerPoints),
            "middle" to PositionPerformance("중간", middlePoints),
            "outer" to PositionPerformance("외곽", outerPoints)
        )
    }
    
    /**
     * Excel 내보내기용 데이터 생성
     */
    fun toExcelData(): List<Map<String, Any>> {
        return dataPoints.mapIndexed { index, point ->
            mapOf(
                "순번" to (index + 1),
                "LED쌍" to (point.ledPairId + 1),
                "색상" to if (point.color == "red") "빨강" else "녹색",
                "자극시간" to point.stimulusTime,
                "응답시간" to "${point.responseTime}ms",
                "정답여부" to if (point.isCorrect) "O" else "X",
                "위치X" to point.positionX,
                "위치Y" to point.positionY
            )
        }
    }
    
    /**
     * 검사 데이터 포인트
     */
    data class TestDataPoint(
        val ledPairId: Int,
        val color: String, // "red" or "green"
        val stimulusTime: Date,
        val responseTime: Long, // ms
        val isCorrect: Boolean,
        val positionX: Float, // LED 위치 좌표
        val positionY: Float
    )
    
    /**
     * 색상별 성능 분석 결과
     */
    data class ColorPerformance(
        val dataPoints: List<TestDataPoint>
    ) {
        val totalCount = dataPoints.size
        val correctCount = dataPoints.count { it.isCorrect }
        val accuracy = if (totalCount > 0) (correctCount.toDouble() / totalCount) * 100.0 else 0.0
        val averageResponseTime = if (totalCount > 0) {
            dataPoints.map { it.responseTime }.average()
        } else 0.0
    }
    
    /**
     * 위치별 성능 분석 결과
     */
    data class PositionPerformance(
        val name: String,
        val dataPoints: List<TestDataPoint>
    ) {
        val totalCount = dataPoints.size
        val correctCount = dataPoints.count { it.isCorrect }
        val accuracy = if (totalCount > 0) (correctCount.toDouble() / totalCount) * 100.0 else 0.0
        val averageResponseTime = if (totalCount > 0) {
            dataPoints.map { it.responseTime }.average()
        } else 0.0
    }
    
    companion object {
        /**
         * 새 검사 결과 생성
         */
        fun createNew(
            patientId: Long,
            sequenceType: Int,
            sequenceInterval: Int,
            totalPairs: Int = 36
        ): TestResult {
            return TestResult(
                id = 0,
                patientId = patientId,
                startTime = Date(),
                endTime = null,
                sequenceType = sequenceType,
                sequenceInterval = sequenceInterval,
                totalPairs = totalPairs,
                completedPairs = 0,
                correctResponses = 0,
                averageResponseTime = 0,
                status = "IN_PROGRESS",
                dataPoints = emptyList()
            )
        }
        
        /**
         * 검사 완료 처리
         */
        fun complete(
            testResult: TestResult,
            dataPoints: List<TestDataPoint>
        ): TestResult {
            val correctCount = dataPoints.count { it.isCorrect }
            val avgResponseTime = if (dataPoints.isNotEmpty()) {
                dataPoints.map { it.responseTime }.average().toLong()
            } else 0L
            
            return testResult.copy(
                endTime = Date(),
                completedPairs = dataPoints.size,
                correctResponses = correctCount,
                averageResponseTime = avgResponseTime,
                status = "COMPLETED",
                dataPoints = dataPoints
            )
        }
    }
}