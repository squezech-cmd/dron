#ifndef SMART_TIMER_H
#define SMART_TIMER_H

#include <stdint.h>
#include <wchar.h>

typedef struct {
    wchar_t name[64];
    uint64_t remaining_ms;
    uint64_t deadline_ms;
    int running;
    int finished;
} SmartTimer;

void timer_start(SmartTimer *t, uint64_t duration_ms, uint64_t now);
uint64_t timer_remaining(const SmartTimer *t, uint64_t now);
void timer_pause(SmartTimer *t, uint64_t now);
void timer_resume(SmartTimer *t, uint64_t now);
int timer_update(SmartTimer *t, uint64_t now);
#endif
