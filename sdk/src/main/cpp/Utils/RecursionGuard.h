#pragma once

// Thread-local recursion guard to prevent stack overflow in hooks
extern thread_local bool g_in_hook;

#define SCOPED_GUARD() \
    if (g_in_hook) return; \
    g_in_hook = true; \
    struct Guard { ~Guard() { g_in_hook = false; } } _guard;

#define HOOK_ACTIVE() (g_in_hook)

#define ENTER_HOOK_VOID() \
    if (g_in_hook) return; \
    g_in_hook = true;

#define EXIT_HOOK() \
    g_in_hook = false;
