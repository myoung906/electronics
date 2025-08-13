package com.ledvision.tester.model

import java.util.*

/**
 * 환자 정보 데이터 클래스
 */
data class PatientInfo(
    val id: Long,
    val name: String,
    val patientId: String,
    val age: Int,
    val gender: String, // "M" or "F"
    val birthDate: Date?,
    val testDate: Date?,
    val leftEyeVision: String,
    val rightEyeVision: String,
    val doctorName: String,
    val clinicalNotes: String,
    val testType: String, // "STANDARD", "EXTENDED", "CUSTOM"
    val testDuration: Int, // minutes
    val saveResults: Boolean,
    val createdAt: Date,
    val updatedAt: Date
) {
    /**
     * 환자 정보 유효성 검증
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (name.isBlank()) {
            errors.add("환자명을 입력해주세요")
        }
        
        if (patientId.isBlank()) {
            errors.add("환자 ID를 입력해주세요")
        }
        
        if (age <= 0 || age > 150) {
            errors.add("올바른 나이를 입력해주세요 (1-150)")
        }
        
        if (gender !in listOf("M", "F")) {
            errors.add("성별을 선택해주세요")
        }
        
        if (testDuration <= 0 || testDuration > 120) {
            errors.add("검사 시간은 1-120분 사이여야 합니다")
        }
        
        if (testType !in listOf("STANDARD", "EXTENDED", "CUSTOM")) {
            errors.add("올바른 검사 타입을 선택해주세요")
        }
        
        return errors
    }
    
    /**
     * 나이 계산 (생년월일 기준)
     */
    fun calculateAge(): Int? {
        return birthDate?.let { birth ->
            val today = Calendar.getInstance()
            val birthCalendar = Calendar.getInstance().apply { time = birth }
            
            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            
            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            
            age
        }
    }
    
    /**
     * 환자 정보 요약
     */
    fun getSummary(): String {
        val genderText = if (gender == "M") "남" else "여"
        val testDateText = testDate?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
        } ?: "미정"
        
        return "$name ($genderText, ${age}세) - 검사일: $testDateText"
    }
    
    companion object {
        /**
         * 빈 환자 정보 생성
         */
        fun createEmpty(): PatientInfo {
            return PatientInfo(
                id = 0,
                name = "",
                patientId = "",
                age = 0,
                gender = "",
                birthDate = null,
                testDate = Date(),
                leftEyeVision = "",
                rightEyeVision = "",
                doctorName = "",
                clinicalNotes = "",
                testType = "STANDARD",
                testDuration = 30,
                saveResults = true,
                createdAt = Date(),
                updatedAt = Date()
            )
        }
    }
}