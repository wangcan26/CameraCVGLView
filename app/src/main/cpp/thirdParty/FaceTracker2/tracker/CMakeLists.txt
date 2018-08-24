#FaceTracker2/Tracker

file(GLOB SOURCE_FILES
	*.cpp
)

add_library(clmtracker STATIC ${SOURCE_FILES})


target_link_libraries( # Specifies the target library.
                       clmtracker
                       # Links the target library to the log library
                       # included in the NDK.
                       ${OPENCV_LIBS}
                       ${OPENCV_THIRD_LIBS}
                       cpu-features
                       z
                       m
                       )