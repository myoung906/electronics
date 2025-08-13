package com.ledvision.tester.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ledvision.tester.model.PatientInfo
import com.ledvision.tester.model.TestResult
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues
import android.database.Cursor

/**
 * 환자 정보 및 검사 결과 데이터베이스 관리 Repository
 * 
 * 주요 기능:
 * - 환자 정보 CRUD 작업
 * - 검사 결과 저장/조회
 * - 데이터 검색 및 필터링
 */
class PatientRepository(context: Context) {
    
    private val dbHelper = DatabaseHelper(context)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val DATABASE_NAME = "led_vision_tester.db"
        private const val DATABASE_VERSION = 1
        
        // 테이블명
        private const val TABLE_PATIENTS = "patients"
        private const val TABLE_TEST_RESULTS = "test_results"
        private const val TABLE_TEST_DATA_POINTS = "test_data_points"
        
        // 환자 테이블 컬럼
        private const val COL_PATIENT_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_PATIENT_NUMBER = "patient_number"
        private const val COL_AGE = "age"
        private const val COL_GENDER = "gender"
        private const val COL_BIRTH_DATE = "birth_date"
        private const val COL_TEST_DATE = "test_date"
        private const val COL_LEFT_EYE_VISION = "left_eye_vision"
        private const val COL_RIGHT_EYE_VISION = "right_eye_vision"
        private const val COL_DOCTOR_NAME = "doctor_name"
        private const val COL_CLINICAL_NOTES = "clinical_notes"
        private const val COL_TEST_TYPE = "test_type"
        private const val COL_TEST_DURATION = "test_duration"
        private const val COL_SAVE_RESULTS = "save_results"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        
        // 검사 결과 테이블 컬럼
        private const val COL_TEST_ID = "id"
        private const val COL_TEST_PATIENT_ID = "patient_id"
        private const val COL_TEST_START_TIME = "start_time"
        private const val COL_TEST_END_TIME = "end_time"
        private const val COL_SEQUENCE_TYPE = "sequence_type"
        private const val COL_SEQUENCE_INTERVAL = "sequence_interval"
        private const val COL_TOTAL_PAIRS = "total_pairs"
        private const val COL_COMPLETED_PAIRS = "completed_pairs"
        private const val COL_CORRECT_RESPONSES = "correct_responses"
        private const val COL_AVERAGE_RESPONSE_TIME = "average_response_time"
        private const val COL_TEST_STATUS = "test_status"
        
        // 검사 데이터 포인트 테이블 컬럼
        private const val COL_DATA_ID = "id"
        private const val COL_DATA_TEST_ID = "test_id"
        private const val COL_LED_PAIR_ID = "led_pair_id"
        private const val COL_LED_COLOR = "led_color"
        private const val COL_STIMULUS_TIME = "stimulus_time"
        private const val COL_RESPONSE_TIME = "response_time"
        private const val COL_IS_CORRECT = "is_correct"
        private const val COL_POSITION_X = "position_x"
        private const val COL_POSITION_Y = "position_y"
    }
    
    // === 환자 정보 관리 ===
    
    /**
     * 환자 정보 저장
     */
    suspend fun insertPatient(patientInfo: PatientInfo): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(COL_NAME, patientInfo.name)
                put(COL_PATIENT_NUMBER, patientInfo.patientId)
                put(COL_AGE, patientInfo.age)
                put(COL_GENDER, patientInfo.gender)
                put(COL_BIRTH_DATE, patientInfo.birthDate?.let { dateFormatter.format(it) })
                put(COL_TEST_DATE, patientInfo.testDate?.let { dateFormatter.format(it) })
                put(COL_LEFT_EYE_VISION, patientInfo.leftEyeVision)
                put(COL_RIGHT_EYE_VISION, patientInfo.rightEyeVision)
                put(COL_DOCTOR_NAME, patientInfo.doctorName)
                put(COL_CLINICAL_NOTES, patientInfo.clinicalNotes)
                put(COL_TEST_TYPE, patientInfo.testType)
                put(COL_TEST_DURATION, patientInfo.testDuration)
                put(COL_SAVE_RESULTS, if (patientInfo.saveResults) 1 else 0)
                put(COL_CREATED_AT, dateFormatter.format(patientInfo.createdAt))
                put(COL_UPDATED_AT, dateFormatter.format(patientInfo.updatedAt))
            }
            
            val rowId = db.insert(TABLE_PATIENTS, null, values)
            Timber.i("환자 정보 저장 완료: ID=$rowId")
            rowId > 0
            
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 저장 실패")
            false
        }
    }
    
    /**
     * 환자 정보 업데이트
     */
    suspend fun updatePatient(patientInfo: PatientInfo): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(COL_NAME, patientInfo.name)
                put(COL_PATIENT_NUMBER, patientInfo.patientId)
                put(COL_AGE, patientInfo.age)
                put(COL_GENDER, patientInfo.gender)
                put(COL_BIRTH_DATE, patientInfo.birthDate?.let { dateFormatter.format(it) })
                put(COL_TEST_DATE, patientInfo.testDate?.let { dateFormatter.format(it) })
                put(COL_LEFT_EYE_VISION, patientInfo.leftEyeVision)
                put(COL_RIGHT_EYE_VISION, patientInfo.rightEyeVision)
                put(COL_DOCTOR_NAME, patientInfo.doctorName)
                put(COL_CLINICAL_NOTES, patientInfo.clinicalNotes)
                put(COL_TEST_TYPE, patientInfo.testType)
                put(COL_TEST_DURATION, patientInfo.testDuration)
                put(COL_SAVE_RESULTS, if (patientInfo.saveResults) 1 else 0)
                put(COL_UPDATED_AT, dateFormatter.format(Date()))
            }
            
            val rowsAffected = db.update(
                TABLE_PATIENTS,
                values,
                "$COL_PATIENT_ID = ?",
                arrayOf(patientInfo.id.toString())
            )
            
            Timber.i("환자 정보 업데이트 완료: ID=${patientInfo.id}")
            rowsAffected > 0
            
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 업데이트 실패")
            false
        }
    }
    
    /**
     * ID로 환자 정보 조회
     */
    suspend fun getPatientById(patientId: String): PatientInfo? {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                TABLE_PATIENTS,
                null,
                "$COL_PATIENT_ID = ?",
                arrayOf(patientId),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    parsePatientInfo(it)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 조회 실패: $patientId")
            null
        }
    }
    
    /**
     * 환자 번호로 환자 정보 조회
     */
    suspend fun getPatientByPatientId(patientNumber: String): PatientInfo? {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                TABLE_PATIENTS,
                null,
                "$COL_PATIENT_NUMBER = ?",
                arrayOf(patientNumber),
                null, null, null
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    parsePatientInfo(it)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 조회 실패: $patientNumber")
            null
        }
    }
    
    /**
     * 모든 환자 정보 조회
     */
    suspend fun getAllPatients(): List<PatientInfo> {
        return try {
            val db = dbHelper.readableDatabase
            val patients = mutableListOf<PatientInfo>()
            
            val cursor = db.query(
                TABLE_PATIENTS,
                null, null, null, null, null,
                "$COL_UPDATED_AT DESC"
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val patient = parsePatientInfo(it)
                    if (patient != null) {
                        patients.add(patient)
                    }
                }
            }
            
            patients
        } catch (e: Exception) {
            Timber.e(e, "전체 환자 목록 조회 실패")
            emptyList()
        }
    }
    
    /**
     * 환자 검색
     */
    suspend fun searchPatients(query: String): List<PatientInfo> {
        return try {
            val db = dbHelper.readableDatabase
            val patients = mutableListOf<PatientInfo>()
            
            val cursor = db.query(
                TABLE_PATIENTS,
                null,
                "$COL_NAME LIKE ? OR $COL_PATIENT_NUMBER LIKE ?",
                arrayOf("%$query%", "%$query%"),
                null, null,
                "$COL_UPDATED_AT DESC"
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val patient = parsePatientInfo(it)
                    if (patient != null) {
                        patients.add(patient)
                    }
                }
            }
            
            patients
        } catch (e: Exception) {
            Timber.e(e, "환자 검색 실패: $query")
            emptyList()
        }
    }
    
    // === 검사 결과 관리 ===
    
    /**
     * 검사 결과 저장
     */
    suspend fun insertTestResult(testResult: TestResult): Long {
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(COL_TEST_PATIENT_ID, testResult.patientId)
                put(COL_TEST_START_TIME, dateFormatter.format(testResult.startTime))
                put(COL_TEST_END_TIME, testResult.endTime?.let { dateFormatter.format(it) })
                put(COL_SEQUENCE_TYPE, testResult.sequenceType)
                put(COL_SEQUENCE_INTERVAL, testResult.sequenceInterval)
                put(COL_TOTAL_PAIRS, testResult.totalPairs)
                put(COL_COMPLETED_PAIRS, testResult.completedPairs)
                put(COL_CORRECT_RESPONSES, testResult.correctResponses)
                put(COL_AVERAGE_RESPONSE_TIME, testResult.averageResponseTime)
                put(COL_TEST_STATUS, testResult.status)
            }
            
            val testId = db.insert(TABLE_TEST_RESULTS, null, values)
            
            // 검사 데이터 포인트 저장
            testResult.dataPoints.forEach { dataPoint ->
                insertTestDataPoint(testId, dataPoint)
            }
            
            Timber.i("검사 결과 저장 완료: ID=$testId")
            testId
            
        } catch (e: Exception) {
            Timber.e(e, "검사 결과 저장 실패")
            -1
        }
    }
    
    /**
     * 검사 데이터 포인트 저장
     */
    private fun insertTestDataPoint(testId: Long, dataPoint: TestResult.TestDataPoint) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(COL_DATA_TEST_ID, testId)
                put(COL_LED_PAIR_ID, dataPoint.ledPairId)
                put(COL_LED_COLOR, dataPoint.color)
                put(COL_STIMULUS_TIME, dateFormatter.format(dataPoint.stimulusTime))
                put(COL_RESPONSE_TIME, dataPoint.responseTime)
                put(COL_IS_CORRECT, if (dataPoint.isCorrect) 1 else 0)
                put(COL_POSITION_X, dataPoint.positionX)
                put(COL_POSITION_Y, dataPoint.positionY)
            }
            
            db.insert(TABLE_TEST_DATA_POINTS, null, values)
        } catch (e: Exception) {
            Timber.e(e, "검사 데이터 포인트 저장 실패")
        }
    }
    
    /**
     * 환자 ID로 검사 결과 조회
     */
    suspend fun getTestResultsByPatientId(patientId: Long): List<TestResult> {
        return try {
            val db = dbHelper.readableDatabase
            val testResults = mutableListOf<TestResult>()
            
            val cursor = db.query(
                TABLE_TEST_RESULTS,
                null,
                "$COL_TEST_PATIENT_ID = ?",
                arrayOf(patientId.toString()),
                null, null,
                "$COL_TEST_START_TIME DESC"
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val testResult = parseTestResult(it)
                    if (testResult != null) {
                        testResults.add(testResult)
                    }
                }
            }
            
            testResults
        } catch (e: Exception) {
            Timber.e(e, "검사 결과 조회 실패: patientId=$patientId")
            emptyList()
        }
    }
    
    // === 유틸리티 메서드 ===
    
    /**
     * Cursor에서 PatientInfo 파싱
     */
    private fun parsePatientInfo(cursor: Cursor): PatientInfo? {
        return try {
            PatientInfo(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PATIENT_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                patientId = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_NUMBER)),
                age = cursor.getInt(cursor.getColumnIndexOrThrow(COL_AGE)),
                gender = cursor.getString(cursor.getColumnIndexOrThrow(COL_GENDER)),
                birthDate = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_BIRTH_DATE))),
                testDate = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_TEST_DATE))),
                leftEyeVision = cursor.getString(cursor.getColumnIndexOrThrow(COL_LEFT_EYE_VISION)) ?: "",
                rightEyeVision = cursor.getString(cursor.getColumnIndexOrThrow(COL_RIGHT_EYE_VISION)) ?: "",
                doctorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOCTOR_NAME)) ?: "",
                clinicalNotes = cursor.getString(cursor.getColumnIndexOrThrow(COL_CLINICAL_NOTES)) ?: "",
                testType = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEST_TYPE)),
                testDuration = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TEST_DURATION)),
                saveResults = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAVE_RESULTS)) == 1,
                createdAt = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT))) ?: Date(),
                updatedAt = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))) ?: Date()
            )
        } catch (e: Exception) {
            Timber.e(e, "환자 정보 파싱 실패")
            null
        }
    }
    
    /**
     * Cursor에서 TestResult 파싱
     */
    private fun parseTestResult(cursor: Cursor): TestResult? {
        return try {
            val testId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TEST_ID))
            val dataPoints = getTestDataPoints(testId)
            
            TestResult(
                id = testId,
                patientId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TEST_PATIENT_ID)),
                startTime = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_TEST_START_TIME))) ?: Date(),
                endTime = parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_TEST_END_TIME))),
                sequenceType = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SEQUENCE_TYPE)),
                sequenceInterval = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SEQUENCE_INTERVAL)),
                totalPairs = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TOTAL_PAIRS)),
                completedPairs = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED_PAIRS)),
                correctResponses = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CORRECT_RESPONSES)),
                averageResponseTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_AVERAGE_RESPONSE_TIME)),
                status = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEST_STATUS)),
                dataPoints = dataPoints
            )
        } catch (e: Exception) {
            Timber.e(e, "검사 결과 파싱 실패")
            null
        }
    }
    
    /**
     * 검사 데이터 포인트 조회
     */
    private fun getTestDataPoints(testId: Long): List<TestResult.TestDataPoint> {
        return try {
            val db = dbHelper.readableDatabase
            val dataPoints = mutableListOf<TestResult.TestDataPoint>()
            
            val cursor = db.query(
                TABLE_TEST_DATA_POINTS,
                null,
                "$COL_DATA_TEST_ID = ?",
                arrayOf(testId.toString()),
                null, null,
                "$COL_DATA_ID ASC"
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val dataPoint = TestResult.TestDataPoint(
                        ledPairId = it.getInt(it.getColumnIndexOrThrow(COL_LED_PAIR_ID)),
                        color = it.getString(it.getColumnIndexOrThrow(COL_LED_COLOR)),
                        stimulusTime = parseDate(it.getString(it.getColumnIndexOrThrow(COL_STIMULUS_TIME))) ?: Date(),
                        responseTime = it.getLong(it.getColumnIndexOrThrow(COL_RESPONSE_TIME)),
                        isCorrect = it.getInt(it.getColumnIndexOrThrow(COL_IS_CORRECT)) == 1,
                        positionX = it.getFloat(it.getColumnIndexOrThrow(COL_POSITION_X)),
                        positionY = it.getFloat(it.getColumnIndexOrThrow(COL_POSITION_Y))
                    )
                    dataPoints.add(dataPoint)
                }
            }
            
            dataPoints
        } catch (e: Exception) {
            Timber.e(e, "검사 데이터 포인트 조회 실패: testId=$testId")
            emptyList()
        }
    }
    
    /**
     * 문자열을 Date로 파싱
     */
    private fun parseDate(dateString: String?): Date? {
        return try {
            if (dateString != null && dateString.isNotEmpty()) {
                dateFormatter.parse(dateString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // === 데이터베이스 헬퍼 클래스 ===
    
    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(
        context, DATABASE_NAME, null, DATABASE_VERSION
    ) {
        
        override fun onCreate(db: SQLiteDatabase) {
            // 환자 테이블 생성
            val createPatientsTable = """
                CREATE TABLE $TABLE_PATIENTS (
                    $COL_PATIENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NAME TEXT NOT NULL,
                    $COL_PATIENT_NUMBER TEXT UNIQUE NOT NULL,
                    $COL_AGE INTEGER NOT NULL,
                    $COL_GENDER TEXT NOT NULL,
                    $COL_BIRTH_DATE TEXT,
                    $COL_TEST_DATE TEXT,
                    $COL_LEFT_EYE_VISION TEXT,
                    $COL_RIGHT_EYE_VISION TEXT,
                    $COL_DOCTOR_NAME TEXT,
                    $COL_CLINICAL_NOTES TEXT,
                    $COL_TEST_TYPE TEXT NOT NULL DEFAULT 'STANDARD',
                    $COL_TEST_DURATION INTEGER NOT NULL DEFAULT 30,
                    $COL_SAVE_RESULTS INTEGER NOT NULL DEFAULT 1,
                    $COL_CREATED_AT TEXT NOT NULL,
                    $COL_UPDATED_AT TEXT NOT NULL
                )
            """.trimIndent()
            
            // 검사 결과 테이블 생성
            val createTestResultsTable = """
                CREATE TABLE $TABLE_TEST_RESULTS (
                    $COL_TEST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TEST_PATIENT_ID INTEGER NOT NULL,
                    $COL_TEST_START_TIME TEXT NOT NULL,
                    $COL_TEST_END_TIME TEXT,
                    $COL_SEQUENCE_TYPE INTEGER NOT NULL,
                    $COL_SEQUENCE_INTERVAL INTEGER NOT NULL,
                    $COL_TOTAL_PAIRS INTEGER NOT NULL,
                    $COL_COMPLETED_PAIRS INTEGER NOT NULL DEFAULT 0,
                    $COL_CORRECT_RESPONSES INTEGER NOT NULL DEFAULT 0,
                    $COL_AVERAGE_RESPONSE_TIME INTEGER NOT NULL DEFAULT 0,
                    $COL_TEST_STATUS TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                    FOREIGN KEY($COL_TEST_PATIENT_ID) REFERENCES $TABLE_PATIENTS($COL_PATIENT_ID)
                )
            """.trimIndent()
            
            // 검사 데이터 포인트 테이블 생성
            val createDataPointsTable = """
                CREATE TABLE $TABLE_TEST_DATA_POINTS (
                    $COL_DATA_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_DATA_TEST_ID INTEGER NOT NULL,
                    $COL_LED_PAIR_ID INTEGER NOT NULL,
                    $COL_LED_COLOR TEXT NOT NULL,
                    $COL_STIMULUS_TIME TEXT NOT NULL,
                    $COL_RESPONSE_TIME INTEGER NOT NULL,
                    $COL_IS_CORRECT INTEGER NOT NULL,
                    $COL_POSITION_X REAL NOT NULL DEFAULT 0.0,
                    $COL_POSITION_Y REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY($COL_DATA_TEST_ID) REFERENCES $TABLE_TEST_RESULTS($COL_TEST_ID)
                )
            """.trimIndent()
            
            db.execSQL(createPatientsTable)
            db.execSQL(createTestResultsTable)
            db.execSQL(createDataPointsTable)
            
            // 인덱스 생성
            db.execSQL("CREATE INDEX idx_patient_number ON $TABLE_PATIENTS($COL_PATIENT_NUMBER)")
            db.execSQL("CREATE INDEX idx_test_patient_id ON $TABLE_TEST_RESULTS($COL_TEST_PATIENT_ID)")
            db.execSQL("CREATE INDEX idx_data_test_id ON $TABLE_TEST_DATA_POINTS($COL_DATA_TEST_ID)")
            
            Timber.i("데이터베이스 테이블 생성 완료")
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Timber.w("데이터베이스 업그레이드: $oldVersion -> $newVersion")
            
            // 필요시 마이그레이션 로직 추가
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TEST_DATA_POINTS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TEST_RESULTS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PATIENTS")
            onCreate(db)
        }
    }
}