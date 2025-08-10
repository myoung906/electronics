# 변경 이력 (CHANGELOG)

모든 주요 변경사항은 이 파일에 기록됩니다.

이 프로젝트는 [Semantic Versioning](https://semver.org/) 형식을 따릅니다.

## [Unreleased]
### 계획된 기능
- 첫 번째 하드웨어 프로젝트 템플릿
- PCB 설계 라이브러리 구축
- 자동화 테스트 환경

## [1.0.0] - 2025-08-10
### ✨ 추가된 기능
- **프로젝트 초기 설정**: GitHub 저장소 연동 및 기본 구조 구축
- **AI 오케스트레이터 시스템**: Terminal.app 기반 다중 에이전트 관리
- **DBpia MCP 통합**: 한국 학술 데이터베이스 검색 기능
- **Claude Code 가이드**: 개발환경 설정 및 사용법 문서화

### 📁 디렉토리 구조
```
electronics/
├── README.md                   # 프로젝트 개요
├── CLAUDE.md                  # Claude Code 설정
├── CHANGELOG.md               # 변경 이력 (이 파일)
├── .gitignore                 # Git 무시 파일 설정
├── package.json               # Node.js 프로젝트 설정
├── Tmux-Orchestrator/         # AI 에이전트 오케스트레이터
├── mcp_dbpia/                 # DBpia 검색 MCP 서버
└── mcp_server/                # 기본 MCP 서버 설정
```

### 🛠️ 기술 스택
- **OS**: macOS 10.15.7 (Catalina)
- **Runtime**: Node.js v20+
- **AI**: Claude Code CLI
- **도구**: Terminal.app, AppleScript
- **버전관리**: Git, GitHub

### 🔧 주요 설정
- GitHub 원격 저장소 연결: `https://github.com/myoung906/electronics`
- Git 워크플로우: 커밋 메시지 표준화 ([feat], [fix], [docs] 등)
- MCP 서버: DBpia 학술검색, 메모리 관리
- AI 에이전트: 하드웨어, 펌웨어, 테스트 팀 역할 분담

### 📝 문서화
- **README.md**: 프로젝트 전체 개요 및 사용법
- **CLAUDE.md**: GitHub 연동 가이드 및 개발 워크플로우
- **Tmux-Orchestrator/README.md**: AI 오케스트레이터 사용법
- **TERMINAL-GUIDE.md**: 터미널 기반 협업 가이드

### ⚙️ 자동화 기능
- 정기 체크인 스케줄링 (`schedule_with_note-terminal.sh`)
- 팀 간 메시지 전송 (`send-claude-message-terminal.sh`)
- 세션 관리 (`terminal-session-manager.sh`)
- AppleScript 터미널 제어 (`terminal-control.applescript`)

### 🎯 프로젝트 목표
1. **토큰 소모 최적화**: GitHub 기반 진행상황 추적
2. **체계적 개발**: 단계별 워크플로우 구축  
3. **팀 협업**: AI 에이전트 기반 자동화
4. **문서화**: 지식 축적 및 재사용성

---

## 변경사항 기록 형식 가이드

### 변경 유형
- **✨ 추가된 기능**: 새로운 기능
- **🐛 버그 수정**: 버그 수정
- **📝 문서**: 문서 변경
- **💄 스타일**: 코드 포맷팅
- **♻️ 리팩토링**: 코드 리팩토링
- **⚡ 성능**: 성능 개선
- **✅ 테스트**: 테스트 추가/수정
- **🔧 도구**: 빌드/도구 변경
- **🚀 배포**: 배포 관련
- **⚠️ 중단**: 향후 삭제될 기능

### 커밋 메시지 규칙
```
[타입] 간단한 설명

더 자세한 설명 (필요시)

관련 이슈: #123
```

---

**마지막 업데이트**: 2025-08-10  
**현재 버전**: v1.0.0  
**다음 버전 계획**: v1.1.0 (첫 번째 하드웨어 프로젝트)