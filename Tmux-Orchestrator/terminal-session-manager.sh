#!/bin/bash

# Terminal.app 기반 세션 관리 스크립트
# tmux 세션 관리 기능을 Terminal.app 탭으로 시뮬레이션

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 도움말 표시
show_help() {
    cat << EOF
Terminal Session Manager - tmux 대안 (Terminal.app 기반)

사용법:
    $0 new-session <session_name>           새 세션(탭) 생성
    $0 new-window <session_name> <window_name>  새 윈도우(탭) 생성
    $0 list-sessions                        모든 탭 목록 표시
    $0 send-keys <tab_name> "<command>"     특정 탭에 명령어 전송
    $0 send-claude <tab_name> "<message>"   Claude에게 메시지 전송
    $0 capture <tab_name> [lines]           탭 내용 캡처
    $0 kill-session <tab_name>              탭 닫기
    
예시:
    $0 new-session "my-project"
    $0 send-claude "Claude-Agent" "안녕하세요!"
    $0 capture "Claude-Agent" 20
    
EOF
}

# 새 세션 생성
create_session() {
    local session_name="$1"
    if [ -z "$session_name" ]; then
        echo "❌ 세션 이름을 입력하세요"
        exit 1
    fi
    
    echo "🚀 새 세션 생성 중: $session_name"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "new-session" "$session_name"
    echo "✅ 세션 생성 완료: $session_name"
}

# 새 윈도우(탭) 생성
create_window() {
    local session_name="$1"
    local window_name="$2"
    
    if [ -z "$window_name" ]; then
        window_name="$session_name-window"
    fi
    
    echo "📝 새 윈도우 생성 중: $window_name"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "new-tab" "$window_name"
    echo "✅ 윈도우 생성 완료: $window_name"
}

# 세션 목록 표시
list_sessions() {
    echo "📋 현재 활성 탭 목록:"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "list-tabs"
}

# 명령어 전송
send_keys() {
    local tab_name="$1"
    local command="$2"
    
    if [ -z "$tab_name" ] || [ -z "$command" ]; then
        echo "❌ 탭 이름과 명령어를 모두 입력하세요"
        exit 1
    fi
    
    echo "📤 명령어 전송 중: $tab_name"
    echo "💬 명령어: $command"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "send-keys" "$tab_name" "$command"
}

# Claude 메시지 전송
send_claude() {
    local tab_name="$1"
    local message="$2"
    
    if [ -z "$tab_name" ] || [ -z "$message" ]; then
        echo "❌ 탭 이름과 메시지를 모두 입력하세요"
        exit 1
    fi
    
    echo "🤖 Claude 메시지 전송 중: $tab_name"
    echo "💬 메시지: $message"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "send-claude" "$tab_name" "$message"
}

# 탭 내용 캡처
capture_output() {
    local tab_name="$1"
    local lines="${2:-20}"
    
    if [ -z "$tab_name" ]; then
        echo "❌ 탭 이름을 입력하세요"
        exit 1
    fi
    
    echo "📸 탭 내용 캡처 중: $tab_name (최근 $lines줄)"
    osascript "$SCRIPT_DIR/terminal-control.applescript" "capture" "$tab_name" "$lines"
}

# 탭 닫기 (수동으로 해야 함)
kill_session() {
    local tab_name="$1"
    
    if [ -z "$tab_name" ]; then
        echo "❌ 탭 이름을 입력하세요"
        exit 1
    fi
    
    echo "⚠️  탭 '$tab_name'을 수동으로 닫아주세요"
    echo "💡 Command+W를 사용하거나 터미널에서 'exit'를 입력하세요"
}

# 메인 로직
case "$1" in
    "new-session")
        create_session "$2"
        ;;
    "new-window")
        create_window "$2" "$3"
        ;;
    "list-sessions"|"list")
        list_sessions
        ;;
    "send-keys")
        send_keys "$2" "$3"
        ;;
    "send-claude")
        send_claude "$2" "$3"
        ;;
    "capture")
        capture_output "$2" "$3"
        ;;
    "kill-session")
        kill_session "$2"
        ;;
    "help"|"-h"|"--help"|"")
        show_help
        ;;
    *)
        echo "❌ 알 수 없는 명령어: $1"
        echo ""
        show_help
        exit 1
        ;;
esac