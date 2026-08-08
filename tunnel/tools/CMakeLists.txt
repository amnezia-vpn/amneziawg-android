# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.

cmake_minimum_required(VERSION 3.4.1)
project("AmneziaWG")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
add_link_options(LINKER:--build-id=none)
add_link_options(-Wl,-z,max-page-size=16384)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
add_compile_options(-Wall -Werror)

add_executable(libawg-quick.so amneziawg-tools/src/wg-quick/android.c ndk-compat/compat.c)
target_compile_options(libawg-quick.so PUBLIC -std=gnu11 -include ${CMAKE_CURRENT_SOURCE_DIR}/ndk-compat/compat.h -DAWG_PACKAGE_NAME=\"${ANDROID_PACKAGE_NAME}\")
target_link_libraries(libawg-quick.so -ldl)

file(GLOB WG_SOURCES amneziawg-tools/src/*.c ndk-compat/compat.c)
add_executable(libawg.so ${WG_SOURCES})
target_include_directories(libawg.so PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/uapi/linux/" "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/")
target_compile_options(libawg.so PUBLIC -std=gnu11 -include ${CMAKE_CURRENT_SOURCE_DIR}/ndk-compat/compat.h -DRUNSTATEDIR=\"/data/data/${ANDROID_PACKAGE_NAME}/cache\")

add_custom_target(libwg-go.so WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go" COMMENT "Building amneziawg-go" VERBATIM COMMAND "${ANDROID_HOST_PREBUILTS}/bin/make"
    ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
    ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
    GRADLE_USER_HOME=${GRADLE_USER_HOME}
    CC=${CMAKE_C_COMPILER}
    CFLAGS=${CMAKE_C_FLAGS}
    LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}
    SYSROOT=${CMAKE_SYSROOT}
    TARGET=${CMAKE_C_COMPILER_TARGET}
    DESTDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    BUILDDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../generated-src
)

# Strip unwanted ELF sections to prevent DT_FLAGS_1 warnings on old Android versions
file(GLOB ELF_CLEANER_SOURCES elf-cleaner/*.c elf-cleaner/*.cpp)
add_custom_target(elf-cleaner COMMENT "Building elf-cleaner" VERBATIM COMMAND cc
        -O2 -DPACKAGE_NAME="elf-cleaner" -DPACKAGE_VERSION="" -DCOPYRIGHT=""
        -o "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner" ${ELF_CLEANER_SOURCES}
)
add_custom_command(TARGET libawg.so POST_BUILD VERBATIM COMMAND "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner"
        --api-level "${ANDROID_NATIVE_API_LEVEL}" "$<TARGET_FILE:libawg.so>")
add_dependencies(libawg.so elf-cleaner)
add_custom_command(TARGET libawg-quick.so POST_BUILD VERBATIM COMMAND "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner"
        --api-level "${ANDROID_NATIVE_API_LEVEL}" "$<TARGET_FILE:libawg-quick.so>")
add_dependencies(libawg-quick.so elf-cleaner)
