#!/bin/bash

# Terminal.app 기반 스케줄링 스크립트
# tmux 대신 터미널 탭을 사용한 버전

# 사용법: ./schedule_with_note-terminal.sh <minutes> "<note>" [target_tab]

if [ "$#" -lt 2 ]; then
    echo "사용법: $0 <minutes> \"<note>\" [target_tab]"
    echo "예시: $0 30 \"프로젝트 상태 확인\" \"Orchestrator\""
    exit 1
fi

MINUTES="$1"
NOTE="$2"
TARGET_TAB="${3:-Orchestrator}"  # 기본값: Orchestrator

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CURRENT_TIME=$(date "+%Y-%m-%d %H:%M:%S")

echo "⏰ 스케줄 설정 중..."
echo "📅 시간: ${MINUTES}분 후"
echo "📝 노트: $NOTE"
echo "🎯 대상 탭: $TARGET_TAB"

# at 명령어가 있는지 확인
if command -v at >/dev/null 2>&1; then
    # at 명령어 사용
    echo "osascript '$SCRIPT_DIR/terminal-control.applescript' 'send-claude' '$TARGET_TAB' '⏰ 스케줄된 체크인: $NOTE (예정시간: $CURRENT_TIME + ${MINUTES}분)'" | at now + ${MINUTES} minutes
    echo "✅ at 명령어로 스케줄 설정 완료"
elif command -v sleep >/dev/null 2>&1; then
    # 백그라운드에서 sleep 사용
    (
        sleep $((MINUTES * 60))
        osascript "$SCRIPT_DIR/terminal-control.applescript" "send-claude" "$TARGET_TAB" "⏰ 스케줄된 체크인: $NOTE (예정시간: $CURRENT_TIME + ${MINUTES}분)"
        echo "✅ 스케줄된 메시지 전송 완료: $TARGET_TAB" >> "$SCRIPT_DIR/schedule.log"
    ) &
    
    echo "✅ 백그라운드 스케줄 설정 완료 (PID: $!)"
    echo "$! $TARGET_TAB $NOTE" >> "$SCRIPT_DIR/schedule.log"
else
    echo "❌ 스케줄링 도구를 찾을 수 없습니다 (at 또는 sleep 필요)"
    exit 1
fi

echo "📊 현재 시간: $CURRENT_TIME"
echo "⏰ 실행 예정: $(date -v+${MINUTES}M "+%Y-%m-%d %H:%M:%S")"