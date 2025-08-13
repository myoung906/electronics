package com.ledvision.tester.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ledvision.tester.model.PatientInfo
import com.ledvision.tester.model.TestResult
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Excel 파일 내보내기 유틸리티
 * 
 * 주요 기능:
 * - 환자 정보 및 검사 결과를 Excel로 내보내기
 * - 다양한 형식의 보고서 생성
 * - 파일 공유 기능
 */
class ExcelExporter(private val context: Context) {
    
    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.ledvision.tester.fileprovider"
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    }
    
    /**
     * 개별 환자 검사 결과 내보내기
     */
    fun exportPatientTestResult(
        patientInfo: PatientInfo,
        testResults: List<TestResult>
    ): ExportResult {
        return try {
            val workbook = XSSFWorkbook()
            
            // 스타일 생성
            val styles = createStyles(workbook)
            
            // 환자 정보 시트
            createPatientInfoSheet(workbook, patientInfo, styles)
            
            // 검사 결과별 시트 생성
            testResults.forEachIndexed { index, testResult ->
                createTestResultSheet(workbook, testResult, index + 1, styles)
            }
            
            // 요약 시트
            if (testResults.isNotEmpty()) {
                createSummarySheet(workbook, patientInfo, testResults, styles)
            }
            
            // 파일 저장
            val fileName = "검사결과_${patientInfo.name}_${patientInfo.patientId}_${fileNameFormatter.format(Date())}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            
            workbook.close()
            
            ExportResult.Success(file, fileName)
            
        } catch (e: Exception) {
            Timber.e(e, "Excel 내보내기 실패")
            ExportResult.Error("Excel 파일 생성 실패: ${e.message}")
        }
    }
    
    /**
     * 여러 환자 검사 결과 통합 내보내기
     */
    fun exportMultiplePatientResults(
        patientResults: List<Pair<PatientInfo, List<TestResult>>>
    ): ExportResult {
        return try {
            val workbook = XSSFWorkbook()
            val styles = createStyles(workbook)
            
            // 전체 요약 시트
            createOverallSummarySheet(workbook, patientResults, styles)
            
            // 각 환자별 시트
            patientResults.forEach { (patientInfo, testResults) ->
                val sheetName = "${patientInfo.name}_${patientInfo.patientId}".take(30)
                createPatientSummarySheet(workbook, patientInfo, testResults, sheetName, styles)
            }
            
            // 파일 저장
            val fileName = "통합검사결과_${fileNameFormatter.format(Date())}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            
            workbook.close()
            
            ExportResult.Success(file, fileName)
            
        } catch (e: Exception) {
            Timber.e(e, "통합 Excel 내보내기 실패")
            ExportResult.Error("통합 Excel 파일 생성 실패: ${e.message}")
        }
    }
    
    /**
     * 환자 정보 시트 생성
     */
    private fun createPatientInfoSheet(
        workbook: Workbook,
        patientInfo: PatientInfo,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("환자 정보")
        var rowNum = 0
        
        // 제목
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("환자 정보")
        titleCell.cellStyle = styles["title"]
        
        rowNum++ // 빈 줄
        
        // 환자 정보 테이블
        val patientData = listOf(
            "환자명" to patientInfo.name,
            "환자 ID" to patientInfo.patientId,
            "나이" to "${patientInfo.age}세",
            "성별" to if (patientInfo.gender == "M") "남성" else "여성",
            "생년월일" to (patientInfo.birthDate?.let { dateFormatter.format(it) } ?: "미입력"),
            "검사일" to (patientInfo.testDate?.let { dateFormatter.format(it) } ?: "미입력"),
            "좌안 시력" to patientInfo.leftEyeVision.ifEmpty { "미입력" },
            "우안 시력" to patientInfo.rightEyeVision.ifEmpty { "미입력" },
            "담당의" to patientInfo.doctorName.ifEmpty { "미입력" },
            "임상 소견" to patientInfo.clinicalNotes.ifEmpty { "없음" }
        )
        
        patientData.forEach { (label, value) ->
            val row = sheet.createRow(rowNum++)
            
            val labelCell = row.createCell(0)
            labelCell.setCellValue(label)
            labelCell.cellStyle = styles["header"]
            
            val valueCell = row.createCell(1)
            valueCell.setCellValue(value)
            valueCell.cellStyle = styles["data"]
        }
        
        // 컬럼 너비 자동 조정
        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)
    }
    
    /**
     * 검사 결과 시트 생성
     */
    private fun createTestResultSheet(
        workbook: Workbook,
        testResult: TestResult,
        sheetNumber: Int,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("검사 결과 $sheetNumber")
        var rowNum = 0
        
        // 검사 기본 정보
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("검사 결과 #$sheetNumber")
        titleCell.cellStyle = styles["title"]
        
        rowNum++ // 빈 줄
        
        // 검사 정보 테이블
        val testInfo = listOf(
            "검사 시작" to dateFormatter.format(testResult.startTime),
            "검사 종료" to (testResult.endTime?.let { dateFormatter.format(it) } ?: "진행 중"),
            "검사 시간" to "${testResult.getTestDurationSeconds()}초",
            "시퀀스 타입" to if (testResult.sequenceType == 0) "무작위" else "순차",
            "점등 간격" to "${testResult.sequenceInterval}ms",
            "총 LED 쌍" to "${testResult.totalPairs}개",
            "완료된 쌍" to "${testResult.completedPairs}개",
            "정답 수" to "${testResult.correctResponses}개",
            "정확도" to "${"%.1f".format(testResult.getAccuracyPercentage())}%",
            "평균 응답시간" to "${testResult.averageResponseTime}ms",
            "완료율" to "${"%.1f".format(testResult.getCompletionPercentage())}%"
        )
        
        testInfo.forEach { (label, value) ->
            val row = sheet.createRow(rowNum++)
            
            val labelCell = row.createCell(0)
            labelCell.setCellValue(label)
            labelCell.cellStyle = styles["header"]
            
            val valueCell = row.createCell(1)
            valueCell.setCellValue(value)
            valueCell.cellStyle = styles["data"]
        }
        
        rowNum += 2 // 빈 줄들
        
        // 상세 데이터 테이블
        if (testResult.dataPoints.isNotEmpty()) {
            createDetailedDataTable(sheet, testResult.dataPoints, rowNum, styles)
        }
        
        // 컬럼 너비 조정
        (0..7).forEach { sheet.autoSizeColumn(it) }
    }
    
    /**
     * 상세 데이터 테이블 생성
     */
    private fun createDetailedDataTable(
        sheet: Sheet,
        dataPoints: List<TestResult.TestDataPoint>,
        startRowNum: Int,
        styles: Map<String, CellStyle>
    ) {
        var rowNum = startRowNum
        
        // 테이블 제목
        val tableTitle = sheet.createRow(rowNum++)
        val tableTitleCell = tableTitle.createCell(0)
        tableTitleCell.setCellValue("상세 검사 데이터")
        tableTitleCell.cellStyle = styles["subtitle"]
        
        rowNum++ // 빈 줄
        
        // 헤더 행
        val headerRow = sheet.createRow(rowNum++)
        val headers = listOf("순번", "LED쌍", "색상", "자극시간", "응답시간(ms)", "정답여부", "위치X", "위치Y")
        
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = styles["header"]
        }
        
        // 데이터 행
        dataPoints.forEachIndexed { index, dataPoint ->
            val row = sheet.createRow(rowNum++)
            
            row.createCell(0).apply {
                setCellValue((index + 1).toDouble())
                cellStyle = styles["data"]
            }
            
            row.createCell(1).apply {
                setCellValue((dataPoint.ledPairId + 1).toDouble())
                cellStyle = styles["data"]
            }
            
            row.createCell(2).apply {
                setCellValue(if (dataPoint.color == "red") "빨강" else "녹색")
                cellStyle = styles["data"]
            }
            
            row.createCell(3).apply {
                setCellValue(dateFormatter.format(dataPoint.stimulusTime))
                cellStyle = styles["data"]
            }
            
            row.createCell(4).apply {
                setCellValue(dataPoint.responseTime.toDouble())
                cellStyle = styles["data"]
            }
            
            row.createCell(5).apply {
                setCellValue(if (dataPoint.isCorrect) "O" else "X")
                cellStyle = styles[if (dataPoint.isCorrect) "correct" else "incorrect"]
            }
            
            row.createCell(6).apply {
                setCellValue(dataPoint.positionX.toDouble())
                cellStyle = styles["data"]
            }
            
            row.createCell(7).apply {
                setCellValue(dataPoint.positionY.toDouble())
                cellStyle = styles["data"]
            }
        }
    }
    
    /**
     * 요약 시트 생성
     */
    private fun createSummarySheet(
        workbook: Workbook,
        patientInfo: PatientInfo,
        testResults: List<TestResult>,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("요약")
        var rowNum = 0
        
        // 제목
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("검사 요약 - ${patientInfo.name}")
        titleCell.cellStyle = styles["title"]
        
        rowNum += 2 // 빈 줄들
        
        // 검사 결과 요약 테이블
        val summaryHeaderRow = sheet.createRow(rowNum++)
        val summaryHeaders = listOf("검사 번호", "검사일시", "완료율", "정확도", "평균 응답시간", "상태")
        
        summaryHeaders.forEachIndexed { index, header ->
            val cell = summaryHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = styles["header"]
        }
        
        testResults.forEachIndexed { index, testResult ->
            val row = sheet.createRow(rowNum++)
            
            row.createCell(0).apply {
                setCellValue((index + 1).toDouble())
                cellStyle = styles["data"]
            }
            
            row.createCell(1).apply {
                setCellValue(dateFormatter.format(testResult.startTime))
                cellStyle = styles["data"]
            }
            
            row.createCell(2).apply {
                setCellValue("${"%.1f".format(testResult.getCompletionPercentage())}%")
                cellStyle = styles["data"]
            }
            
            row.createCell(3).apply {
                setCellValue("${"%.1f".format(testResult.getAccuracyPercentage())}%")
                cellStyle = styles["data"]
            }
            
            row.createCell(4).apply {
                setCellValue("${testResult.averageResponseTime}ms")
                cellStyle = styles["data"]
            }
            
            row.createCell(5).apply {
                setCellValue(getStatusText(testResult.status))
                cellStyle = styles["data"]
            }
        }
        
        // 컬럼 너비 조정
        (0..5).forEach { sheet.autoSizeColumn(it) }
    }
    
    /**
     * 전체 요약 시트 생성 (다중 환자)
     */
    private fun createOverallSummarySheet(
        workbook: Workbook,
        patientResults: List<Pair<PatientInfo, List<TestResult>>>,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("전체 요약")
        var rowNum = 0
        
        // 제목
        val titleRow = sheet.createRow(rowNum++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("전체 검사 요약")
        titleCell.cellStyle = styles["title"]
        
        rowNum += 2 // 빈 줄들
        
        // 환자별 요약 테이블
        val headerRow = sheet.createRow(rowNum++)
        val headers = listOf("환자명", "환자ID", "나이", "성별", "검사 수", "평균 정확도", "평균 응답시간", "최근 검사일")
        
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = styles["header"]
        }
        
        patientResults.forEach { (patientInfo, testResults) ->
            if (testResults.isNotEmpty()) {
                val row = sheet.createRow(rowNum++)
                
                val avgAccuracy = testResults.map { it.getAccuracyPercentage() }.average()
                val avgResponseTime = testResults.map { it.averageResponseTime.toDouble() }.average()
                val latestTestDate = testResults.maxByOrNull { it.startTime }?.startTime
                
                row.createCell(0).apply {
                    setCellValue(patientInfo.name)
                    cellStyle = styles["data"]
                }
                
                row.createCell(1).apply {
                    setCellValue(patientInfo.patientId)
                    cellStyle = styles["data"]
                }
                
                row.createCell(2).apply {
                    setCellValue(patientInfo.age.toDouble())
                    cellStyle = styles["data"]
                }
                
                row.createCell(3).apply {
                    setCellValue(if (patientInfo.gender == "M") "남" else "여")
                    cellStyle = styles["data"]
                }
                
                row.createCell(4).apply {
                    setCellValue(testResults.size.toDouble())
                    cellStyle = styles["data"]
                }
                
                row.createCell(5).apply {
                    setCellValue("${"%.1f".format(avgAccuracy)}%")
                    cellStyle = styles["data"]
                }
                
                row.createCell(6).apply {
                    setCellValue("${"%.0f".format(avgResponseTime)}ms")
                    cellStyle = styles["data"]
                }
                
                row.createCell(7).apply {
                    setCellValue(latestTestDate?.let { dateFormatter.format(it) } ?: "없음")
                    cellStyle = styles["data"]
                }
            }
        }
        
        // 컬럼 너비 조정
        (0..7).forEach { sheet.autoSizeColumn(it) }
    }
    
    /**
     * 환자 요약 시트 생성 (다중 환자용)
     */
    private fun createPatientSummarySheet(
        workbook: Workbook,
        patientInfo: PatientInfo,
        testResults: List<TestResult>,
        sheetName: String,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet(sheetName)
        var rowNum = 0
        
        // 환자 기본 정보
        val patientRow = sheet.createRow(rowNum++)
        val patientCell = patientRow.createCell(0)
        patientCell.setCellValue("${patientInfo.name} (${patientInfo.patientId})")
        patientCell.cellStyle = styles["title"]
        
        rowNum++ // 빈 줄
        
        // 검사 결과 요약
        if (testResults.isNotEmpty()) {
            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf("검사일", "완료율", "정확도", "응답시간")
            
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = styles["header"]
            }
            
            testResults.forEach { testResult ->
                val row = sheet.createRow(rowNum++)
                
                row.createCell(0).apply {
                    setCellValue(dateFormatter.format(testResult.startTime))
                    cellStyle = styles["data"]
                }
                
                row.createCell(1).apply {
                    setCellValue("${"%.1f".format(testResult.getCompletionPercentage())}%")
                    cellStyle = styles["data"]
                }
                
                row.createCell(2).apply {
                    setCellValue("${"%.1f".format(testResult.getAccuracyPercentage())}%")
                    cellStyle = styles["data"]
                }
                
                row.createCell(3).apply {
                    setCellValue("${testResult.averageResponseTime}ms")
                    cellStyle = styles["data"]
                }
            }
        }
        
        // 컬럼 너비 조정
        (0..3).forEach { sheet.autoSizeColumn(it) }
    }
    
    /**
     * 스타일 생성
     */
    private fun createStyles(workbook: Workbook): Map<String, CellStyle> {
        val styles = mutableMapOf<String, CellStyle>()
        
        // 제목 스타일
        styles["title"] = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeight = 16 * 20 // 16pt
            setFont(font)
            alignment = HorizontalAlignment.CENTER
        }
        
        // 부제목 스타일
        styles["subtitle"] = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeight = 14 * 20 // 14pt
            setFont(font)
        }
        
        // 헤더 스타일
        styles["header"] = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }
        
        // 데이터 스타일
        styles["data"] = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
        }
        
        // 정답 스타일
        styles["correct"] = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }
        
        // 오답 스타일
        styles["incorrect"] = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.ROSE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }
        
        return styles
    }
    
    /**
     * 워크북을 파일로 저장
     */
    private fun saveWorkbook(workbook: Workbook, fileName: String): File {
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { fos ->
            workbook.write(fos)
        }
        
        Timber.i("Excel 파일 저장 완료: ${file.absolutePath}")
        return file
    }
    
    /**
     * 상태 텍스트 변환
     */
    private fun getStatusText(status: String): String {
        return when (status) {
            "IN_PROGRESS" -> "진행 중"
            "COMPLETED" -> "완료"
            "CANCELLED" -> "취소"
            else -> "알 수 없음"
        }
    }
    
    /**
     * Excel 파일 공유
     */
    fun shareExcelFile(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LED Vision Tester 검사 결과")
            putExtra(Intent.EXTRA_TEXT, "LED Vision Tester에서 생성된 검사 결과입니다.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return Intent.createChooser(shareIntent, "검사 결과 공유")
    }
    
    /**
     * 내보내기 결과 봉인 클래스
     */
    sealed class ExportResult {
        data class Success(val file: File, val fileName: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
}