#include <jni.h>
#include <limits.h>
#include <stdint.h>
#include <lame.h>

static lame_t from_handle(jlong handle) {
    return (lame_t)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_com_example_bili2media_export_mp3_engine_NativeLame_create(
        JNIEnv *env,
        jobject receiver,
        jint sample_rate,
        jint channels,
        jint bitrate_kbps) {
    (void)env;
    (void)receiver;
    if (sample_rate <= 0 || (channels != 1 && channels != 2) || bitrate_kbps != 192) {
        return 0;
    }

    lame_t encoder = lame_init();
    if (encoder == NULL) {
        return 0;
    }
    if (lame_set_num_channels(encoder, channels) < 0 ||
        lame_set_in_samplerate(encoder, sample_rate) < 0 ||
        /* MPEG-1 supports 192 kbps; use its 44.1 kHz grid for lower-rate AAC. */
        lame_set_out_samplerate(encoder, sample_rate < 32000 ? 44100 : sample_rate) < 0 ||
        lame_set_mode(encoder, channels == 1 ? MONO : JOINT_STEREO) < 0 ||
        lame_set_VBR(encoder, vbr_off) < 0 ||
        lame_set_brate(encoder, bitrate_kbps) < 0 ||
        lame_init_params(encoder) < 0) {
        lame_close(encoder);
        return 0;
    }
    return (jlong)(intptr_t)encoder;
}

JNIEXPORT jint JNICALL
Java_com_example_bili2media_export_mp3_engine_NativeLame_encode(
        JNIEnv *env,
        jobject receiver,
        jlong handle,
        jshortArray pcm_array,
        jint samples_per_channel,
        jbyteArray mp3_array) {
    (void)receiver;
    lame_t encoder = from_handle(handle);
    if (encoder == NULL || pcm_array == NULL || mp3_array == NULL || samples_per_channel < 0) {
        return -1;
    }
    jint channels = lame_get_num_channels(encoder);
    if (channels < 1 || samples_per_channel > INT_MAX / channels ||
        (*env)->GetArrayLength(env, pcm_array) < samples_per_channel * channels) {
        return -1;
    }

    jshort *pcm = (*env)->GetShortArrayElements(env, pcm_array, NULL);
    if (pcm == NULL) {
        return -1;
    }
    jbyte *mp3 = (*env)->GetByteArrayElements(env, mp3_array, NULL);
    if (mp3 == NULL) {
        (*env)->ReleaseShortArrayElements(env, pcm_array, pcm, JNI_ABORT);
        return -1;
    }

    jint capacity = (*env)->GetArrayLength(env, mp3_array);
    int encoded_bytes;
    if (channels == 1) {
        encoded_bytes = lame_encode_buffer(
                encoder,
                pcm,
                pcm,
                samples_per_channel,
                (unsigned char *)mp3,
                capacity);
    } else {
        encoded_bytes = lame_encode_buffer_interleaved(
                encoder,
                pcm,
                samples_per_channel,
                (unsigned char *)mp3,
                capacity);
    }

    (*env)->ReleaseByteArrayElements(env, mp3_array, mp3, 0);
    (*env)->ReleaseShortArrayElements(env, pcm_array, pcm, JNI_ABORT);
    return encoded_bytes;
}

JNIEXPORT jint JNICALL
Java_com_example_bili2media_export_mp3_engine_NativeLame_flush(
        JNIEnv *env,
        jobject receiver,
        jlong handle,
        jbyteArray mp3_array) {
    (void)receiver;
    lame_t encoder = from_handle(handle);
    if (encoder == NULL || mp3_array == NULL) {
        return -1;
    }

    jbyte *mp3 = (*env)->GetByteArrayElements(env, mp3_array, NULL);
    if (mp3 == NULL) {
        return -1;
    }
    jint capacity = (*env)->GetArrayLength(env, mp3_array);
    int encoded_bytes = lame_encode_flush(encoder, (unsigned char *)mp3, capacity);
    (*env)->ReleaseByteArrayElements(env, mp3_array, mp3, 0);
    return encoded_bytes;
}

JNIEXPORT void JNICALL
Java_com_example_bili2media_export_mp3_engine_NativeLame_close(
        JNIEnv *env,
        jobject receiver,
        jlong handle) {
    (void)env;
    (void)receiver;
    lame_t encoder = from_handle(handle);
    if (encoder != NULL) {
        lame_close(encoder);
    }
}
