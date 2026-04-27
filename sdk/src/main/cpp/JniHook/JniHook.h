#ifndef JNI_HOOK_H
#define JNI_HOOK_H

#include <jni.h>

namespace OneCore {
    void installJniHooks(JNIEnv* env);
}

#endif // JNI_HOOK_H
