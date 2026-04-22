#ifndef DOBBY_H
#define DOBBY_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Dobby: A lightweight, multi-platform, multi-architecture exploit instrumentation framework.
 */

// Function to install a hook
int DobbyHook(void *address, void *replace_call, void **origin_call);

#ifdef __cplusplus
}
#endif

#endif // DOBBY_H
