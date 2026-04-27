#include "RecursionGuard.h"

thread_local bool g_in_hook = false;
