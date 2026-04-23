cmake_minimum_required(VERSION 3.22.1)

project("onecore_native")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Library name matches System.loadLibrary("onecore_native")
add_library(onecore_native SHARED
        native_hook.cpp
        memory.cpp
        dobby.c
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(onecore_native
        ${log-lib}
        ${android-lib}
        dl
)
