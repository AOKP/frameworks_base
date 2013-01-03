LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags

ifeq ($(BOARD_HAVE_BLUETOOTH_BLUEZ), true)
    LOCAL_SRC_FILES := $(filter-out \
                        com/android/server/BluetoothManagerService.java \
                        ,$(LOCAL_SRC_FILES))
endif

LOCAL_MODULE:= services

LOCAL_JAVA_LIBRARIES := android.policy telephony-common

include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)
