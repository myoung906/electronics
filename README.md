# Electronics 전자기기 개발 프로젝트

## 📋 프로젝트 개요

전자기기 시제품 개발을 위한 통합 워크스페이스입니다. 하드웨어 설계, 임베디드 소프트웨어, PCB 설계, 시뮬레이션 및 테스트 관련 프로젝트를 체계적으로 관리합니다.

## 🏗️ 프로젝트 구조

```
electronics/
├── README.md                    # 📍 프로젝트 개요 (이 파일)
├── CLAUDE.md                   # 📍 Claude Code 설정 및 가이드
├── CHANGELOG.md                # 📍 변경 이력 기록
├── Tmux-Orchestrator/          # 🤖 AI 에이전트 오케스트레이터 시스템
├── mcp_dbpia/                  # 🔍 DBpia 학술 검색 MCP 서버
├── mcp_server/                 # ⚙️ 기본 MCP 서버 설정
├── hardware/                   # 🔧 하드웨어 설계 파일 (예정)
├── firmware/                   # 💾 임베디드 소프트웨어 (예정)
├── simulation/                 # 📊 시뮬레이션 파일 (예정)
├── docs/                       # 📚 설계 문서 및 매뉴얼 (예정)
└── prototypes/                 # 🔬 시제품 버전 관리 (예정)
```

## 🚀 주요 기능

### 🤖 AI 에이전트 오케스트레이터
- **위치**: `Tmux-Orchestrator/`
- **기능**: macOS Terminal.app 기반 다중 AI 에이전트 관리 시스템
- **특징**: 하드웨어, 펌웨어, 테스트 팀 간 협업 자동화

### 🔍 학술 검색 통합
- **위치**: `mcp_dbpia/`
- **기능**: DBpia 한국 학술 데이터베이스 검색 MCP 서버
- **용도**: 전자공학 관련 논문 및 연구 자료 검색

### 🔧 개발 도구 통합
- **회로 설계**: KiCad, Altium Designer, Eagle
- **시뮬레이션**: LTspice, MATLAB/Simulink, Proteus  
- **3D 설계**: Fusion 360, SolidWorks
- **개발 환경**: Arduino IDE, PlatformIO, STM32CubeIDE

## 📅 개발 워크플로우

### 1단계: 설계 단계
- [ ] 요구사항 분석 및 사양 정의
- [ ] 블록 다이어그램 작성
- [ ] 회로 설계 및 시뮬레이션
- [ ] PCB 레이아웃 설계

### 2단계: 제작 단계  
- [ ] BOM 생성 및 부품 주문
- [ ] PCB 제작 및 조립
- [ ] 초기 기능 테스트

### 3단계: 검증 단계
- [ ] 단위 테스트 및 통합 테스트
- [ ] 성능 평가 및 최적화
- [ ] 환경 테스트 및 신뢰성 검증

### 4단계: 문서화
- [ ] 설계 문서 작성
- [ ] 사용자 매뉴얼 제작
- [ ] 테스트 보고서 작성

## ⚙️ 시스템 요구사항

- **OS**: macOS 10.15.7 (Catalina) 이상
- **Node.js**: v20+ (NVM 권장)
- **개발 도구**: Claude Code CLI
- **권한**: AppleScript 실행 권한 (Terminal.app 접근성)

## 🛠️ 설치 및 설정

### 기본 환경 설정
```bash
# 저장소 클론
git clone https://github.com/myoung906/electronics.git
cd electronics

# Node.js 종속성 설치
npm install

# MCP 서버 설정
cd mcp_dbpia && npm install
cd ../mcp_server && npm install
```

### AI 오케스트레이터 설정
```bash
# Tmux-Orchestrator 디렉토리로 이동
cd Tmux-Orchestrator

# 실행 권한 설정
chmod +x *.sh *.applescript

# 시스템 테스트
./terminal-session-manager.sh help
```

## 📊 진행 상황

### ✅ 완료된 작업
- [x] GitHub 저장소 연동 설정
- [x] AI 오케스트레이터 시스템 구축
- [x] DBpia 학술 검색 MCP 통합
- [x] Claude Code 설정 가이드 작성
- [x] 프로젝트 구조 설계

### 🔄 진행 중인 작업
- [ ] 기본 하드웨어 설계 템플릿 구축
- [ ] 임베디드 개발 환경 설정
- [ ] 시뮬레이션 환경 구축

### 📋 계획된 작업
- [ ] 첫 번째 시제품 프로젝트 시작
- [ ] PCB 설계 라이브러리 구축
- [ ] 자동화 테스트 환경 구축
- [ ] 문서화 시스템 완성

## 🔍 사용법

### AI 오케스트레이터 시작
```bash
cd Tmux-Orchestrator

# 개발팀 세션 생성
./terminal-session-manager.sh new-session "HW-Team"
./terminal-session-manager.sh new-session "FW-Team"  
./terminal-session-manager.sh new-session "Test-Team"

# Claude 에이전트 역할 배정
./send-claude-message-terminal.sh "HW-Team" "하드웨어 엔지니어 역할을 맡아주세요."
```

### 학술 자료 검색
```bash
# DBpia MCP 서버 실행 (별도 터미널에서)
cd mcp_dbpia
node index.js

# Claude에서 사용
# mcp__dbpia-search__search_dbpia 도구로 검색 가능
```

## 🏆 성과 측정 지표

### 개발 효율성
- 설계 완료율: 0% (시작 단계)
- 코딩 완료율: 0% (시작 단계)  
- 테스트 통과율: 0% (시작 단계)

### 품질 지표
- 문서화 완성도: 80% (기본 구조 완성)
- 자동화 수준: 60% (AI 오케스트레이터 구축)
- 도구 통합도: 70% (MCP 서버 통합 완료)

## 📄 라이선스

MIT License - 교육 및 연구 목적으로 자유롭게 사용 가능

## 👥 기여자

- **프로젝트 관리**: myoung906
- **AI 오케스트레이터**: Claude Code
- **시스템 아키텍처**: 통합 설계 팀

---

**마지막 업데이트**: 2025-08-10  
**프로젝트 상태**: 🚀 초기 설정 완료, 개발 준비 단계  
**다음 마일스톤**: 첫 번째 하드웨어 프로젝트 시작

*"혁신은 체계적인 준비에서 시작된다"*