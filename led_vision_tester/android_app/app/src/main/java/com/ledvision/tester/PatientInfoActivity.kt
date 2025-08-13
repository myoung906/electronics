package com.ledvision.tester

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.ledvision.tester.databinding.ActivityPatientInfoBinding
import com.ledvision.tester.model.PatientInfo
import com.ledvision.tester.viewmodel.PatientInfoViewModel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * 환자 정보 입력 액티비티
 * 
 * 주요 기능:
 * - 환자 개인정보 입력
 * - 검사 설정 관리
 * - 데이터 검증 및 저장
 */
class PatientInfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPatientInfoBinding
    private val viewModel: PatientInfoViewModel by viewModels()
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = DataBindingUtil.setContentView(this, R.layout.activity_patient_info)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        
        setupUI()
        setupObservers()
        
        // 기존 환자 정보 로드 (편집 모드인 경우)
        intent.getStringExtra("patient_id")?.let { patientId ->
            viewModel.loadPatientInfo(patientId)
        }
    }
    
    /**
     * UI 초기 설정
     */
    private fun setupUI() {
        // 툴바 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "환자 정보"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 생년월일 선택 버튼
        binding.btnSelectBirthDate.setOnClickListener {
            showDatePicker { date ->
                binding.editBirthDate.setText(dateFormatter.format(date))
                viewModel.updateBirthDate(date)
            }
        }
        
        // 검사일 선택 버튼
        binding.btnSelectTestDate.setOnClickListener {
            showDatePicker { date ->
                binding.editTestDate.setText(dateFormatter.format(date))
                viewModel.updateTestDate(date)
            }
        }
        
        // 저장 버튼
        binding.btnSave.setOnClickListener {
            savePatientInfo()
        }
        
        // 취소 버튼
        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        // 기본값 설정
        val today = Calendar.getInstance().time
        binding.editTestDate.setText(dateFormatter.format(today))
        viewModel.updateTestDate(today)
    }
    
    /**
     * ViewModel 옵저버 설정
     */
    private fun setupObservers() {
        // 환자 정보 로드 관찰
        viewModel.patientInfo.observe(this) { patientInfo ->
            if (patientInfo != null) {
                populateFields(patientInfo)
            }
        }
        
        // 저장 결과 관찰
        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is PatientInfoViewModel.SaveResult.Success -> {
                    Toast.makeText(this, "환자 정보가 저장되었습니다", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is PatientInfoViewModel.SaveResult.Error -> {
                    Toast.makeText(this, "저장 실패: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // 입력 검증 오류 관찰
        viewModel.validationErrors.observe(this) { errors ->
            displayValidationErrors(errors)
        }
    }
    
    /**
     * 기존 환자 정보로 필드 채우기
     */
    private fun populateFields(patientInfo: PatientInfo) {
        binding.apply {
            editPatientName.setText(patientInfo.name)
            editPatientId.setText(patientInfo.patientId)
            editAge.setText(patientInfo.age.toString())
            
            // 성별 선택
            when (patientInfo.gender) {
                "M" -> radioMale.isChecked = true
                "F" -> radioFemale.isChecked = true
            }
            
            // 날짜 설정
            patientInfo.birthDate?.let { date ->
                editBirthDate.setText(dateFormatter.format(date))
            }
            
            patientInfo.testDate?.let { date ->
                editTestDate.setText(dateFormatter.format(date))
            }
            
            // 시력 정보
            editLeftEyeVision.setText(patientInfo.leftEyeVision)
            editRightEyeVision.setText(patientInfo.rightEyeVision)
            
            // 의료진 정보
            editDoctorName.setText(patientInfo.doctorName)
            editClinicalNotes.setText(patientInfo.clinicalNotes)
            
            // 검사 설정
            spinnerTestType.setSelection(getTestTypePosition(patientInfo.testType))
            editTestDuration.setText(patientInfo.testDuration.toString())
            
            checkboxSaveResults.isChecked = patientInfo.saveResults
        }
    }
    
    /**
     * 환자 정보 저장
     */
    private fun savePatientInfo() {
        val patientInfo = collectPatientInfo()
        
        if (patientInfo != null) {
            viewModel.savePatientInfo(patientInfo)
        }
    }
    
    /**
     * 입력 필드에서 환자 정보 수집
     */
    private fun collectPatientInfo(): PatientInfo? {
        try {
            val name = binding.editPatientName.text.toString().trim()
            val patientId = binding.editPatientId.text.toString().trim()
            val ageText = binding.editAge.text.toString().trim()
            
            // 필수 필드 검증
            if (name.isEmpty() || patientId.isEmpty() || ageText.isEmpty()) {
                Toast.makeText(this, "필수 정보를 모두 입력해주세요", Toast.LENGTH_SHORT).show()
                return null
            }
            
            val age = ageText.toIntOrNull() ?: run {
                Toast.makeText(this, "올바른 나이를 입력해주세요", Toast.LENGTH_SHORT).show()
                return null
            }
            
            // 성별 확인
            val gender = when {
                binding.radioMale.isChecked -> "M"
                binding.radioFemale.isChecked -> "F"
                else -> {
                    Toast.makeText(this, "성별을 선택해주세요", Toast.LENGTH_SHORT).show()
                    return null
                }
            }
            
            // 날짜 파싱
            val birthDate = parseDateString(binding.editBirthDate.text.toString())
            val testDate = parseDateString(binding.editTestDate.text.toString()) ?: Date()
            
            return PatientInfo(
                id = System.currentTimeMillis(), // 새 ID 생성
                name = name,
                patientId = patientId,
                age = age,
                gender = gender,
                birthDate = birthDate,
                testDate = testDate,
                leftEyeVision = binding.editLeftEyeVision.text.toString().trim(),
                rightEyeVision = binding.editRightEyeVision.text.toString().trim(),
                doctorName = binding.editDoctorName.text.toString().trim(),
                clinicalNotes = binding.editClinicalNotes.text.toString().trim(),
                testType = getSelectedTestType(),
                testDuration = binding.editTestDuration.text.toString().toIntOrNull() ?: 30,
                saveResults = binding.checkboxSaveResults.isChecked,
                createdAt = Date(),
                updatedAt = Date()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 수집 중 오류")
            Toast.makeText(this, "정보 입력 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            return null
        }
    }
    
    /**
     * 날짜 선택 다이얼로그 표시
     */
    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        datePickerDialog.show()
    }
    
    /**
     * 문자열을 Date로 파싱
     */
    private fun parseDateString(dateString: String): Date? {
        return try {
            if (dateString.isNotEmpty()) {
                dateFormatter.parse(dateString)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "날짜 파싱 실패: $dateString")
            null
        }
    }
    
    /**
     * 선택된 검사 타입 가져오기
     */
    private fun getSelectedTestType(): String {
        return when (binding.spinnerTestType.selectedItemPosition) {
            0 -> "STANDARD"
            1 -> "EXTENDED" 
            2 -> "CUSTOM"
            else -> "STANDARD"
        }
    }
    
    /**
     * 검사 타입에 해당하는 스피너 위치 가져오기
     */
    private fun getTestTypePosition(testType: String): Int {
        return when (testType) {
            "STANDARD" -> 0
            "EXTENDED" -> 1
            "CUSTOM" -> 2
            else -> 0
        }
    }
    
    /**
     * 입력 검증 오류 표시
     */
    private fun displayValidationErrors(errors: List<String>) {
        if (errors.isNotEmpty()) {
            val errorMessage = errors.joinToString("\n")
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}