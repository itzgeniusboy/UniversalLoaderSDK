#ifndef FAKE_DLFCN_H
#define FAKE_DLFCN_H

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

void *fake_dlopen(const char *filename, int flags);
void *fake_dlsym(void *handle, const char *name);
int fake_dlclose(void *handle);

#ifdef __cplusplus
}
#endif

#endif // FAKE_DLFCN_H
