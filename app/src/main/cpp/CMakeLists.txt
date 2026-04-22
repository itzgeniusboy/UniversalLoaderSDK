cmake_minimum_required(VERSION 3.22.1)

project("onecore-sdk")

add_library(onecore SHARED
        native_hook.cpp
        memory.cpp)

find_library(log-lib log)

target_link_libraries(onecore
        ${log-lib})
