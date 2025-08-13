package com.ledvision.tester

import android.app.Application
import timber.log.Timber

/**
 * LED Vision Tester 애플리케이션 클래스
 */
class LEDVisionApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Timber 초기화 (디버그 로깅)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("LED Vision Tester Application 시작")
    }
}