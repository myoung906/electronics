#!/bin/bash

# Terminal.app 기반 Claude 메시지 전송 스크립트
# tmux 대신 AppleScript를 사용하여 터미널 탭 제어

# 사용법: ./send-claude-message-terminal.sh <tab_name> "message"

if [ "$#" -ne 2 ]; then
    echo "사용법: $0 <tab_name> \"message\""
    echo "예시: $0 \"Claude-Agent\" \"안녕하세요!\""
    exit 1
fi

TAB_NAME="$1"
MESSAGE="$2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# AppleScript를 사용하여 메시지 전송
osascript "$SCRIPT_DIR/terminal-control.applescript" "send-claude" "$TAB_NAME" "$MESSAGE"

if [ $? -eq 0 ]; then
    echo "✅ 메시지 전송 완료: $TAB_NAME"
    echo "📝 메시지: $MESSAGE"
else
    echo "❌ 메시지 전송 실패: $TAB_NAME"
    echo "탭이 존재하는지 확인하세요."
fi