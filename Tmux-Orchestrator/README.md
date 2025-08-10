# 전자기기 개발용 Terminal.app Orchestrator

## 📋 개요

macOS Catalina 환경에서 tmux 설치 제약으로 인해 Terminal.app과 AppleScript를 활용하여 구현한 전자기기 개발 전용 AI 에이전트 오케스트레이터 시스템입니다.

## 🎯 주요 기능

- **다중 팀 관리**: 하드웨어, 펌웨어, 테스트 팀 간 협업 조율
- **자동화된 스케줄링**: 정기 체크인 및 작업 알림
- **실시간 통신**: 팀 간 메시지 전송 및 상태 공유
- **프로젝트 추적**: 개발 진행률 및 품질 지표 모니터링

## 🚀 빠른 시작

### 1. 시스템 테스트
```bash
cd /Users/workspace/electronics/Tmux-Orchestrator
./terminal-session-manager.sh help
```

### 2. 개발팀 세션 생성
```bash
# 오케스트레이터
./terminal-session-manager.sh new-session "Orchestrator"

# 각 개발팀
./terminal-session-manager.sh new-session "HW-Team"
./terminal-session-manager.sh new-session "FW-Team"  
./terminal-session-manager.sh new-session "Test-Team"
```

### 3. Claude 에이전트 시작 및 역할 배정
```bash
# Claude 시작
./terminal-session-manager.sh send-keys "HW-Team" "cd /Users/workspace/electronics && claude"

# 역할 브리핑
./terminal-session-manager.sh send-claude "HW-Team" "당신은 하드웨어 엔지니어입니다. 회로 설계와 PCB 개발을 담당합니다."
```

## 🛠 핵심 스크립트

### terminal-session-manager.sh
메인 세션 관리 스크립트
```bash
./terminal-session-manager.sh new-session "프로젝트명"
./terminal-session-manager.sh send-claude "팀명" "메시지"
./terminal-session-manager.sh capture "팀명" 20
```

### send-claude-message-terminal.sh
Claude 에이전트 메시지 전송
```bash
./send-claude-message-terminal.sh "팀명" "메시지 내용"
```

### schedule_with_note-terminal.sh
자동화된 스케줄링
```bash
./schedule_with_note-terminal.sh 30 "체크인 메시지" "대상팀"
```

### terminal-control.applescript
AppleScript 기반 터미널 제어 라이브러리

## 📚 사용법 가이드

### 전자기기 개발 워크플로우
1. **설계 단계**: 요구사항 → 회로설계 → 시뮬레이션 → PCB설계
2. **개발 단계**: 펌웨어 구현 → 하드웨어 제작 → 통합
3. **검증 단계**: 기능테스트 → 성능테스트 → 환경테스트 → 인증

### 팀 간 협업 예시
```bash
# 설계 변경 알림
./send-claude-message-terminal.sh "FW-Team" "GPIO 핀 배치가 변경되었습니다. 펌웨어 수정이 필요합니다."

# 테스트 결과 공유  
./send-claude-message-terminal.sh "HW-Team" "온도 테스트에서 85°C에서 불안정. 방열 설계 개선 필요."

# 정기 체크인 스케줄
./schedule_with_note-terminal.sh 1440 "일일 스탠드업 미팅" "Orchestrator"
```

## 📊 전자기기 개발 특화 기능

### 품질 관리
- DRC 검사 자동화
- 코드 리뷰 추적
- 테스트 커버리지 모니터링

### 규정 준수
- 전기 안전 규정 (KS, IEC, UL)
- EMC/EMI 테스트 관리
- 환경 규정 (RoHS, REACH)

### 개발 도구 통합
- KiCad, Altium Designer
- Arduino IDE, PlatformIO, STM32CubeIDE
- LTspice, MATLAB/Simulink

## 📁 파일 구조

```
Tmux-Orchestrator/
├── README.md                           # 이 파일
├── CLAUDE.md                          # 에이전트 행동 가이드
├── TERMINAL-GUIDE.md                  # 상세 사용법 가이드
├── terminal-control.applescript       # AppleScript 제어 라이브러리
├── terminal-session-manager.sh        # 메인 세션 관리
├── send-claude-message-terminal.sh    # Claude 메시지 전송
└── schedule_with_note-terminal.sh     # 스케줄링 기능
```

## ⚠️ 시스템 요구사항

- **OS**: macOS Catalina 10.15.7 이상
- **터미널**: Terminal.app
- **권한**: AppleScript 실행 권한 (접근성 설정)
- **도구**: Claude Code CLI

## 🔧 설정 및 권한

### AppleScript 권한 설정
1. 시스템 환경설정 → 보안 및 개인정보보호
2. 개인정보보호 → 접근성
3. Terminal.app에 접근성 권한 부여

### 실행 권한 확인
```bash
ls -la *.sh *.applescript
# 모든 스크립트 파일이 실행 권한(x)을 가져야 함
```

## 🎓 고급 사용법

### 다중 프로젝트 관리
```bash
# 프로젝트별 세션 생성
./terminal-session-manager.sh new-session "IoT-Sensor-HW"
./terminal-session-manager.sh new-session "Smart-Display-FW"
./terminal-session-manager.sh new-session "Drone-Controller-Test"
```

### 크로스 팀 인텔리전스
```bash
# 프로젝트 간 지식 공유
./send-claude-message-terminal.sh "Smart-Display-FW" "IoT 센서 프로젝트의 전력 관리 기법을 참고하세요."
```

### 자동화된 보고
```bash
# 주간 진행 보고
./schedule_with_note-terminal.sh 10080 "주간 프로젝트 진행률 보고서 작성" "Orchestrator"
```

## 🚨 문제 해결

### 자주 발생하는 문제
1. **탭을 찾을 수 없음**: 탭 이름 정확성 확인
2. **AppleScript 권한 오류**: 접근성 권한 설정 확인
3. **스케줄 미작동**: `at` 명령어 또는 `sleep` 가용성 확인

### 디버깅 명령어
```bash
# 현재 활성 탭 목록
./terminal-session-manager.sh list-sessions

# 백그라운드 프로세스 확인
ps aux | grep sleep
```

## 📈 성과 측정

### KPI 지표
- 설계 완료율 (회로도, PCB, 3D 모델)
- 코딩 완료율 (모듈별, 기능별)
- 테스트 통과율 (기능, 성능, 환경)
- 품질 지표 (결함 밀도, 커버리지)

### 일정 관리
- 마일스톤 달성률
- 지연 발생 빈도  
- 리소스 활용률

## 🤝 기여 및 개선

이 시스템은 전자기기 개발팀의 실제 사용 경험을 바탕으로 지속적으로 개선됩니다:

1. 새로운 개발 도구 통합 패턴 문서화
2. 팀 간 협업 베스트 프랙티스 공유
3. 자동화 워크플로우 최적화
4. 전자기기 특화 기능 추가

## 📄 라이선스

MIT License - 자유롭게 사용하되 현명하게 사용하세요.

---

**마지막 업데이트**: 2025-08-03  
**환경**: macOS Catalina 10.15.7, Terminal.app  
**버전**: v1.0 (전자기기 개발 특화)

*"오늘 우리가 만드는 도구들이 내일 스스로를 프로그래밍할 것이다"* - Alan Kay, 1971