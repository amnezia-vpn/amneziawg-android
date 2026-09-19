/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

struct go_string { const char *str; long n; };
extern int awgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings);
extern void awgTurnOff(int handle);
extern int awgGetSocketV4(int handle);
extern int awgGetSocketV6(int handle);
extern char *awgGetConfig(int handle);
extern char *awgVersion();
extern void awgSetUidFilter(int enabled);

/*
 * Strict Split Tunneling (issue amnezia-client#2457): Go asks the UidFilter
 * registered from Java whether a new outbound flow may enter the tunnel. The
 * upcall arrives on a Go thread the JVM does not know, so the thread is attached
 * once and detached by a pthread key destructor when it exits. Any failure on
 * the way denies the flow.
 */
static pthread_mutex_t uid_filter_lock = PTHREAD_MUTEX_INITIALIZER;
static JavaVM *uid_filter_vm;
static jobject uid_filter_obj;
static jmethodID uid_filter_allow;
static pthread_key_t uid_filter_thread_key;
static pthread_once_t uid_filter_thread_key_once = PTHREAD_ONCE_INIT;

static void uid_filter_detach_thread(void *vm)
{
	(*(JavaVM *)vm)->DetachCurrentThread((JavaVM *)vm);
}

static void uid_filter_create_thread_key(void)
{
	pthread_key_create(&uid_filter_thread_key, uid_filter_detach_thread);
}

static JNIEnv *uid_filter_env(void)
{
	JNIEnv *env;

	if ((*uid_filter_vm)->GetEnv(uid_filter_vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK)
		return env;
	if ((*uid_filter_vm)->AttachCurrentThreadAsDaemon(uid_filter_vm, &env, NULL) != JNI_OK)
		return NULL;
	pthread_once(&uid_filter_thread_key_once, uid_filter_create_thread_key);
	pthread_setspecific(uid_filter_thread_key, uid_filter_vm);
	return env;
}

int awgUidFilterAllow(const char *network, const char *src_ip, int src_port, const char *dst_ip, int dst_port)
{
	JNIEnv *env;
	jstring network_str, src_ip_str, dst_ip_str;
	jboolean allow = JNI_FALSE;

	pthread_mutex_lock(&uid_filter_lock);
	if (!uid_filter_obj)
		goto out;
	env = uid_filter_env();
	if (!env)
		goto out;
	/* The thread never returns to Java, so local references must be freed here. */
	if ((*env)->PushLocalFrame(env, 3) != JNI_OK) {
		(*env)->ExceptionClear(env);
		goto out;
	}
	network_str = (*env)->NewStringUTF(env, network);
	src_ip_str = (*env)->NewStringUTF(env, src_ip);
	dst_ip_str = (*env)->NewStringUTF(env, dst_ip);
	if (network_str && src_ip_str && dst_ip_str)
		allow = (*env)->CallBooleanMethod(env, uid_filter_obj, uid_filter_allow, network_str, src_ip_str,
						  (jint)src_port, dst_ip_str, (jint)dst_port);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionClear(env);
		allow = JNI_FALSE;
	}
	(*env)->PopLocalFrame(env, NULL);
out:
	pthread_mutex_unlock(&uid_filter_lock);
	return allow == JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgSetUidFilter(JNIEnv *env, jclass c, jobject filter)
{
	JavaVM *vm = NULL;
	jobject obj = NULL, old_obj;
	jmethodID allow = NULL;
	jclass filter_class;

	if (filter) {
		if ((*env)->GetJavaVM(env, &vm) != JNI_OK)
			return -1;
		/* Resolved here, on a Java thread: FindClass from a Go thread would not see app classes. */
		filter_class = (*env)->GetObjectClass(env, filter);
		allow = (*env)->GetMethodID(env, filter_class, "allow",
					    "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;I)Z");
		(*env)->DeleteLocalRef(env, filter_class);
		if (!allow) {
			(*env)->ExceptionClear(env);
			return -1;
		}
		obj = (*env)->NewGlobalRef(env, filter);
		if (!obj)
			return -1;
	} else {
		awgSetUidFilter(0);
	}

	pthread_mutex_lock(&uid_filter_lock);
	old_obj = uid_filter_obj;
	uid_filter_obj = obj;
	uid_filter_allow = allow;
	if (vm)
		uid_filter_vm = vm;
	pthread_mutex_unlock(&uid_filter_lock);

	if (old_obj)
		(*env)->DeleteGlobalRef(env, old_obj);
	if (filter)
		awgSetUidFilter(1);
	return 0;
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOn(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings)
{
	const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
	int ret = awgTurnOn((struct go_string){
		.str = ifname_str,
		.n = ifname_len
	}, tun_fd, (struct go_string){
		.str = settings_str,
		.n = settings_len
	});
	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
	return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOff(JNIEnv *env, jclass c, jint handle)
{
	awgTurnOff(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV4(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV4(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV6(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV6(handle);
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgGetConfig(JNIEnv *env, jclass c, jint handle)
{
	jstring ret;
	char *config = awgGetConfig(handle);
	if (!config)
		return NULL;
	ret = (*env)->NewStringUTF(env, config);
	free(config);
	return ret;
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgVersion(JNIEnv *env, jclass c)
{
	jstring ret;
	char *version = awgVersion();
	if (!version)
		return NULL;
	ret = (*env)->NewStringUTF(env, version);
	free(version);
	return ret;
}
