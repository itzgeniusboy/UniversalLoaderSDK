cmake_minimum_required(VERSION 3.22.1)

project("onecore_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(onecore_native SHARED
        native_hook.cpp
        memory.cpp
        BinderHook.cpp
        Hook/UnixFileSystemHook.cpp
        Hook/RuntimeHook.cpp
        JniHook/JniHook.cpp
        Utils/fake_dlfcn.cpp
        Utils/Stealth.cpp
        Utils/EnvironmentProtector.cpp
        Utils/KernelShield.cpp
        Utils/HiddenApiBypass.cpp
        Utils/ModuleScanner.cpp
        Utils/RecursionGuard.cpp
        Hook/SocketHook.cpp
        KittyMemory/KittyMemory.cpp
        KittyMemory/MemoryPatch.cpp
        Hook/DexFileHook.cpp
        dobby.c
)

target_include_directories(onecore_native PRIVATE 
    .
    Hook
    Utils
    JniHook
    KittyMemory
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)

target_link_libraries(onecore_native
        ${log-lib}
        ${android-lib}
        ${egl-lib}
        dl
)
