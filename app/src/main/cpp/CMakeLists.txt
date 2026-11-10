cmake_minimum_required(VERSION 3.22)
project(iraqhub_native422 LANGUAGES C CXX)

# Offline CMake: never fetch binaries or resolve arbitrary packages at build.
# The FFmpeg libraries must be built from the verified LGPL official release
# by scripts/build-native422-ffmpeg.sh for Android ARMv7.
if(NOT CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
 message(FATAL_ERROR "Native422 is tested only for armeabi-v7a X5M")
endif()
if(NOT DEFINED NATIVE422_FFMPEG_ROOT OR
   NOT EXISTS "${NATIVE422_FFMPEG_ROOT}/include/libavcodec/avcodec.h")
 message(FATAL_ERROR "Set NATIVE422_FFMPEG_ROOT to verified FFmpeg ARMv7 headers and .so")
endif()
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
foreach(LIB avformat avcodec swscale avutil)
 if(NOT EXISTS "${NATIVE422_FFMPEG_ROOT}/lib/lib${LIB}.so")
  message(FATAL_ERROR "Missing verified LGPL lib${LIB}.so")
 endif()
 add_library(${LIB} SHARED IMPORTED)
 set_target_properties(${LIB} PROPERTIES
   IMPORTED_LOCATION "${NATIVE422_FFMPEG_ROOT}/lib/lib${LIB}.so")
endforeach()
add_library(iraqhub_native422 SHARED native422_decoder.cpp)
target_compile_options(iraqhub_native422 PRIVATE
 -Wall -Wextra -Werror -fvisibility=hidden -fstack-protector-strong)
target_include_directories(iraqhub_native422 PRIVATE
 "${NATIVE422_FFMPEG_ROOT}/include")
target_link_libraries(iraqhub_native422 PRIVATE
 avformat avcodec swscale avutil android log z m)
