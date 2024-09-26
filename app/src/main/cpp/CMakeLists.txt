cmake_minimum_required(VERSION 3.10.2)
project("MyApplication")

# Set C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Specify the path to PCL headers
include_directories(${CMAKE_SOURCE_DIR}/../include/pcl)
include_directories(${CMAKE_SOURCE_DIR}/../include/boost)
include_directories(${CMAKE_SOURCE_DIR}/../include/flann)
include_directories(${CMAKE_SOURCE_DIR}/../include/lz4)

# Link directories for PCL and dependencies
link_directories(${CMAKE_SOURCE_DIR}/../cppLibs/pcl)
link_directories(${CMAKE_SOURCE_DIR}/../cppLibs/flann)
link_directories(${CMAKE_SOURCE_DIR}/../cppLibs/lz4)
link_directories(${CMAKE_SOURCE_DIR}/../cppLibs/boost)

# Find OpenMP
find_package(OpenMP REQUIRED)
if(OpenMP_CXX_FOUND)
    message("Found OpenMP")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${OpenMP_EXE_LINKER_FLAGS}")
endif()

# Add your native library
add_library(
        native-lib
        SHARED
        native-lib.cpp  # Your native source files
)

# Specify the Boost libraries to link against
set(BOOST_LIBS
        boost_filesystem
        boost_system
        boost_iostreams
        boost_atomic
        # Add other Boost libraries as needed
)

# Link Boost static libraries
target_link_libraries(
        native-lib
        # PCL Libraries
        pcl_common
        pcl_io
        pcl_octree
        # FLANN and LZ4
        flann_cpp
        lz4
        # Boost Libraries (Static)
        ${CMAKE_SOURCE_DIR}/../cppLibs/boost/libboost_filesystem.a
        ${CMAKE_SOURCE_DIR}/../cppLibs/boost/libboost_system.a
        ${CMAKE_SOURCE_DIR}/../cppLibs/boost/libboost_iostreams.a
        ${CMAKE_SOURCE_DIR}/../cppLibs/boost/libboost_atomic.a
        # Other dependencies
        # Add any other libraries required by PCL
        log  # Android log library
        ${OpenMP_CXX_LIBRARIES}
        # Link other necessary libraries here
)
