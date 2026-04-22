cmake_minimum_required(VERSION 3.22.1)

project("onecore-native")

# Set the C++ standard to 17
set(CMAKE_CXX_STANDARD 17)

# Library name matches System.loadLibrary("onecore")
add_library(onecore SHARED
        native_hook.cpp
        memory.cpp)

# Include directories (Standard JNI + Local)
include_directories(SYSTEM ${ANDROID_NDK}/sysroot/usr/include)

# Standard Android Logging library
find_library(log-lib log)

# Link the library
target_link_libraries(onecore
        ${log-lib})
