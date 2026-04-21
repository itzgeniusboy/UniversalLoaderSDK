cmake_minimum_required(VERSION 3.22.1)

project("onecore-native")

add_library(onecore_injector SHARED
        native_hook.cpp
        memory.cpp)

find_library(log-lib log)

target_link_libraries(onecore_injector
        ${log-lib})
