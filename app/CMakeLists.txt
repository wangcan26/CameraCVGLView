# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             nvision_core

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/jniapi.cpp
             src/main/cpp/nvapp.cpp
             src/main/cpp/nvrenderer.cpp
             src/main/cpp/nv_cam_background.cpp
             src/main/cpp/nv_point_cloud.cpp
             src/main/cpp/nvtracker.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

set(ThirdParty_DIR ${CMAKE_SOURCE_DIR}/src/main/cpp/thirdParty/)
set(ThirdParty_OPENCV ${ThirdParty_DIR}/opencv/)

#opencv core
add_library(opencv_core STATIC IMPORTED)
set_target_properties(opencv_core PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_core.a)
#opencv videoio
add_library(opencv_videoio STATIC IMPORTED)
set_target_properties(opencv_videoio PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_videoio.a)
#opencv highgui
add_library(opencv_highgui STATIC IMPORTED)
set_target_properties(opencv_highgui PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_highgui.a)
#opencv imgproc
add_library(opencv_imgproc STATIC IMPORTED)
set_target_properties(opencv_imgproc PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_imgproc.a)
#opencv_objdetect
add_library(opencv_objdetect STATIC IMPORTED)
set_target_properties(opencv_objdetect PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_objdetect.a)
#opencv_calib3d
add_library(opencv_calib3d STATIC IMPORTED)
set_target_properties(opencv_calib3d PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libopencv_calib3d.a)

#opencv_thirdparty
#jasper
add_library(lib_jasper STATIC IMPORTED)
set_target_properties(lib_jasper PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/liblibjasper.a)

#jpeg
add_library(lib_jpeg STATIC IMPORTED)
set_target_properties(lib_jpeg PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/liblibjpeg.a)

#png
add_library(lib_png STATIC IMPORTED)
set_target_properties(lib_png PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/liblibpng.a)


#protobuf
add_library(lib_protobuf STATIC IMPORTED)
set_target_properties(lib_protobuf PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/liblibprotobuf.a)

#tiff
add_library(lib_tiff STATIC IMPORTED)
set_target_properties(lib_tiff PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/liblibtiff.a)

#tbb
add_library(lib_tbb STATIC IMPORTED)
set_target_properties(lib_tbb PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libtbb.a)

#tegra_hal
add_library(lib_tegra_hal STATIC IMPORTED)
set_target_properties(lib_tegra_hal PROPERTIES IMPORTED_LOCATION ${ThirdParty_OPENCV}/src/${ANDROID_ABI}/libtegra_hal.a)

set(OPENCV_LIBS  opencv_videoio opencv_objdetect opencv_highgui opencv_imgproc  opencv_calib3d opencv_core)
set(OPENCV_THIRD_LIBS lib_jasper lib_jpeg lib_png lib_protobuf lib_tiff lib_tbb lib_tegra_hal)

include_directories(${ThirdParty_OPENCV}/include)
include_directories(${ThirdParty_DIR}/FaceTracker2)

add_subdirectory(${ThirdParty_DIR}/cpufeatures)
add_subdirectory(${ThirdParty_DIR}/FaceTracker2)
add_subdirectory(${ThirdParty_DIR}/FaceTracker)


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.



target_link_libraries( # Specifies the target library.
                       nvision_core
                       # Links the target library to the log library
                       # included in the NDK.
                       android
                       EGL
                       GLESv2
                       jnigraphics
                       facetracker
                       log
                       )