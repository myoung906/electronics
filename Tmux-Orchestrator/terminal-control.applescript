#!/usr/bin/osascript

-- Terminal.app 제어를 위한 AppleScript 라이브러리
-- tmux 명령어를 Terminal.app 탭 제어로 변환

-- 새 터미널 탭 생성
on createNewTab(sessionName)
    tell application "Terminal"
        activate
        tell application "System Events"
            keystroke "t" using command down
        end tell
        delay 0.5
        
        -- 현재 탭을 작업 디렉토리로 이동
        do script "cd /Users/workspace/optom_research" in front window
        delay 0.2
        
        -- 탭 제목 설정
        set custom title of front tab of front window to sessionName
        
        return front tab of front window
    end tell
end createNewTab

-- 특정 탭에 명령어 전송
on sendCommandToTab(tabReference, command)
    tell application "Terminal"
        do script command in tabReference
    end tell
end sendCommandToTab

-- 탭 목록 가져오기
on getTabList()
    tell application "Terminal"
        set tabList to {}
        repeat with i from 1 to count of tabs of front window
            set tabTitle to custom title of tab i of front window
            set end of tabList to {index:i, title:tabTitle}
        end repeat
        return tabList
    end tell
end getTabList

-- 탭 이름으로 탭 찾기
on findTabByName(tabName)
    tell application "Terminal"
        repeat with i from 1 to count of tabs of front window
            if custom title of tab i of front window is tabName then
                return tab i of front window
            end if
        end repeat
        return missing value
    end tell
end findTabByName

-- 터미널 창 내용 캡처 (최근 라인들)
on captureTabOutput(tabReference, lineCount)
    tell application "Terminal"
        set tabContent to contents of tabReference
        set lineList to paragraphs of tabContent
        set totalLines to count of lineList
        
        if totalLines > lineCount then
            set startLine to totalLines - lineCount + 1
            set recentLines to items startLine through totalLines of lineList
        else
            set recentLines to lineList
        end if
        
        return recentLines as string
    end tell
end captureTabOutput

-- Claude 메시지 전송 (0.5초 딜레이 포함)
on sendClaudeMessage(tabReference, message)
    tell application "Terminal"
        do script message in tabReference
        delay 0.5
        do script "" in tabReference -- Enter 키
    end tell
end sendClaudeMessage

-- 세션 생성 (tmux new-session 대응)
on createSession(sessionName)
    set newTab to createNewTab(sessionName)
    return newTab
end createSession

-- 윈도우 생성 (tmux new-window 대응)
on createWindow(sessionName, windowName)
    set newTab to createNewTab(windowName)
    return newTab
end createWindow

-- 메인 실행 부분
on run argv
    if (count of argv) is 0 then
        display dialog "사용법: osascript terminal-control.applescript [command] [args...]"
        return
    end if
    
    set command to item 1 of argv
    
    if command is "new-session" then
        if (count of argv) ≥ 2 then
            set sessionName to item 2 of argv
            createSession(sessionName)
        else
            createSession("default")
        end if
        
    else if command is "new-tab" then
        if (count of argv) ≥ 2 then
            set tabName to item 2 of argv
            createNewTab(tabName)
        else
            createNewTab("new-tab")
        end if
        
    else if command is "send-keys" then
        if (count of argv) ≥ 3 then
            set tabName to item 2 of argv
            set message to item 3 of argv
            set targetTab to findTabByName(tabName)
            if targetTab is not missing value then
                sendCommandToTab(targetTab, message)
            else
                display dialog "탭을 찾을 수 없습니다: " & tabName
            end if
        end if
        
    else if command is "send-claude" then
        if (count of argv) ≥ 3 then
            set tabName to item 2 of argv
            set message to item 3 of argv
            set targetTab to findTabByName(tabName)
            if targetTab is not missing value then
                sendClaudeMessage(targetTab, message)
            else
                display dialog "탭을 찾을 수 없습니다: " & tabName
            end if
        end if
        
    else if command is "list-tabs" then
        set tabList to getTabList()
        repeat with tabInfo in tabList
            log "Tab " & (index of tabInfo) & ": " & (title of tabInfo)
        end repeat
        
    else if command is "capture" then
        if (count of argv) ≥ 2 then
            set tabName to item 2 of argv
            set lineCount to 20
            if (count of argv) ≥ 3 then
                set lineCount to (item 3 of argv) as integer
            end if
            
            set targetTab to findTabByName(tabName)
            if targetTab is not missing value then
                set output to captureTabOutput(targetTab, lineCount)
                return output
            else
                display dialog "탭을 찾을 수 없습니다: " & tabName
            end if
        end if
        
    else
        display dialog "알 수 없는 명령어: " & command
    end if
end run