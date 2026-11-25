#include "timer.h"

void timer_start(SmartTimer *t, uint64_t duration_ms, uint64_t now) {
    t->remaining_ms = duration_ms;
    t->deadline_ms = now + duration_ms;
    t->running = 1;
    t->finished = 0;
}
uint64_t timer_remaining(const SmartTimer *t, uint64_t now) {
    if (t->finished) return 0;
    if (!t->running) return t->remaining_ms;
    return t->deadline_ms > now ? t->deadline_ms - now : 0;
}
void timer_pause(SmartTimer *t, uint64_t now) {
    if (!t->running || t->finished) return;
    t->remaining_ms = timer_remaining(t, now);
    /* A timer already due must be completed by timer_update, not paused. */
    if (t->remaining_ms) t->running = 0;
}
void timer_resume(SmartTimer *t, uint64_t now) {
    if (t->running || t->finished || !t->remaining_ms) return;
    t->deadline_ms = now + t->remaining_ms;
    t->running = 1;
}
int timer_update(SmartTimer *t, uint64_t now) {
    if (!t->running || t->finished || timer_remaining(t, now)) return 0;
    t->running = 0;
    t->finished = 1;
    t->remaining_ms = 0;
    return 1;
}
