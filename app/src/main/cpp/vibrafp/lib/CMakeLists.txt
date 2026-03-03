cmake_minimum_required(VERSION 3.22)
project(vibra_fp LANGUAGES CXX)

# ========== Options ==========
option(ENABLE_LTO "Enable thin-LTO compile/link flags" ON)

# Optional: user can pass -DFFTW3_PATH=/path/to/install-android-fftw
# Expected layout if FFTW3_PATH is provided:
#   <FFTW3_PATH>/<abi>/include/   -> headers
#   <FFTW3_PATH>/<abi>/lib/libfftw3.a  -> static lib
if(NOT DEFINED FFTW3_PATH)
    set(FFTW3_PATH "" CACHE PATH "Path to FFTW install root (optional)")
endif()

message(STATUS "Project source dir: ${CMAKE_SOURCE_DIR}")

# ========== FFTW detection and import ==========
set(FFTW3_INCLUDE_DIR "")
set(FFTW3_STATIC_LIB "")

if(FFTW3_PATH)
    # fallback layout
    set(FFTW3_STATIC_LIB "${FFTW3_PATH}/lib/libfftw3.a")
    set(FFTW3_INCLUDE_DIR "${FFTW3_PATH}/include")
    # prefer per-ABI layout if building for Android and file exists
    if(ANDROID AND ANDROID_ABI)
        set(CANDIDATE_LIB "${FFTW3_PATH}/${ANDROID_ABI}/lib/libfftw3.a")
        if(EXISTS "${CANDIDATE_LIB}")
            set(FFTW3_STATIC_LIB "${CANDIDATE_LIB}")
            set(FFTW3_INCLUDE_DIR "${FFTW3_PATH}/${ANDROID_ABI}/include")
        endif()
    endif()
endif()

# If FFTW3_PATH not provided, try the repository-local default layout:
# <CMAKE_SOURCE_DIR>/third_party/fftw-android/<ANDROID_ABI>/libfftw3.a
if(ANDROID AND (NOT FFTW3_STATIC_LIB OR NOT EXISTS "${FFTW3_STATIC_LIB}"))
    set(LOCAL_THIRD "${CMAKE_SOURCE_DIR}/../third_party/fftw-android")
    if(DEFINED ANDROID_ABI)
        set(LOCAL_LIB "${LOCAL_THIRD}/${ANDROID_ABI}/lib/libfftw3.a")
        if(EXISTS "${LOCAL_LIB}")
            set(FFTW3_STATIC_LIB "${LOCAL_LIB}")
            set(FFTW3_INCLUDE_DIR "${LOCAL_THIRD}/${ANDROID_ABI}/include")
        endif()
    endif()
endif()

## Check if FFTW was found
if(FFTW3_STATIC_LIB AND EXISTS "${FFTW3_STATIC_LIB}")
    set(FFTW3_FOUND TRUE)
else()
    set(FFTW3_FOUND FALSE)
    message(WARNING "FFTW3 static library not found at ${FFTW3_STATIC_LIB}. Build fftw and place prebuilt in third_party/fftw-android/<abi>. The vibra_fp library will NOT be built.")
endif()

if(FFTW3_FOUND)
    # ========== Sources ==========
    set(LIBVIBRA_SOURCES
            vibra.cpp
            vibra_jni.cpp
            algorithm/signature.cpp
            algorithm/frequency.cpp
            algorithm/signature_generator.cpp
            audio/wav.cpp
            audio/downsampler.cpp
    )

    add_library(vibra_fp SHARED ${LIBVIBRA_SOURCES})

    target_include_directories(vibra_fp
            PRIVATE
            ${CMAKE_SOURCE_DIR}/../include
            ${CMAKE_SOURCE_DIR}
            ${CMAKE_SOURCE_DIR}/algorithm
            ${CMAKE_SOURCE_DIR}/audio
            ${CMAKE_SOURCE_DIR}/utils
    )

    add_library(fftw3_static STATIC IMPORTED GLOBAL)
    set_target_properties(fftw3_static PROPERTIES
            IMPORTED_LOCATION "${FFTW3_STATIC_LIB}"
            INTERFACE_INCLUDE_DIRECTORIES "${FFTW3_INCLUDE_DIR}"
    )
    message(STATUS "Using FFTW (imported static): ${FFTW3_STATIC_LIB}")
    target_link_libraries(vibra_fp PRIVATE fftw3_static)

    # ========== C++ standard ==========
    set_target_properties(vibra_fp PROPERTIES CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES)

    # ========== Android system libs ==========
    if (ANDROID)
        target_link_libraries(vibra_fp PRIVATE log m)
    endif()

    # ========== Compiler / Linker options ==========
    target_compile_options(vibra_fp PRIVATE
            $<$<CONFIG:Release>:-O2>
            $<$<NOT:$<CONFIG:Release>>:-O0>
            $<$<NOT:$<CONFIG:Release>>:-g>
            $<$<CONFIG:Release>:-ffunction-sections>
            $<$<CONFIG:Release>:-fdata-sections>
            $<$<CONFIG:Release>:-fvisibility=hidden>
            $<$<CONFIG:Release>:-fomit-frame-pointer>
            $<$<CONFIG:Release>:-fPIC>
    )

    if(ENABLE_LTO)
        target_compile_options(vibra_fp PRIVATE $<$<CONFIG:Release>:-flto=thin>)
        target_link_options(vibra_fp PRIVATE
                $<$<CONFIG:Release>:-flto=thin>
                $<$<CONFIG:Release>:-Wl,--gc-sections>
                $<$<CONFIG:Release>:-Wl,--strip-all>
        )
    else()
        target_link_options(vibra_fp PRIVATE
                $<$<CONFIG:Release>:-Wl,--gc-sections>
                $<$<CONFIG:Release>:-Wl,--strip-all>
        )
    endif()

    # ========== Limit exported symbols ==========
    set(VIBRA_EXPORT_SCRIPT "${CMAKE_SOURCE_DIR}/vibra.sym")
    if(EXISTS "${VIBRA_EXPORT_SCRIPT}")
        message(STATUS "Using version script: ${VIBRA_EXPORT_SCRIPT}")
        target_link_options(vibra_fp PRIVATE "-Wl,--version-script=${VIBRA_EXPORT_SCRIPT}")
    endif()
else()
    # Dummy target or just skip. Since this is an externalNativeBuild,
    # if we don't define any target, it might complain depending on Gradle version.
    # Let's create an empty dummy library to satisfy Gradle if needed, or just let it be.
    # Most modern AGP versions handle empty CMake projects fine.
endif()
