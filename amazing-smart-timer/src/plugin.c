#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif
#include <windows.h>
#include <shellapi.h>
#include <stdint.h>
#include <stdlib.h>
#include <wchar.h>
#include <stdio.h>
#include "timer.h"

#define MAX_TIMERS 12
#define HOTKEY_ID 1
#define TRAY_ID 1
#define WM_TRAY (WM_APP + 1)
#define ID_NAME 101
#define ID_MINUTES 102
#define ID_ADD 103
#define ID_LIST 104
#define ID_TOGGLE 105
#define ID_DELETE 106
#define ID_HIDE 107

static HINSTANCE instance;
static HWND name_edit, minutes_edit, list, info;
static SmartTimer timers[MAX_TIMERS];
static int timer_count, hotkey_ok, tray_ok;
static const wchar_t class_name[] = L"AmazingSmartTimerWindow_v1";

static HWND control(HWND parent, const wchar_t *klass, const wchar_t *text,
                    DWORD style, int x, int y, int w, int h, int id) {
    HWND c = CreateWindowExW(0, klass, text, WS_CHILD | WS_VISIBLE | style,
                             x, y, w, h, parent, (HMENU)(INT_PTR)id, instance, NULL);
    if (c) SendMessageW(c, WM_SETFONT, (WPARAM)GetStockObject(DEFAULT_GUI_FONT), TRUE);
    return c;
}
static void tray(HWND hwnd, DWORD operation, const wchar_t *message) {
    NOTIFYICONDATAW n;
    ZeroMemory(&n, sizeof(n));
    n.cbSize = sizeof(n); n.hWnd = hwnd; n.uID = TRAY_ID;
    if (operation == NIM_ADD) {
        n.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
        n.uCallbackMessage = WM_TRAY;
        n.hIcon = LoadIconW(NULL, IDI_INFORMATION);
        lstrcpynW(n.szTip, L"Amazing Smart Timer", 128);
        tray_ok = Shell_NotifyIconW(operation, &n) != FALSE;
    } else if (operation == NIM_MODIFY && message) {
        n.uFlags = NIF_INFO;
        lstrcpynW(n.szInfoTitle, L"Таймер завершён", 64);
        lstrcpynW(n.szInfo, message, 256);
        n.dwInfoFlags = NIIF_INFO;
        Shell_NotifyIconW(operation, &n);
    } else {
        Shell_NotifyIconW(operation, &n);
    }
}
static void show_window(HWND hwnd) {
    ShowWindow(hwnd, SW_SHOW);
    SetForegroundWindow(hwnd);
}
static void refresh_list(void) {
    int selected = (int)SendMessageW(list, LB_GETCURSEL, 0, 0);
    int i;
    uint64_t now = GetTickCount64();
    SendMessageW(list, WM_SETREDRAW, FALSE, 0);
    SendMessageW(list, LB_RESETCONTENT, 0, 0);
    for (i = 0; i < timer_count; ++i) {
        wchar_t line[200];
        uint64_t secs = (timer_remaining(&timers[i], now) + 999) / 1000;
        const wchar_t *state = timers[i].finished ? L"готово" : timers[i].running ? L"идёт" : L"пауза";
        swprintf(line, 200, L"%ls  |  %02llu:%02llu:%02llu  |  %ls", timers[i].name,
                 (unsigned long long)(secs / 3600), (unsigned long long)((secs / 60) % 60),
                 (unsigned long long)(secs % 60), state);
        SendMessageW(list, LB_ADDSTRING, 0, (LPARAM)line);
    }
    if (selected >= timer_count) selected = timer_count - 1;
    if (selected >= 0) SendMessageW(list, LB_SETCURSEL, selected, 0);
    SendMessageW(list, WM_SETREDRAW, TRUE, 0);
    InvalidateRect(list, NULL, TRUE);
}
static void add_timer(HWND hwnd) {
    wchar_t name[64], text[32], *end;
    long minutes;
    if (timer_count >= MAX_TIMERS) {
        MessageBoxW(hwnd, L"Максимум 12 таймеров. Удалите ненужный.", L"Таймер", MB_OK | MB_ICONINFORMATION);
        return;
    }
    GetWindowTextW(name_edit, name, 64);
    GetWindowTextW(minutes_edit, text, 32);
    minutes = wcstol(text, &end, 10);
    if (end == text || *end || minutes < 1 || minutes > 10080) {
        MessageBoxW(hwnd, L"Введите целое число минут от 1 до 10080 (7 дней).", L"Таймер", MB_OK | MB_ICONWARNING);
        return;
    }
    if (!name[0]) lstrcpyW(name, L"Без названия");
    lstrcpynW(timers[timer_count].name, name, 64);
    timer_start(&timers[timer_count], (uint64_t)minutes * 60000, GetTickCount64());
    ++timer_count;
    refresh_list();
    SendMessageW(list, LB_SETCURSEL, timer_count - 1, 0);
}
static void check_timers(HWND hwnd) {
    int i;
    uint64_t now = GetTickCount64();
    for (i = 0; i < timer_count; ++i) {
        if (timer_update(&timers[i], now)) {
            MessageBeep(MB_ICONASTERISK);
            if (tray_ok) tray(hwnd, NIM_MODIFY, timers[i].name);
            SetWindowTextW(info, L"Таймер завершён! Выберите его в списке.");
        }
    }
    refresh_list();
}
static LRESULT CALLBACK window_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    int selected;
    switch (msg) {
    case WM_CREATE:
        control(hwnd, L"STATIC", L"Название:", 0, 14, 16, 90, 20, 0);
        name_edit = control(hwnd, L"EDIT", L"Мой таймер", WS_BORDER | ES_AUTOHSCROLL, 106, 13, 255, 24, ID_NAME);
        SendMessageW(name_edit, EM_SETLIMITTEXT, 63, 0);
        control(hwnd, L"STATIC", L"Минуты:", 0, 14, 48, 90, 20, 0);
        minutes_edit = control(hwnd, L"EDIT", L"10", WS_BORDER | ES_NUMBER, 106, 45, 80, 24, ID_MINUTES);
        SendMessageW(minutes_edit, EM_SETLIMITTEXT, 5, 0);
        control(hwnd, L"BUTTON", L"Запустить", BS_PUSHBUTTON, 200, 44, 161, 27, ID_ADD);
        list = control(hwnd, L"LISTBOX", L"", WS_BORDER | WS_VSCROLL | LBS_NOTIFY, 14, 81, 347, 170, ID_LIST);
        control(hwnd, L"BUTTON", L"Пауза / продолжить", BS_PUSHBUTTON, 14, 261, 164, 30, ID_TOGGLE);
        control(hwnd, L"BUTTON", L"Удалить", BS_PUSHBUTTON, 187, 261, 82, 30, ID_DELETE);
        control(hwnd, L"BUTTON", L"Скрыть", BS_PUSHBUTTON, 278, 261, 83, 30, ID_HIDE);
        info = control(hwnd, L"STATIC", L"Ctrl+Alt+T — показать / скрыть", 0, 14, 302, 347, 35, 0);
        tray(hwnd, NIM_ADD, NULL);
        hotkey_ok = RegisterHotKey(hwnd, HOTKEY_ID, MOD_CONTROL | MOD_ALT | MOD_NOREPEAT, 'T') != FALSE;
        if (!hotkey_ok && tray_ok) SetWindowTextW(info, L"Горячая клавиша занята. Откройте окно через значок в трее.");
        if (!hotkey_ok && !tray_ok) SetWindowTextW(info, L"Клавиша и трей недоступны: окно нельзя скрыть.");
        SetTimer(hwnd, 1, 250, NULL);
        return 0;
    case WM_TIMER:
        check_timers(hwnd);
        return 0;
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case ID_ADD: add_timer(hwnd); break;
        case ID_TOGGLE:
            selected = (int)SendMessageW(list, LB_GETCURSEL, 0, 0);
            if (selected >= 0 && selected < timer_count) {
                check_timers(hwnd);
                if (timers[selected].running) timer_pause(&timers[selected], GetTickCount64());
                else timer_resume(&timers[selected], GetTickCount64());
                refresh_list();
            }
            break;
        case ID_DELETE:
            selected = (int)SendMessageW(list, LB_GETCURSEL, 0, 0);
            if (selected >= 0 && selected < timer_count) {
                int i;
                for (i = selected; i + 1 < timer_count; ++i) timers[i] = timers[i + 1];
                --timer_count; refresh_list();
            }
            break;
        case ID_HIDE:
            if (hotkey_ok || tray_ok) ShowWindow(hwnd, SW_HIDE);
            break;
        }
        return 0;
    case WM_HOTKEY:
        if (wp == HOTKEY_ID) {
            if (IsWindowVisible(hwnd)) ShowWindow(hwnd, SW_HIDE);
            else show_window(hwnd);
        }
        return 0;
    case WM_TRAY:
        if (lp == WM_LBUTTONDBLCLK || lp == WM_RBUTTONUP || lp == NIN_BALLOONUSERCLICK) show_window(hwnd);
        return 0;
    case WM_CLOSE:
        if (hotkey_ok || tray_ok) ShowWindow(hwnd, SW_HIDE);
        return 0;
    case WM_DESTROY:
        KillTimer(hwnd, 1);
        if (hotkey_ok) UnregisterHotKey(hwnd, HOTKEY_ID);
        if (tray_ok) tray(hwnd, NIM_DELETE, NULL);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}
static DWORD WINAPI ui_thread(LPVOID unused) {
    WNDCLASSW wc;
    HWND hwnd;
    MSG msg;
    (void)unused;
    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = window_proc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = class_name;
    if (!RegisterClassW(&wc)) return 1;
    hwnd = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, class_name, L"Amazing Smart Timer",
                            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                            CW_USEDEFAULT, CW_USEDEFAULT, 391, 385, NULL, NULL, instance, NULL);
    if (!hwnd) return 1;
    ShowWindow(hwnd, SW_SHOW);
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    UnregisterClassW(class_name, instance);
    return 0;
}
BOOL WINAPI DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved) {
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        HANDLE thread;
        instance = hinst;
        DisableThreadLibraryCalls(hinst);
        thread = CreateThread(NULL, 0, ui_thread, NULL, 0, NULL);
        if (thread) CloseHandle(thread);
    }
    return TRUE;
}
