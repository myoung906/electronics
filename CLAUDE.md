# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어 설정

**중요**: 모든 답변과 상호작용은 반드시 한국어로 진행해야 합니다. Claude Code는 이 워크스페이스에서 항상 한국어로 응답해야 합니다.

## GitHub 연동 지침

### 저장소 정보
- **GitHub URL**: https://github.com/myoung906/electronics
- **목적**: 토큰 소모 최적화 및 진행 과정 관리
- **브랜치**: main

### 세션 시작 시 필수 작업
**⚠️ 중요**: Claude Code를 새로 시작할 때마다 다음 작업을 반드시 수행해야 합니다:

1. **GitHub 저장소 확인**: https://github.com/myoung906/electronics 방문
2. **최신 커밋 내역 확인**: 이전 작업 진행 상황 파악
3. **README.md 검토**: 프로젝트 현재 상태 및 다음 단계 확인
4. **이슈 및 TODO 확인**: 진행 중인 작업 및 계획된 작업 파악

### Git 워크플로우

#### 작업 시작 전
```bash
# 현재 상태 확인
git status
git log --oneline -5

# 최신 변경사항 가져오기 (필요시)
git pull origin main
```

#### 작업 중 커밋 규칙
```bash
# 변경사항 스테이징
git add .

# 커밋 메시지 규칙: [타입] 간단한 설명
# 타입: feat, fix, docs, style, refactor, test, chore
git commit -m "[feat] 새로운 기능 추가"
git commit -m "[docs] 문서 업데이트"
git commit -m "[fix] 버그 수정"

# 원격 저장소에 푸시
git push origin main
```

#### 커밋 메시지 가이드라인
- **[feat]**: 새로운 기능 추가
- **[fix]**: 버그 수정
- **[docs]**: 문서 변경
- **[style]**: 코드 포맷팅, 세미콜론 누락 등
- **[refactor]**: 코드 리팩토링
- **[test]**: 테스트 코드 추가/수정
- **[chore]**: 빌드 프로세스 또는 보조 도구 변경

### 파일 생성 및 수정 시 주의사항

1. **모든 작업 내용 커밋**: 생성된 모든 파일과 수정 사항을 즉시 커밋
2. **의미 있는 커밋**: 논리적 단위로 커밋 분할
3. **진행 상황 문서화**: README.md 또는 별도 진행 보고서 업데이트
4. **백업 목적**: 중요한 설계 파일이나 코드는 반드시 커밋하여 백업

### 프로젝트 상태 추적

#### README.md 업데이트 규칙
- **현재 진행 상황**: 완료된 작업 목록
- **다음 계획**: 예정된 작업 및 우선순위
- **문제점 및 해결책**: 발생한 이슈와 해결 방안
- **참고사항**: 중요한 결정사항이나 변경사항

#### 디렉토리별 진행 상황
```
electronics/
├── README.md              # 📍 전체 프로젝트 진행 상황
├── CHANGELOG.md           # 📍 변경 이력 기록
├── hardware/
│   └── README.md          # 📍 하드웨어 설계 진행 상황
├── firmware/
│   └── README.md          # 📍 펌웨어 개발 진행 상황
└── docs/
    └── progress_log.md    # 📍 상세 작업 로그
```

## 프로젝트 개요

이 디렉터리는 전자기기 시제품 개발을 위한 전용 워크스페이스입니다. 하드웨어 설계, 임베디드 소프트웨어, PCB 설계, 시뮬레이션 및 테스트 관련 프로젝트를 포함합니다.

## 주요 개발 영역

### 하드웨어 설계
- 회로 설계 및 시뮬레이션 (SPICE, LTspice, KiCad)
- PCB 레이아웃 설계
- 부품 선정 및 BOM(Bill of Materials) 관리
- 3D 모델링 및 기구 설계

### 임베디드 소프트웨어
- 마이크로컨트롤러 프로그래밍 (Arduino, STM32, ESP32 등)
- 펌웨어 개발 및 최적화
- 센서 데이터 처리 및 통신 프로토콜
- 실시간 시스템 구현

### 시제품 검증
- 기능 테스트 및 성능 평가
- EMC/EMI 테스트 준비
- 환경 테스트 (온도, 습도, 진동)
- 사용자 테스트 및 피드백 수집

## 개발 도구 및 환경

### 설계 도구
- **회로 설계**: KiCad, Altium Designer, Eagle
- **시뮬레이션**: LTspice, MATLAB/Simulink, Proteus
- **3D 설계**: Fusion 360, SolidWorks

### 개발 환경
- **IDE**: Arduino IDE, PlatformIO, STM32CubeIDE, Keil
- **컴파일러**: GCC, ARM-GCC
- **디버깅**: JTAG/SWD 디버거, 로직 분석기

### 측정 장비
- 오실로스코프, 멀티미터, 함수 발생기
- 스펙트럼 분석기, 네트워크 분석기
- 3D 프린터, 리플로우 오븐

## 프로젝트 구조

```
electronics/
├── hardware/          # 하드웨어 설계 파일
│   ├── schematics/    # 회로도
│   ├── pcb/           # PCB 레이아웃
│   └── 3d_models/     # 3D 모델 파일
├── firmware/          # 임베디드 소프트웨어
│   ├── src/           # 소스 코드
│   ├── lib/           # 라이브러리
│   └── tests/         # 테스트 코드
├── simulation/        # 시뮬레이션 파일
├── docs/              # 설계 문서 및 매뉴얼
│   ├── datasheets/    # 부품 데이터시트
│   ├── specifications/ # 제품 사양서
│   └── test_reports/  # 테스트 보고서
└── prototypes/        # 시제품 버전 관리
```

## 개발 워크플로우

### 1. 설계 단계
- 요구사항 분석 및 사양 정의
- 블록 다이어그램 작성
- 회로 설계 및 시뮬레이션
- PCB 레이아웃 설계

### 2. 제작 단계
- BOM 생성 및 부품 주문
- PCB 제작 및 조립
- 초기 기능 테스트

### 3. 검증 단계
- 단위 테스트 및 통합 테스트
- 성능 평가 및 최적화
- 환경 테스트 및 신뢰성 검증

### 4. 문서화
- 설계 문서 작성
- 사용자 매뉴얼 제작
- 테스트 보고서 작성

## 파일 명명 규칙

### 하드웨어 파일
- 회로도: `[프로젝트명]_schematic_v[버전].sch`
- PCB: `[프로젝트명]_pcb_v[버전].kicad_pcb`
- BOM: `[프로젝트명]_BOM_v[버전].xlsx`

### 소프트웨어 파일
- 메인 파일: `main.c` 또는 `main.cpp`
- 헤더 파일: `[모듈명].h`
- 소스 파일: `[모듈명].c` 또는 `[모듈명].cpp`

### 문서 파일
- 사양서: `[프로젝트명]_specification_v[버전].md`
- 테스트 결과: `[프로젝트명]_test_report_[날짜].md`

## 버전 관리

- 하드웨어 버전: v1.0, v1.1, v2.0 (주요 변경 시 주 버전 증가)
- 소프트웨어 버전: 시맨틱 버저닝 (x.y.z) 사용
- 시제품 버전: P1, P2, P3 (Prototype 1, 2, 3)

## 안전 및 규정 준수

- 전기 안전 규정 (KS, IEC, UL) 준수
- EMC/EMI 규정 고려
- RoHS, REACH 등 환경 규정 준수
- 작업 안전 수칙 준수 (정전기 방지, 화학물질 취급 등)



## Playwright MCP 설치 지침

### 문제 상황
macOS 10.15.7 (Catalina) 환경에서 Playwright MCP 사용 시 다음과 같은 오류가 발생할 수 있습니다:
- `Executable doesn't exist at /Users/[username]/Library/Caches/ms-playwright/[browser]-[version]/...`
- 브라우저 버전 불일치 문제
- NVM 환경에서 경로 문제

### 해결된 설치 방법 (검증 완료)

#### 1단계: 기존 Playwright 캐시 정리
```bash
# 기존 브라우저 캐시 완전 삭제
rm -rf /Users/[username]/Library/Caches/ms-playwright
```

#### 2단계: Playwright MCP 글로벌 설치
```bash
# MCP 패키지 글로벌 설치
npm install -g @playwright/mcp

# 필요한 브라우저 설치
npx playwright install
npx playwright install chromium
npx playwright install firefox
```

#### 3단계: MCP 설정 파일 구성
프로젝트의 `.mcp.json` 파일에 다음과 같이 설정:

```json
{
  "mcpServers": {
    "playwright": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y",
        "@playwright/mcp",
        "--browser",
        "chromium"
      ]
    }
  }
}
```

#### 4단계: 버전 불일치 문제 해결 (필요시)
만약 여전히 버전 불일치 오류가 발생한다면 심볼릭 링크로 해결:

```bash
# Firefox 버전 불일치 해결 예시
ln -sf /Users/[username]/Library/Caches/ms-playwright/firefox-[설치된버전] \
       /Users/[username]/Library/Caches/ms-playwright/firefox-[요구버전]

# Chromium 버전 불일치 해결 예시  
ln -sf /Users/[username]/Library/Caches/ms-playwright/chromium-[설치된버전] \
       /Users/[username]/Library/Caches/ms-playwright/chromium-[요구버전]

# WebKit 버전 불일치 해결 예시
ln -sf /Users/[username]/Library/Caches/ms-playwright/webkit-[설치된버전] \
       /Users/[username]/Library/Caches/ms-playwright/webkit_mac10.15_special-[요구버전]
```

#### 5단계: 연결 상태 확인
```bash
# MCP 서버 목록 및 연결 상태 확인
claude mcp list
```

#### 6단계: 테스트 실행
브라우저별 테스트 명령어:
- **Chromium (가장 안정적)**: `browserType="chromium", headless=false`
- **Firefox**: `browserType="firefox", headless=true`  
- **WebKit**: `browserType="webkit", headless=true`

### 브라우저별 호환성 (macOS Catalina 기준)

| 브라우저 | 호환성 | 권장도 | 비고 |
|---------|--------|--------|------|
| **Chromium** | ✅ 우수 | ⭐⭐⭐ | 가장 안정적, headless=false 권장 |
| **Firefox** | ⚠️ 보통 | ⭐⭐ | 프로토콜 오류 가능성, headless=true 권장 |  
| **WebKit** | ⚠️ 보통 | ⭐ | 프로토콜 오류 가능성, 테스트 필요 |

### 주요 해결 포인트
1. **브라우저 선택**: Chromium이 macOS Catalina에서 가장 안정적
2. **버전 동기화**: 심볼릭 링크를 활용한 버전 불일치 해결
3. **헤드리스 모드**: 브라우저별로 적절한 headless 설정 적용
4. **캐시 관리**: 정기적인 캐시 정리로 버전 충돌 방지

### 검증된 작동 환경
- **OS**: macOS 10.15.7 (Catalina)
- **Node.js**: v20.19.3 (NVM 관리)
- **성공 브라우저**: Chromium (headless=false)
- **MCP 버전**: @playwright/mcp 최신 버전

### 실제 성공 사례
2025-07-29에 다음 환경에서 성공적으로 작동 확인:
- KAIST Brain Lab 웹사이트 접속 성공
- 스크린샷 촬영 완료
- 모든 브라우저 자동화 기능 정상 작동