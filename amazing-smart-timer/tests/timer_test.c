#include "../src/timer.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    SmartTimer t = {0};
    timer_start(&t, 60000, 1000);
    assert(timer_remaining(&t, 11000) == 50000);
    timer_pause(&t, 11000);
    assert(!t.running && timer_remaining(&t, 90000) == 50000);
    timer_resume(&t, 90000);
    assert(t.running && timer_remaining(&t, 100000) == 40000);
    assert(!timer_update(&t, 139999));
    assert(timer_update(&t, 140000));
    assert(t.finished && !t.running && !timer_remaining(&t, 150000));
    assert(!timer_update(&t, 150000));
    timer_resume(&t, 160000);
    assert(!t.running);
    timer_start(&t, 100, 1000);
    timer_pause(&t, 1100);
    assert(timer_update(&t, 1100));
    puts("timer tests: OK");
    return 0;
}
