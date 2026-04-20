cmake_minimum_required(VERSION 3.22.1)

project("loader-native")

add_library(loader-native SHARED
        native_hook.cpp
        memory.cpp)

find_library(log-lib log)

target_link_libraries(loader-native
        ${log-lib})
