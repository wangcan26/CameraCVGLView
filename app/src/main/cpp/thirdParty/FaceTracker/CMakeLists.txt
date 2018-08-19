
file(GLOB SOURCE_FILES
	lib/*.cpp
)


add_library(facetracker STATIC ${SOURCE_FILES})
target_include_directories(facetracker PUBLIC ${ThirdParty_DIR})


target_link_libraries( # Specifies the target library.
                       facetracker
                       # Links the target library to the log library
                       # included in the NDK.
                       ${OPENCV_LIBS}
                       ${OPENCV_THIRD_LIBS}
                       cpu-features
                       z
                       m
                       )