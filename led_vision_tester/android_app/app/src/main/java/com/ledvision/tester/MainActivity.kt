package com.ledvision.tester

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.ledvision.tester.bluetooth.BluetoothManager
import com.ledvision.tester.databinding.ActivityMainBinding
import com.ledvision.tester.viewmodel.MainViewModel
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

/**
 * 메인 액티비티 - LED 시각검사 제어 인터페이스
 * 
 * 주요 기능:
 * - ESP32와 Bluetooth 연결 관리
 * - LED 시퀀스 제어
 * - 실시간 상태 모니터링
 * - 검사 데이터 수집
 */
class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    
    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST = 1001
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    // Bluetooth 활성화 요청 런처
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("Bluetooth 활성화됨")
            viewModel.initializeBluetooth()
        } else {
            Timber.w("Bluetooth 활성화 거부됨")
            showToast("Bluetooth가 필요합니다")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 데이터 바인딩 설정
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        
        setupUI()
        setupObservers()
        
        // 권한 확인 및 Bluetooth 초기화
        checkPermissionsAndInitialize()
    }
    
    /**
     * UI 초기 설정
     */
    private fun setupUI() {
        // 툴바 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "LED Vision Tester"
        
        // 버튼 클릭 리스너
        binding.btnConnect.setOnClickListener {
            if (viewModel.isConnected.value == true) {
                viewModel.disconnect()
            } else {
                viewModel.connect()
            }
        }
        
        binding.btnStartSequence.setOnClickListener {
            if (viewModel.isSequenceRunning.value == true) {
                viewModel.stopSequence()
            } else {
                val type = if (binding.radioRandomSequence.isChecked) 0 else 1
                val interval = binding.seekBarInterval.progress.coerceAtLeast(200)
                viewModel.startSequence(type, interval)
            }
        }
        
        binding.btnPatientInfo.setOnClickListener {
            startActivity(Intent(this, PatientInfoActivity::class.java))
        }
        
        binding.btnTestResults.setOnClickListener {
            startActivity(Intent(this, TestResultsActivity::class.java))
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // 시퀀스 간격 슬라이더 설정
        binding.seekBarInterval.apply {
            min = 200
            max = 3000
            progress = 800
        }
        
        // LED 그리드 설정
        setupLedGrid()
    }
    
    /**
     * ViewModel 옵저버 설정
     */
    private fun setupObservers() {
        // 연결 상태 관찰
        viewModel.connectionState.observe(this) { state ->
            updateConnectionUI(state)
        }
        
        // 장치 정보 관찰
        viewModel.deviceInfo.observe(this) { deviceInfo ->
            binding.textDeviceInfo.text = if (deviceInfo != null) {
                "연결됨: ${deviceInfo.name} (${deviceInfo.address})"
            } else {
                "연결된 장치 없음"
            }
        }
        
        // 시퀀스 상태 관찰
        viewModel.isSequenceRunning.observe(this) { isRunning ->
            binding.btnStartSequence.text = if (isRunning) "정지" else "시작"
            binding.progressSequence.isIndeterminate = isRunning
        }
        
        // 시퀀스 진행률 관찰
        viewModel.sequenceProgress.observe(this) { progress ->
            binding.progressSequence.progress = progress
            binding.textProgress.text = "${progress}%"
        }
        
        // 현재 LED 쌍 관찰
        viewModel.currentLedPair.observe(this) { pairId ->
            updateLedGrid(pairId)
        }
        
        // 오류 메시지 관찰
        viewModel.errorMessage.observe(this) { error ->
            if (error.isNotEmpty()) {
                showToast("오류: $error")
            }
        }
        
        // 상태 메시지 관찰
        viewModel.statusMessage.observe(this) { status ->
            binding.textStatus.text = "상태: $status"
        }
    }
    
    /**
     * LED 그리드 UI 설정
     */
    private fun setupLedGrid() {
        // 36쌍 LED를 시각적으로 표현하는 그리드 설정
        // 동심원 형태: 내경 12개, 중간 12개, 외곽 12개
        
        // LED 상태를 표시할 뷰들을 동적으로 생성
        for (i in 0 until 36) {
            // LED 쌍별 뷰 생성 및 클릭 리스너 설정
            // 실제 구현에서는 커스텀 뷰나 RecyclerView 사용 권장
        }
    }
    
    /**
     * 연결 상태에 따른 UI 업데이트
     */
    private fun updateConnectionUI(state: BluetoothManager.ConnectionState) {
        binding.apply {
            when (state) {
                BluetoothManager.ConnectionState.NOT_SUPPORTED -> {
                    btnConnect.text = "Bluetooth 미지원"
                    btnConnect.isEnabled = false
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
                }
                BluetoothManager.ConnectionState.DISABLED -> {
                    btnConnect.text = "Bluetooth 활성화 필요"
                    btnConnect.isEnabled = true
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_warning))
                }
                BluetoothManager.ConnectionState.DISCONNECTED -> {
                    btnConnect.text = "연결"
                    btnConnect.isEnabled = true
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_disconnected))
                }
                BluetoothManager.ConnectionState.DEVICE_NOT_FOUND -> {
                    btnConnect.text = "장치 찾기 실패"
                    btnConnect.isEnabled = true
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
                }
                BluetoothManager.ConnectionState.CONNECTING -> {
                    btnConnect.text = "연결 중..."
                    btnConnect.isEnabled = false
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_connecting))
                }
                BluetoothManager.ConnectionState.CONNECTED -> {
                    btnConnect.text = "연결 해제"
                    btnConnect.isEnabled = true
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_connected))
                }
                BluetoothManager.ConnectionState.CONNECTION_LOST -> {
                    btnConnect.text = "재연결"
                    btnConnect.isEnabled = true
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
                }
            }
            
            // 시퀀스 제어 버튼은 연결된 경우에만 활성화
            btnStartSequence.isEnabled = (state == BluetoothManager.ConnectionState.CONNECTED)
        }
    }
    
    /**
     * LED 그리드 상태 업데이트
     */
    private fun updateLedGrid(currentPairId: Int?) {
        // 모든 LED 표시등을 끄고 현재 점등 중인 LED만 표시
        // 실제 구현에서는 LED 뷰들의 상태를 업데이트
        binding.textCurrentLed.text = if (currentPairId != null) {
            "현재 LED: ${currentPairId + 1}"
        } else {
            "LED 없음"
        }
    }
    
    /**
     * 권한 확인 및 Bluetooth 초기화
     */
    private fun checkPermissionsAndInitialize() {
        if (EasyPermissions.hasPermissions(this, *REQUIRED_PERMISSIONS)) {
            initializeBluetooth()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "LED Vision Tester는 Bluetooth 통신을 위해 다음 권한이 필요합니다:",
                BLUETOOTH_PERMISSION_REQUEST,
                *REQUIRED_PERMISSIONS
            )
        }
    }
    
    /**
     * Bluetooth 초기화
     */
    private fun initializeBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter?.isEnabled != true) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            viewModel.initializeBluetooth()
        }
    }
    
    /**
     * 권한 요청 결과 처리
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }
    
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            initializeBluetooth()
        }
    }
    
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            showToast("Bluetooth 권한이 필요합니다")
            finish()
        }
    }
    
    /**
     * 토스트 메시지 표시
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}