package com.ledvision.tester.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ledvision.tester.model.PatientInfo
import com.ledvision.tester.repository.PatientRepository
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

/**
 * 환자 정보 ViewModel
 * 
 * 주요 역할:
 * - 환자 정보 입력 데이터 관리
 * - 데이터 유효성 검증
 * - 환자 정보 저장/로드
 */
class PatientInfoViewModel(application: Application) : AndroidViewModel(application) {
    
    private val patientRepository = PatientRepository(application)
    
    // === LiveData 정의 ===
    
    private val _patientInfo = MutableLiveData<PatientInfo?>()
    val patientInfo: LiveData<PatientInfo?> = _patientInfo
    
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult
    
    private val _validationErrors = MutableLiveData<List<String>>()
    val validationErrors: LiveData<List<String>> = _validationErrors
    
    // 입력 필드 LiveData
    private val _name = MutableLiveData<String>()
    val name: LiveData<String> = _name
    
    private val _patientId = MutableLiveData<String>()
    val patientId: LiveData<String> = _patientId
    
    private val _age = MutableLiveData<String>()
    val age: LiveData<String> = _age
    
    private val _gender = MutableLiveData<String>()
    val gender: LiveData<String> = _gender
    
    private val _birthDate = MutableLiveData<Date?>()
    val birthDate: LiveData<Date?> = _birthDate
    
    private val _testDate = MutableLiveData<Date?>()
    val testDate: LiveData<Date?> = _testDate
    
    private val _leftEyeVision = MutableLiveData<String>()
    val leftEyeVision: LiveData<String> = _leftEyeVision
    
    private val _rightEyeVision = MutableLiveData<String>()
    val rightEyeVision: LiveData<String> = _rightEyeVision
    
    private val _doctorName = MutableLiveData<String>()
    val doctorName: LiveData<String> = _doctorName
    
    private val _clinicalNotes = MutableLiveData<String>()
    val clinicalNotes: LiveData<String> = _clinicalNotes
    
    init {
        // 기본값 설정
        _testDate.value = Date()
        clearValidationErrors()
    }
    
    /**
     * 기존 환자 정보 로드
     */
    fun loadPatientInfo(patientId: String) {
        viewModelScope.launch {
            try {
                val patient = patientRepository.getPatientById(patientId)
                _patientInfo.value = patient
                
                // 입력 필드에 데이터 설정
                patient?.let { populateFields(it) }
                
                Timber.i("환자 정보 로드 완료: $patientId")
            } catch (e: Exception) {
                Timber.e(e, "환자 정보 로드 실패")
                _saveResult.value = SaveResult.Error("환자 정보 로드 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 환자 정보 저장
     */
    fun savePatientInfo(patientInfo: PatientInfo) {
        viewModelScope.launch {
            try {
                // 데이터 유효성 검증
                val validationErrors = patientInfo.validate()
                if (validationErrors.isNotEmpty()) {
                    _validationErrors.value = validationErrors
                    return@launch
                }
                
                // 중복 환자 ID 확인
                if (isDuplicatePatientId(patientInfo.patientId, patientInfo.id)) {
                    _validationErrors.value = listOf("이미 존재하는 환자 ID입니다")
                    return@launch
                }
                
                // 환자 정보 저장
                val success = if (patientInfo.id > 0) {
                    patientRepository.updatePatient(patientInfo)
                } else {
                    val newPatientInfo = patientInfo.copy(
                        id = System.currentTimeMillis(),
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                    patientRepository.insertPatient(newPatientInfo)
                }
                
                if (success) {
                    _saveResult.value = SaveResult.Success
                    Timber.i("환자 정보 저장 완료: ${patientInfo.patientId}")
                } else {
                    _saveResult.value = SaveResult.Error("저장 실패")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "환자 정보 저장 중 오류")
                _saveResult.value = SaveResult.Error("저장 중 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 입력 필드 데이터로 환자 정보 생성
     */
    fun createPatientInfoFromFields(): PatientInfo? {
        return try {
            val ageValue = _age.value?.toIntOrNull()
            if (ageValue == null || ageValue <= 0) {
                _validationErrors.value = listOf("올바른 나이를 입력해주세요")
                return null
            }
            
            PatientInfo(
                id = _patientInfo.value?.id ?: 0,
                name = _name.value.orEmpty().trim(),
                patientId = _patientId.value.orEmpty().trim(),
                age = ageValue,
                gender = _gender.value.orEmpty(),
                birthDate = _birthDate.value,
                testDate = _testDate.value,
                leftEyeVision = _leftEyeVision.value.orEmpty().trim(),
                rightEyeVision = _rightEyeVision.value.orEmpty().trim(),
                doctorName = _doctorName.value.orEmpty().trim(),
                clinicalNotes = _clinicalNotes.value.orEmpty().trim(),
                testType = "STANDARD", // 기본값
                testDuration = 30, // 기본값
                saveResults = true, // 기본값
                createdAt = _patientInfo.value?.createdAt ?: Date(),
                updatedAt = Date()
            )
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 생성 실패")
            _validationErrors.value = listOf("입력 정보를 확인해주세요")
            null
        }
    }
    
    /**
     * 기존 환자 정보로 입력 필드 채우기
     */
    private fun populateFields(patientInfo: PatientInfo) {
        _name.value = patientInfo.name
        _patientId.value = patientInfo.patientId
        _age.value = patientInfo.age.toString()
        _gender.value = patientInfo.gender
        _birthDate.value = patientInfo.birthDate
        _testDate.value = patientInfo.testDate
        _leftEyeVision.value = patientInfo.leftEyeVision
        _rightEyeVision.value = patientInfo.rightEyeVision
        _doctorName.value = patientInfo.doctorName
        _clinicalNotes.value = patientInfo.clinicalNotes
    }
    
    /**
     * 중복 환자 ID 확인
     */
    private suspend fun isDuplicatePatientId(patientId: String, currentId: Long): Boolean {
        return try {
            val existingPatient = patientRepository.getPatientByPatientId(patientId)
            existingPatient != null && existingPatient.id != currentId
        } catch (e: Exception) {
            Timber.w(e, "중복 환자 ID 확인 중 오류")
            false
        }
    }
    
    // === 입력 필드 업데이트 메서드 ===
    
    fun updateName(name: String) {
        _name.value = name
    }
    
    fun updatePatientId(patientId: String) {
        _patientId.value = patientId
    }
    
    fun updateAge(age: String) {
        _age.value = age
    }
    
    fun updateGender(gender: String) {
        _gender.value = gender
    }
    
    fun updateBirthDate(date: Date) {
        _birthDate.value = date
        
        // 생년월일로부터 나이 자동 계산
        val calculatedAge = calculateAgeFromBirthDate(date)
        if (calculatedAge > 0) {
            _age.value = calculatedAge.toString()
        }
    }
    
    fun updateTestDate(date: Date) {
        _testDate.value = date
    }
    
    fun updateLeftEyeVision(vision: String) {
        _leftEyeVision.value = vision
    }
    
    fun updateRightEyeVision(vision: String) {
        _rightEyeVision.value = vision
    }
    
    fun updateDoctorName(doctorName: String) {
        _doctorName.value = doctorName
    }
    
    fun updateClinicalNotes(notes: String) {
        _clinicalNotes.value = notes
    }
    
    /**
     * 생년월일로부터 나이 계산
     */
    private fun calculateAgeFromBirthDate(birthDate: Date): Int {
        return try {
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance().apply { time = birthDate }
            
            var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            
            if (age >= 0) age else 0
        } catch (e: Exception) {
            Timber.w(e, "나이 계산 실패")
            0
        }
    }
    
    /**
     * 검증 오류 초기화
     */
    fun clearValidationErrors() {
        _validationErrors.value = emptyList()
    }
    
    /**
     * 모든 입력 필드 초기화
     */
    fun clearAllFields() {
        _name.value = ""
        _patientId.value = ""
        _age.value = ""
        _gender.value = ""
        _birthDate.value = null
        _testDate.value = Date()
        _leftEyeVision.value = ""
        _rightEyeVision.value = ""
        _doctorName.value = ""
        _clinicalNotes.value = ""
        _patientInfo.value = null
        clearValidationErrors()
    }
    
    /**
     * 저장 결과 봉인 클래스
     */
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}