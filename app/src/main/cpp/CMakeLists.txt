cmake_minimum_required(VERSION 3.22.1)
project(bili2media_lame C)

set(LAME_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/third_party/lame")

add_library(mp3lame STATIC
    ${LAME_ROOT}/libmp3lame/VbrTag.c
    ${LAME_ROOT}/libmp3lame/bitstream.c
    ${LAME_ROOT}/libmp3lame/encoder.c
    ${LAME_ROOT}/libmp3lame/fft.c
    ${LAME_ROOT}/libmp3lame/gain_analysis.c
    ${LAME_ROOT}/libmp3lame/id3tag.c
    ${LAME_ROOT}/libmp3lame/lame.c
    ${LAME_ROOT}/libmp3lame/newmdct.c
    ${LAME_ROOT}/libmp3lame/presets.c
    ${LAME_ROOT}/libmp3lame/psymodel.c
    ${LAME_ROOT}/libmp3lame/quantize.c
    ${LAME_ROOT}/libmp3lame/quantize_pvt.c
    ${LAME_ROOT}/libmp3lame/reservoir.c
    ${LAME_ROOT}/libmp3lame/set_get.c
    ${LAME_ROOT}/libmp3lame/tables.c
    ${LAME_ROOT}/libmp3lame/takehiro.c
    ${LAME_ROOT}/libmp3lame/util.c
    ${LAME_ROOT}/libmp3lame/vbrquantize.c
    ${LAME_ROOT}/libmp3lame/version.c
    ${LAME_ROOT}/libmp3lame/mpglib_interface.c
)
target_include_directories(mp3lame PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LAME_ROOT}/include
    ${LAME_ROOT}/libmp3lame
    ${LAME_ROOT}
)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H=1)
target_link_libraries(mp3lame PUBLIC m)

add_library(bili2media_lame SHARED lame_jni.c)
target_include_directories(bili2media_lame PRIVATE ${LAME_ROOT}/include)
target_link_libraries(bili2media_lame PRIVATE mp3lame)
