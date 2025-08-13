/*
 * 실시간 그래프 화면 Fragment
 * 
 * 기능:
 * - LED별 반응 시간 실시간 그래프
 * - 위치별 정확도 히트맵
 * - 색상별 성능 비교 차트
 * - 시간대별 성능 추이
 * - 통계 데이터 실시간 업데이트
 * 
 * 작성일: 2025-08-10
 * Phase 2 Week 5-6
 */

package com.ledvision.tester.ui.realtime

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.ledvision.tester.R
import com.ledvision.tester.databinding.FragmentRealtimeGraphBinding
import com.ledvision.tester.viewmodel.TestViewModel
import com.ledvision.tester.model.TestDataPoint
import com.ledvision.tester.model.LEDPosition
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

class RealtimeGraphFragment : Fragment(), OnChartValueSelectedListener {
    
    private var _binding: FragmentRealtimeGraphBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TestViewModel by viewModels()
    
    // 그래프 데이터 버퍼
    private val responseTimeBuffer = mutableListOf<Entry>()
    private val accuracyByPositionBuffer = mutableMapOf<LEDPosition, Float>()
    private val colorComparisonBuffer = mutableMapOf<String, Float>()
    
    // 그래프 설정
    private val maxDataPoints = 100
    private val updateIntervalMs = 500L
    private var isGraphUpdateEnabled = true
    
    // 색상 정의
    private val colorRed = Color.parseColor("#F44336")
    private val colorGreen = Color.parseColor("#4CAF50") 
    private val colorBlue = Color.parseColor("#2196F3")
    private val colorOrange = Color.parseColor("#FF9800")
    private val colorPurple = Color.parseColor("#9C27B0")
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRealtimeGraphBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupCharts()
        setupObservers()
        startRealtimeUpdates()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        isGraphUpdateEnabled = false
        _binding = null
    }
    
    private fun setupCharts() {
        setupResponseTimeChart()
        setupAccuracyHeatmap()
        setupColorComparisonChart()
        setupTrendChart()
    }
    
    /**
     * 반응 시간 실시간 그래프 설정
     */
    private fun setupResponseTimeChart() {
        binding.chartResponseTime.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setOnChartValueSelectedListener(this@RealtimeGraphFragment)
            
            // X축 설정
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                labelCount = 6
                textColor = Color.GRAY
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        return timeFormat.format(Date(System.currentTimeMillis() - (maxDataPoints - value.toInt()) * 1000))
                    }
                }
            }
            
            // Y축 설정 (좌측)
            axisLeft.apply {
                setDrawGridLines(true)
                textColor = Color.GRAY
                axisMinimum = 0f
                axisMaximum = 2000f // 2초
                labelCount = 5
            }
            
            // Y축 설정 (우측)
            axisRight.isEnabled = false
            
            // 범례 설정
            legend.apply {
                isEnabled = true
                textColor = Color.GRAY
                textSize = 12f
            }
        }
    }
    
    /**
     * 위치별 정확도 히트맵 (산점도로 구현)
     */
    private fun setupAccuracyHeatmap() {
        binding.chartAccuracyHeatmap.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setOnChartValueSelectedListener(this@RealtimeGraphFragment)
            
            // X축 설정 (각도)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                axisMinimum = 0f
                axisMaximum = 360f
                labelCount = 9 // 0, 45, 90, ..., 360
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}°"
                }
            }
            
            // Y축 설정 (거리)
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 3f // 3개 동심원
                labelCount = 4
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return when (value.toInt()) {
                            1 -> "내경"
                            2 -> "중간"  
                            3 -> "외곽"
                            else -> ""
                        }
                    }
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    /**
     * 색상별 성능 비교 막대 그래프
     */
    private fun setupColorComparisonChart() {
        binding.chartColorComparison.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setOnChartValueSelectedListener(this@RealtimeGraphFragment)
            
            // X축 설정
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(arrayOf("적색", "녹색", "전체"))
            }
            
            // Y축 설정
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                labelCount = 6
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    /**
     * 시간대별 성능 추이 그래프
     */
    private fun setupTrendChart() {
        binding.chartTrend.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setOnChartValueSelectedListener(this@RealtimeGraphFragment)
            
            // X축 설정 (시간)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                labelCount = 6
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        return timeFormat.format(Date(value.toLong() * 1000))
                    }
                }
            }
            
            // Y축 설정
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 100f
                valueFormatter = object : IndexAxisValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
                }
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    /**
     * ViewModel 옵저버 설정
     */
    private fun setupObservers() {
        viewModel.currentTestData.observe(viewLifecycleOwner) { dataPoint ->
            dataPoint?.let { updateGraphs(it) }
        }
        
        viewModel.testStatistics.observe(viewLifecycleOwner) { stats ->
            updateStatisticsDisplay(stats)
        }
        
        viewModel.isSequenceRunning.observe(viewLifecycleOwner) { isRunning ->
            updateGraphUpdateState(isRunning)
        }
    }
    
    /**
     * 실시간 업데이트 시작
     */
    private fun startRealtimeUpdates() {
        lifecycleScope.launch {
            while (isGraphUpdateEnabled) {
                if (viewModel.isSequenceRunning.value == true) {
                    updateAllGraphs()
                }
                delay(updateIntervalMs)
            }
        }
    }
    
    /**
     * 모든 그래프 업데이트
     */
    private fun updateAllGraphs() {
        updateResponseTimeData()
        updateAccuracyHeatmapData()
        updateColorComparisonData()
        updateTrendData()
    }
    
    /**
     * 그래프 데이터 업데이트 (새로운 데이터 포인트)
     */
    private fun updateGraphs(dataPoint: TestDataPoint) {
        // 반응 시간 버퍼 업데이트
        val timestamp = System.currentTimeMillis()
        val responseTime = if (dataPoint.wasCorrect) {
            (dataPoint.responseTimestamp - dataPoint.stimulusTimestamp).toFloat()
        } else {
            0f // 오답시 0으로 표시
        }
        
        responseTimeBuffer.add(Entry(responseTimeBuffer.size.toFloat(), responseTime))
        if (responseTimeBuffer.size > maxDataPoints) {
            responseTimeBuffer.removeAt(0)
            // 인덱스 재조정
            responseTimeBuffer.forEachIndexed { index, entry ->
                entry.x = index.toFloat()
            }
        }
        
        // 위치별 정확도 업데이트
        val position = LEDPosition.fromPairIndex(dataPoint.ledPairIndex)
        val currentAccuracy = accuracyByPositionBuffer.getOrDefault(position, 0f)
        val newAccuracy = if (dataPoint.wasCorrect) 
            (currentAccuracy * 0.9f + 100f * 0.1f) // 이동 평균
        else 
            (currentAccuracy * 0.9f + 0f * 0.1f)
        accuracyByPositionBuffer[position] = newAccuracy
        
        // 색상별 성능 업데이트
        val colorKey = if (dataPoint.isRedLED) "red" else "green"
        val currentColorPerf = colorComparisonBuffer.getOrDefault(colorKey, 0f)
        val newColorPerf = if (dataPoint.wasCorrect)
            (currentColorPerf * 0.9f + 100f * 0.1f)
        else
            (currentColorPerf * 0.9f + 0f * 0.1f)
        colorComparisonBuffer[colorKey] = newColorPerf
    }
    
    /**
     * 반응 시간 그래프 데이터 업데이트
     */
    private fun updateResponseTimeData() {
        if (responseTimeBuffer.isEmpty()) return
        
        val dataSet = LineDataSet(ArrayList(responseTimeBuffer), "반응 시간 (ms)").apply {
            color = colorBlue
            setCircleColor(colorBlue)
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 0f // 값 텍스트 숨김
            setDrawFilled(true)
            fillColor = colorBlue
            fillAlpha = 30
        }
        
        val lineData = LineData(dataSet)
        binding.chartResponseTime.apply {
            data = lineData
            notifyDataSetChanged()
            invalidate()
        }
    }
    
    /**
     * 정확도 히트맵 데이터 업데이트
     */
    private fun updateAccuracyHeatmapData() {
        if (accuracyByPositionBuffer.isEmpty()) return
        
        val entries = mutableListOf<Entry>()
        
        accuracyByPositionBuffer.forEach { (position, accuracy) ->
            val angle = position.angleInDegrees.toFloat()
            val radius = when {
                position.isInnerCircle -> 1f
                position.isMiddleCircle -> 2f
                else -> 3f
            }
            
            entries.add(Entry(angle, radius).apply {
                data = accuracy // 정확도를 데이터로 저장
            })
        }
        
        val dataSet = ScatterDataSet(entries, "위치별 정확도").apply {
            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
            scatterShapeSize = 15f
            
            // 정확도에 따른 색상 설정
            colors = entries.map { entry ->
                val accuracy = entry.data as? Float ?: 0f
                when {
                    accuracy >= 90f -> colorGreen
                    accuracy >= 70f -> colorOrange  
                    accuracy >= 50f -> colorRed
                    else -> Color.GRAY
                }
            }
        }
        
        val scatterData = ScatterData(dataSet)
        binding.chartAccuracyHeatmap.apply {
            data = scatterData
            notifyDataSetChanged()
            invalidate()
        }
    }
    
    /**
     * 색상별 성능 비교 데이터 업데이트
     */
    private fun updateColorComparisonData() {
        val entries = mutableListOf<BarEntry>().apply {
            add(BarEntry(0f, colorComparisonBuffer.getOrDefault("red", 0f)))
            add(BarEntry(1f, colorComparisonBuffer.getOrDefault("green", 0f)))
            
            val overall = (colorComparisonBuffer.values.sum() / colorComparisonBuffer.size.coerceAtLeast(1))
            add(BarEntry(2f, overall))
        }
        
        val dataSet = BarDataSet(entries, "정확도 (%)").apply {
            colors = listOf(colorRed, colorGreen, colorBlue)
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }
        
        val barData = BarData(dataSet)
        binding.chartColorComparison.apply {
            data = barData
            notifyDataSetChanged()
            invalidate()
        }
    }
    
    /**
     * 시간대별 추이 데이터 업데이트
     */
    private fun updateTrendData() {
        // 최근 10분간의 1분 단위 정확도 추이
        val now = System.currentTimeMillis() / 1000
        val entries = mutableListOf<Entry>()
        
        for (i in 9 downTo 0) {
            val timePoint = now - i * 60 // 1분씩 과거로
            val accuracy = calculateAccuracyAtTime(timePoint * 1000) // ms로 변환
            entries.add(Entry(timePoint.toFloat(), accuracy))
        }
        
        val dataSet = LineDataSet(entries, "시간대별 정확도").apply {
            color = colorPurple
            lineWidth = 3f
            setDrawCircles(true)
            setCircleColor(colorPurple)
            circleRadius = 4f
            valueTextSize = 0f
        }
        
        val lineData = LineData(dataSet)
        binding.chartTrend.apply {
            data = lineData
            notifyDataSetChanged()
            invalidate()
        }
    }
    
    /**
     * 특정 시점의 정확도 계산
     */
    private fun calculateAccuracyAtTime(timestamp: Long): Float {
        val windowMs = 60 * 1000L // 1분 윈도우
        val testData = viewModel.getTestDataInTimeWindow(timestamp - windowMs, timestamp)
        
        if (testData.isEmpty()) return 0f
        
        val correctCount = testData.count { it.wasCorrect }
        return (correctCount.toFloat() / testData.size * 100f)
    }
    
    /**
     * 통계 표시 업데이트
     */
    private fun updateStatisticsDisplay(stats: Any) {
        // 통계 텍스트 뷰들 업데이트
        binding.apply {
            // 이 부분은 실제 통계 모델에 따라 구현
            // 예: textViewTotalTests.text = "총 테스트: ${stats.totalTests}"
        }
    }
    
    /**
     * 그래프 업데이트 상태 변경
     */
    private fun updateGraphUpdateState(isRunning: Boolean) {
        isGraphUpdateEnabled = isRunning
        
        if (!isRunning) {
            // 테스트 중지시 최종 그래프 업데이트
            updateAllGraphs()
        }
    }
    
    /**
     * 그래프 값 선택 이벤트 처리
     */
    override fun onValueSelected(e: Entry?, h: Highlight?) {
        e?.let { entry ->
            when (h?.dataSetIndex) {
                // 반응 시간 그래프 선택
                0 -> {
                    val timeIndex = entry.x.toInt()
                    val responseTime = entry.y
                    showDataPointDetail("반응 시간", "${responseTime.toInt()}ms", timeIndex)
                }
                // 기타 그래프들...
            }
        }
    }
    
    override fun onNothingSelected() {
        // 선택 해제시 처리
    }
    
    /**
     * 데이터 포인트 상세 정보 표시
     */
    private fun showDataPointDetail(type: String, value: String, index: Int) {
        val message = "$type: $value (인덱스: $index)"
        Timber.d("Chart detail: $message")
        
        // 상세 정보를 표시할 UI 컴포넌트가 있다면 여기서 업데이트
        // 예: 토스트, 스낵바, 또는 별도의 상세 정보 패널
    }
    
    /**
     * 그래프 데이터 초기화
     */
    fun clearGraphData() {
        responseTimeBuffer.clear()
        accuracyByPositionBuffer.clear()
        colorComparisonBuffer.clear()
        
        updateAllGraphs()
    }
    
    /**
     * 그래프 스크린샷 저장
     */
    fun saveGraphScreenshots(): Boolean {
        return try {
            // 각 그래프의 비트맵 저장 로직
            // 실제 구현은 파일 시스템 접근 권한 확인 필요
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save graph screenshots")
            false
        }
    }
}

/**
 * LED 위치 정보 확장 클래스
 */
data class LEDPosition(
    val pairIndex: Int,
    val angleInDegrees: Int,
    val radiusLevel: Int // 1=내경, 2=중간, 3=외곽
) {
    val isInnerCircle: Boolean get() = radiusLevel == 1
    val isMiddleCircle: Boolean get() = radiusLevel == 2
    val isOuterCircle: Boolean get() = radiusLevel == 3
    
    companion object {
        fun fromPairIndex(pairIndex: Int): LEDPosition {
            // 36쌍 LED 배치에서 위치 계산
            val radiusLevel = when {
                pairIndex < 12 -> 1 // 내경 (0-11)
                pairIndex < 24 -> 2 // 중간 (12-23)
                else -> 3           // 외곽 (24-35)
            }
            
            val indexInCircle = when (radiusLevel) {
                1 -> pairIndex
                2 -> pairIndex - 12
                else -> pairIndex - 24
            }
            
            val angleStep = 360 / 12 // 각 원마다 12개씩, 30도 간격
            val angleInDegrees = indexInCircle * angleStep
            
            return LEDPosition(pairIndex, angleInDegrees, radiusLevel)
        }
    }
}