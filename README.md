# 🔌 LED Vision Tester

36쌍 LED 시각검사 장비 - **즉시 제작 가능**

## 🚀 빠른 시작

### 파일 구조
```
led_vision_tester/
├── hardware/
│   ├── schematics/led_vision_tester_complete_v6.kicad_sch  # KiCad 회로도
│   └── bom/8pair_led_prototype_bom.md                     # 부품 목록
├── firmware/                                              # ESP32 코드
│   ├── src/main.cpp
│   └── platformio.ini
└── android_app/                                           # 안드로이드 앱
```

### 제작 순서
1. **부품 구매** (55,000원) → `bom/8pair_led_prototype_bom.md`
2. **회로 조립** → KiCad 회로도 참조
3. **펌웨어 업로드** → PlatformIO 사용

## 🔧 주요 사양

- **컨트롤러**: ESP32-WROOM-32
- **LED**: 72개 (36쌍) 빨강/초록
- **드라이버**: 74HC595 + ULN2803A
- **전원**: 5V 2A
- **통신**: WiFi/Bluetooth

## 📊 완성도

- [x] 회로 설계 완료
- [x] BOM 작성 완료
- [x] ESP32 펌웨어 완료
- [x] 안드로이드 앱 MVP
- [ ] PCB 레이아웃 (선택사항)

---

**상태**: ✅ 제작 준비 완료  
**업데이트**: 2025-08-13