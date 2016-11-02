/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/**
 * Provides access to environment variables.
 */
public class Environment {
    private static final String TAG = "Environment";

    private static final String ENV_EXTERNAL_STORAGE = "EXTERNAL_STORAGE";
    private static final String ENV_ANDROID_ROOT = "ANDROID_ROOT";
    private static final String ENV_ANDROID_DATA = "ANDROID_DATA";
    private static final String ENV_ANDROID_EXPAND = "ANDROID_EXPAND";
    private static final String ENV_ANDROID_STORAGE = "ANDROID_STORAGE";
    private static final String ENV_DOWNLOAD_CACHE = "DOWNLOAD_CACHE";
    private static final String ENV_OEM_ROOT = "OEM_ROOT";
    private static final String ENV_ODM_ROOT = "ODM_ROOT";
    private static final String ENV_VENDOR_ROOT = "VENDOR_ROOT";

    /** {@hide} */
    public static final String DIR_ANDROID = "Android";
    private static final String DIR_DATA = "data";
    private static final String DIR_MEDIA = "media";
    private static final String DIR_OBB = "obb";
    private static final String DIR_FILES = "files";
    private static final String DIR_CACHE = "cache";

    /** {@hide} */
    @Deprecated
    public static final String DIRECTORY_ANDROID = DIR_ANDROID;

    private static final File DIR_ANDROID_ROOT = getDirectory(ENV_ANDROID_ROOT, "/system");
    private static final File DIR_ANDROID_DATA = getDirectory(ENV_ANDROID_DATA, "/data");
    private static final File DIR_ANDROID_EXPAND = getDirectory(ENV_ANDROID_EXPAND, "/mnt/expand");
    private static final File DIR_ANDROID_STORAGE = getDirectory(ENV_ANDROID_STORAGE, "/storage");
    private static final File DIR_DOWNLOAD_CACHE = getDirectory(ENV_DOWNLOAD_CACHE, "/cache");
    private static final File DIR_OEM_ROOT = getDirectory(ENV_OEM_ROOT, "/oem");
    private static final File DIR_ODM_ROOT = getDirectory(ENV_ODM_ROOT, "/odm");
    private static final File DIR_VENDOR_ROOT = getDirectory(ENV_VENDOR_ROOT, "/vendor");

    private static UserEnvironment sCurrentUser;
    private static boolean sUserRequired;

    static {
        initForCurrentUser();
    }

    /** {@hide} */
    public static void initForCurrentUser() {
        final int userId = UserHandle.myUserId();
        sCurrentUser = new UserEnvironment(userId);
    }

    /** {@hide} */
    public static class UserEnvironment {
        private final int mUserId;

        public UserEnvironment(int userId) {
            mUserId = userId;
        }

        public File[] getExternalDirs() {
            final StorageVolume[] volumes = StorageManager.getVolumeList(mUserId,
                    StorageManager.FLAG_FOR_WRITE);
            final File[] files = new File[volumes.length];
            for (int i = 0; i < volumes.length; i++) {
                files[i] = volumes[i].getPathFile();
            }
            return files;
        }

        @Deprecated
        public File getExternalStorageDirectory() {
            return getExternalDirs()[0];
        }

        @Deprecated
        public File getExternalStoragePublicDirectory(String type) {
            return buildExternalStoragePublicDirs(type)[0];
        }

        public File[] buildExternalStoragePublicDirs(String type) {
            return buildPaths(getExternalDirs(), type);
        }

        public File[] buildExternalStorageAndroidDataDirs() {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA);
        }

        public File[] buildExternalStorageAndroidObbDirs() {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_OBB);
        }

        public File[] buildExternalStorageAppDataDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName);
        }

        public File[] buildExternalStorageAppMediaDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_MEDIA, packageName);
        }

        public File[] buildExternalStorageAppObbDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_OBB, packageName);
        }

        public File[] buildExternalStorageAppFilesDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_FILES);
        }

        public File[] buildExternalStorageAppCacheDirs(String packageName) {
            return buildPaths(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_CACHE);
        }
    }

    /**
     * Return root of the "system" partition holding the core Android OS.
     * Always present and mounted read-only.
     */
    public static File getRootDirectory() {
        return DIR_ANDROID_ROOT;
    }

    /** {@hide} */
    public static File getStorageDirectory() {
        return DIR_ANDROID_STORAGE;
    }

    /**
     * Return root directory of the "oem" partition holding OEM customizations,
     * if any. If present, the partition is mounted read-only.
     *
     * @hide
     */
    public static File getOemDirectory() {
        return DIR_OEM_ROOT;
    }

    /**
     * Return root directory of the "odm" partition holding ODM customizations,
     * if any. If present, the partition is mounted read-only.
     *
     * @hide
     */
    public static File getOdmDirectory() {
        return DIR_ODM_ROOT;
    }

    /**
     * Return root directory of the "vendor" partition that holds vendor-provided
     * software that should persist across simple reflashing of the "system" partition.
     * @hide
     */
    public static File getVendorDirectory() {
        return DIR_VENDOR_ROOT;
    }

    /**
     * Return the system directory for a user. This is for use by system
     * services to store files relating to the user. This directory will be
     * automatically deleted when the user is removed.
     *
     * @deprecated This directory is valid and still exists, but callers should
     *             <em>strongly</em> consider switching to
     *             {@link #getDataSystemCeDirectory(int)} which is protected
     *             with user credentials or
     *             {@link #getDataSystemDeDirectory(int)} which supports fast
     *             user wipe.
     * @hide
     */
    @Deprecated
    public static File getUserSystemDirectory(int userId) {
        return new File(new File(getDataSystemDirectory(), "users"), Integer.toString(userId));
    }

    /**
     * Returns the config directory for a user. This is for use by system
     * services to store files relating to the user which should be readable by
     * any app running as that user.
     *
     * @deprecated This directory is valid and still exists, but callers should
     *             <em>strongly</em> consider switching to
     *             {@link #getDataMiscCeDirectory(int)} which is protected with
     *             user credentials or {@link #getDataMiscDeDirectory(int)}
     *             which supports fast user wipe.
     * @hide
     */
    @Deprecated
    public static File getUserConfigDirectory(int userId) {
        return new File(new File(new File(
                getDataDirectory(), "misc"), "user"), Integer.toString(userId));
    }

    /**
     * Return the user data directory.
     */
    public static File getDataDirectory() {
        return DIR_ANDROID_DATA;
    }

    /** {@hide} */
    public static File getDataDirectory(String volumeUuid) {
        if (TextUtils.isEmpty(volumeUuid)) {
            return DIR_ANDROID_DATA;
        } else {
            return new File("/mnt/expand/" + volumeUuid);
        }
    }

    /** {@hide} */
    public static File getExpandDirectory() {
        return DIR_ANDROID_EXPAND;
    }

    /** {@hide} */
    public static File getDataSystemDirectory() {
        return new File(getDataDirectory(), "system");
    }

    /**
     * Returns the base directory for per-user system directory, device encrypted.
     * {@hide}
     */
    public static File getDataSystemDeDirectory() {
        return buildPath(getDataDirectory(), "system_de");
    }

    /**
     * Returns the base directory for per-user system directory, credential encrypted.
     * {@hide}
     */
    public static File getDataSystemCeDirectory() {
        return buildPath(getDataDirectory(), "system_ce");
    }

    /** {@hide} */
    public static File getDataSystemCeDirectory(int userId) {
        return buildPath(getDataDirectory(), "system_ce", String.valueOf(userId));
    }

    /** {@hide} */
    public static File getDataSystemDeDirectory(int userId) {
        return buildPath(getDataDirectory(), "system_de", String.valueOf(userId));
    }

    /** {@hide} */
    public static File getDataMiscDirectory() {
        return new File(getDataDirectory(), "misc");
    }

    /** {@hide} */
    public static File getDataMiscCeDirectory(int userId) {
        return buildPath(getDataDirectory(), "misc_ce", String.valueOf(userId));
    }

    /** {@hide} */
    public static File getDataMiscDeDirectory(int userId) {
        return buildPath(getDataDirectory(), "misc_de", String.valueOf(userId));
    }

    private static File getDataProfilesDeDirectory(int userId) {
        return buildPath(getDataDirectory(), "misc", "profiles", "cur", String.valueOf(userId));
    }

    /** {@hide} */
    public static File getReferenceProfile(String packageName) {
        return buildPath(getDataDirectory(), "misc", "profiles", "ref", packageName);
    }

    /** {@hide} */
    public static File getDataProfilesDePackageDirectory(int userId, String packageName) {
        return buildPath(getDataProfilesDeDirectory(userId), packageName);
    }

    /** {@hide} */
    public static File getDataProfilesDeForeignDexDirectory(int userId) {
        return buildPath(getDataProfilesDeDirectory(userId), "foreign-dex");
    }

    /** {@hide} */
    public static File getDataAppDirectory(String volumeUuid) {
        return new File(getDataDirectory(volumeUuid), "app");
    }

    /** {@hide} */
    public static File getDataAppEphemeralDirectory(String volumeUuid) {
        return new File(getDataDirectory(volumeUuid), "app-ephemeral");
    }

    /** {@hide} */
    public static File getDataUserCeDirectory(String volumeUuid) {
        return new File(getDataDirectory(volumeUuid), "user");
    }

    /** {@hide} */
    public static File getDataUserCeDirectory(String volumeUuid, int userId) {
        return new File(getDataUserCeDirectory(volumeUuid), String.valueOf(userId));
    }

    /** {@hide} */
    public static File getDataUserCePackageDirectory(String volumeUuid, int userId,
            String packageName) {
        // TODO: keep consistent with installd
        return new File(getDataUserCeDirectory(volumeUuid, userId), packageName);
    }

    /** {@hide} */
    public static File getDataUserDeDirectory(String volumeUuid) {
        return new File(getDataDirectory(volumeUuid), "user_de");
    }

    /** {@hide} */
    public static File getDataUserDeDirectory(String volumeUuid, int userId) {
        return new File(getDataUserDeDirectory(volumeUuid), String.valueOf(userId));
    }

    /** {@hide} */
    public static File getDataUserDePackageDirectory(String volumeUuid, int userId,
            String packageName) {
        // TODO: keep consistent with installd
        return new File(getDataUserDeDirectory(volumeUuid, userId), packageName);
    }

    /**
     * Return preloads directory.
     * <p>This directory may contain pre-loaded content such as
     * {@link #getDataPreloadsDemoDirectory() demo videos} and
     * {@link #getDataPreloadsAppsDirectory() APK files} .
     * {@hide}
     */
    public static File getDataPreloadsDirectory() {
        return new File(getDataDirectory(), "preloads");
    }

    /**
     * @see #getDataPreloadsDirectory()
     * {@hide}
     */
    public static File getDataPreloadsDemoDirectory() {
        return new File(getDataPreloadsDirectory(), "demo");
    }

    /**
     * @see #getDataPreloadsDirectory()
     * {@hide}
     */
    public static File getDataPreloadsAppsDirectory() {
        return new File(getDataPreloadsDirectory(), "apps");
    }

    /**
     * @see #getDataPreloadsDirectory()
     * {@hide}
     */
    public static File getDataPreloadsMediaDirectory() {
        return new File(getDataPreloadsDirectory(), "media");
    }

    /**
     * Return the primary shared/external storage directory. This directory may
     * not currently be accessible if it has been mounted by the user on their
     * computer, has been removed from the device, or some other problem has
     * happened. You can determine its current state with
     * {@link #getExternalStorageState()}.
     * <p>
     * <em>Note: don't be confused by the word "external" here. This directory
     * can better be thought as media/shared storage. It is a filesystem that
     * can hold a relatively large amount of data and that is shared across all
     * applications (does not enforce permissions). Traditionally this is an SD
     * card, but it may also be implemented as built-in storage in a device that
     * is distinct from the protected internal storage and can be mounted as a
     * filesystem on a computer.</em>
     * <p>
     * On devices with multiple users (as described by {@link UserManager}),
     * each user has their own isolated shared storage. Applications only have
     * access to the shared storage for the user they're running as.
     * <p>
     * In devices with multiple shared/external storage directories, this
     * directory represents the primary storage that the user will interact
     * with. Access to secondary storage is available through
     * {@link Context#getExternalFilesDirs(String)},
     * {@link Context#getExternalCacheDirs()}, and
     * {@link Context#getExternalMediaDirs()}.
     * <p>
     * Applications should not directly use this top-level directory, in order
     * to avoid polluting the user's root namespace. Any files that are private
     * to the application should be placed in a directory returned by
     * {@link android.content.Context#getExternalFilesDir
     * Context.getExternalFilesDir}, which the system will take care of deleting
     * if the application is uninstalled. Other shared files should be placed in
     * one of the directories returned by
     * {@link #getExternalStoragePublicDirectory}.
     * <p>
     * Writing to this path requires the
     * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} permission,
     * and starting in {@link android.os.Build.VERSION_CODES#KITKAT}, read access requires the
     * {@link android.Manifest.permission#READ_EXTERNAL_STORAGE} permission,
     * which is automatically granted if you hold the write permission.
     * <p>
     * Starting in {@link android.os.Build.VERSION_CODES#KITKAT}, if your
     * application only needs to store internal data, consider using
     * {@link Context#getExternalFilesDir(String)},
     * {@link Context#getExternalCacheDir()}, or
     * {@link Context#getExternalMediaDirs()}, which require no permissions to
     * read or write.
     * <p>
     * This path may change between platform versions, so applications should
     * only persist relative paths.
     * <p>
     * Here is an example of typical code to monitor the state of external
     * storage:
     * <p>
     * {@sample development/samples/ApiDemos/src/com/example/android/apis/content/ExternalStorage.java
     * monitor_storage}
     *
     * @see #getExternalStorageState()
     * @see #isExternalStorageRemovable()
     */
    public static File getExternalStorageDirectory() {
        throwIfUserRequired();
        return sCurrentUser.getExternalDirs()[0];
    }

    /** {@hide} */
    public static File getLegacyExternalStorageDirectory() {
        return new File(System.getenv(ENV_EXTERNAL_STORAGE));
    }

    /** {@hide} */
    public static File getLegacyExternalStorageObbDirectory() {
        return buildPath(getLegacyExternalStorageDirectory(), DIR_ANDROID, DIR_OBB);
    }

    /**
     * Standard directory in which to place any audio files that should be
     * in the regular list of music for the user.
     * This may be combined with
     * {@link #DIRECTORY_PODCASTS}, {@link #DIRECTORY_NOTIFICATIONS},
     * {@link #DIRECTORY_ALARMS}, and {@link #DIRECTORY_RINGTONES} as a series
     * of directories to categories a particular audio file as more than one
     * type.
     */
    public static String DIRECTORY_MUSIC = "Music";

    /**
     * Standard directory in which to place any audio files that should be
     * in the list of podcasts that the user can select (not as regular
     * music).
     * This may be combined with {@link #DIRECTORY_MUSIC},
     * {@link #DIRECTORY_NOTIFICATIONS},
     * {@link #DIRECTORY_ALARMS}, and {@link #DIRECTORY_RINGTONES} as a series
     * of directories to categories a particular audio file as more than one
     * type.
     */
    public static String DIRECTORY_PODCASTS = "Podcasts";

    /**
     * Standard directory in which to place any audio files that should be
     * in the list of ringtones that the user can select (not as regular
     * music).
     * This may be combined with {@link #DIRECTORY_MUSIC},
     * {@link #DIRECTORY_PODCASTS}, {@link #DIRECTORY_NOTIFICATIONS}, and
     * {@link #DIRECTORY_ALARMS} as a series
     * of directories to categories a particular audio file as more than one
     * type.
     */
    public static String DIRECTORY_RINGTONES = "Ringtones";

    /**
     * Standard directory in which to place any audio files that should be
     * in the list of alarms that the user can select (not as regular
     * music).
     * This may be combined with {@link #DIRECTORY_MUSIC},
     * {@link #DIRECTORY_PODCASTS}, {@link #DIRECTORY_NOTIFICATIONS},
     * and {@link #DIRECTORY_RINGTONES} as a series
     * of directories to categories a particular audio file as more than one
     * type.
     */
    public static String DIRECTORY_ALARMS = "Alarms";

    /**
     * Standard directory in which to place any audio files that should be
     * in the list of notifications that the user can select (not as regular
     * music).
     * This may be combined with {@link #DIRECTORY_MUSIC},
     * {@link #DIRECTORY_PODCASTS},
     * {@link #DIRECTORY_ALARMS}, and {@link #DIRECTORY_RINGTONES} as a series
     * of directories to categories a particular audio file as more than one
     * type.
     */
    public static String DIRECTORY_NOTIFICATIONS = "Notifications";

    /**
     * Standard directory in which to place pictures that are available to
     * the user.  Note that this is primarily a convention for the top-level
     * public directory, as the media scanner will find and collect pictures
     * in any directory.
     */
    public static String DIRECTORY_PICTURES = "Pictures";

    /**
     * Standard directory in which to place movies that are available to
     * the user.  Note that this is primarily a convention for the top-level
     * public directory, as the media scanner will find and collect movies
     * in any directory.
     */
    public static String DIRECTORY_MOVIES = "Movies";

    /**
     * Standard directory in which to place files that have been downloaded by
     * the user.  Note that this is primarily a convention for the top-level
     * public directory, you are free to download files anywhere in your own
     * private directories.  Also note that though the constant here is
     * named DIRECTORY_DOWNLOADS (plural), the actual file name is non-plural for
     * backwards compatibility reasons.
     */
    public static String DIRECTORY_DOWNLOADS = "Download";

    /**
     * The traditional location for pictures and videos when mounting the
     * device as a camera.  Note that this is primarily a convention for the
     * top-level public directory, as this convention makes no sense elsewhere.
     */
    public static String DIRECTORY_DCIM = "DCIM";

    /**
     * Standard directory in which to place documents that have been created by
     * the user.
     */
    public static String DIRECTORY_DOCUMENTS = "Documents";

    /**
     * List of standard storage directories.
     * <p>
     * Each of its values have its own constant:
     * <ul>
     *   <li>{@link #DIRECTORY_MUSIC}
     *   <li>{@link #DIRECTORY_PODCASTS}
     *   <li>{@link #DIRECTORY_ALARMS}
     *   <li>{@link #DIRECTORY_RINGTONES}
     *   <li>{@link #DIRECTORY_NOTIFICATIONS}
     *   <li>{@link #DIRECTORY_PICTURES}
     *   <li>{@link #DIRECTORY_MOVIES}
     *   <li>{@link #DIRECTORY_DOWNLOADS}
     *   <li>{@link #DIRECTORY_DCIM}
     *   <li>{@link #DIRECTORY_DOCUMENTS}
     * </ul>
     * @hide
     */
    public static final String[] STANDARD_DIRECTORIES = {
            DIRECTORY_MUSIC,
            DIRECTORY_PODCASTS,
            DIRECTORY_RINGTONES,
            DIRECTORY_ALARMS,
            DIRECTORY_NOTIFICATIONS,
            DIRECTORY_PICTURES,
            DIRECTORY_MOVIES,
            DIRECTORY_DOWNLOADS,
            DIRECTORY_DCIM,
            DIRECTORY_DOCUMENTS
    };

    /**
     * @hide
     */
    public static boolean isStandardDirectory(String dir) {
        for (String valid : STANDARD_DIRECTORIES) {
            if (valid.equals(dir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a top-level shared/external storage directory for placing files of a
     * particular type. This is where the user will typically place and manage
     * their own files, so you should be careful about what you put here to
     * ensure you don't erase their files or get in the way of their own
     * organization.
     * <p>
     * On devices with multiple users (as described by {@link UserManager}),
     * each user has their own isolated shared storage. Applications only have
     * access to the shared storage for the user they're running as.
     * </p>
     * <p>
     * Here is an example of typical code to manipulate a picture on the public
     * shared storage:
     * </p>
     * {@sample development/samples/ApiDemos/src/com/example/android/apis/content/ExternalStorage.java
     * public_picture}
     *
     * @param type The type of storage directory to return. Should be one of
     *            {@link #DIRECTORY_MUSIC}, {@link #DIRECTORY_PODCASTS},
     *            {@link #DIRECTORY_RINGTONES}, {@link #DIRECTORY_ALARMS},
     *            {@link #DIRECTORY_NOTIFICATIONS}, {@link #DIRECTORY_PICTURES},
     *            {@link #DIRECTORY_MOVIES}, {@link #DIRECTORY_DOWNLOADS},
     *            {@link #DIRECTORY_DCIM}, or {@link #DIRECTORY_DOCUMENTS}. May not be null.
     * @return Returns the File path for the directory. Note that this directory
     *         may not yet exist, so you must make sure it exists before using
     *         it such as with {@link File#mkdirs File.mkdirs()}.
     */
    public static File getExternalStoragePublicDirectory(String type) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStoragePublicDirs(type)[0];
    }

    /**
     * Returns the path for android-specific data on the SD card.
     * @hide
     */
    public static File[] buildExternalStorageAndroidDataDirs() {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAndroidDataDirs();
    }

    /**
     * Generates the raw path to an application's data
     * @hide
     */
    public static File[] buildExternalStorageAppDataDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppDataDirs(packageName);
    }

    /**
     * Generates the raw path to an application's media
     * @hide
     */
    public static File[] buildExternalStorageAppMediaDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppMediaDirs(packageName);
    }

    /**
     * Generates the raw path to an application's OBB files
     * @hide
     */
    public static File[] buildExternalStorageAppObbDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppObbDirs(packageName);
    }

    /**
     * Generates the path to an application's files.
     * @hide
     */
    public static File[] buildExternalStorageAppFilesDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppFilesDirs(packageName);
    }

    /**
     * Generates the path to an application's cache.
     * @hide
     */
    public static File[] buildExternalStorageAppCacheDirs(String packageName) {
        throwIfUserRequired();
        return sCurrentUser.buildExternalStorageAppCacheDirs(packageName);
    }

    /**
     * Return the download/cache content directory.
     */
    public static File getDownloadCacheDirectory() {
        return DIR_DOWNLOAD_CACHE;
    }

    /**
     * Unknown storage state, such as when a path isn't backed by known storage
     * media.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_UNKNOWN = "unknown";

    /**
     * Storage state if the media is not present.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_REMOVED = "removed";

    /**
     * Storage state if the media is present but not mounted.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_UNMOUNTED = "unmounted";

    /**
     * Storage state if the media is present and being disk-checked.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_CHECKING = "checking";

    /**
     * Storage state if the media is present but is blank or is using an
     * unsupported filesystem.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_NOFS = "nofs";

    /**
     * Storage state if the media is present and mounted at its mount point with
     * read/write access.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_MOUNTED = "mounted";

    /**
     * Storage state if the media is present and mounted at its mount point with
     * read-only access.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_MOUNTED_READ_ONLY = "mounted_ro";

    /**
     * Storage state if the media is present not mounted, and shared via USB
     * mass storage.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_SHARED = "shared";

    /**
     * Storage state if the media was removed before it was unmounted.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_BAD_REMOVAL = "bad_removal";

    /**
     * Storage state if the media is present but cannot be mounted. Typically
     * this happens if the file system on the media is corrupted.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_UNMOUNTABLE = "unmountable";

    /**
     * Storage state if the media is in the process of being ejected.
     *
     * @see #getExternalStorageState(File)
     */
    public static final String MEDIA_EJECTING = "ejecting";

    /**
     * Returns the current state of the primary shared/external storage media.
     *
     * @see #getExternalStorageDirectory()
     * @return one of {@link #MEDIA_UNKNOWN}, {@link #MEDIA_REMOVED},
     *         {@link #MEDIA_UNMOUNTED}, {@link #MEDIA_CHECKING},
     *         {@link #MEDIA_NOFS}, {@link #MEDIA_MOUNTED},
     *         {@link #MEDIA_MOUNTED_READ_ONLY}, {@link #MEDIA_SHARED},
     *         {@link #MEDIA_BAD_REMOVAL}, or {@link #MEDIA_UNMOUNTABLE}.
     */
    public static String getExternalStorageState() {
        final File externalDir = sCurrentUser.getExternalDirs()[0];
        return getExternalStorageState(externalDir);
    }

    /**
     * @deprecated use {@link #getExternalStorageState(File)}
     */
    @Deprecated
    public static String getStorageState(File path) {
        return getExternalStorageState(path);
    }

    /**
     * Returns the current state of the shared/external storage media at the
     * given path.
     *
     * @return one of {@link #MEDIA_UNKNOWN}, {@link #MEDIA_REMOVED},
     *         {@link #MEDIA_UNMOUNTED}, {@link #MEDIA_CHECKING},
     *         {@link #MEDIA_NOFS}, {@link #MEDIA_MOUNTED},
     *         {@link #MEDIA_MOUNTED_READ_ONLY}, {@link #MEDIA_SHARED},
     *         {@link #MEDIA_BAD_REMOVAL}, or {@link #MEDIA_UNMOUNTABLE}.
     */
    public static String getExternalStorageState(File path) {
        final StorageVolume volume = StorageManager.getStorageVolume(path, UserHandle.myUserId());
        if (volume != null) {
            return volume.getState();
        } else {
            return MEDIA_UNKNOWN;
        }
    }

    /**
     * Returns whether the primary shared/external storage media is physically
     * removable.
     *
     * @return true if the storage device can be removed (such as an SD card),
     *         or false if the storage device is built in and cannot be
     *         physically removed.
     */
    public static boolean isExternalStorageRemovable() {
        if (isStorageDisabled()) return false;
        final File externalDir = sCurrentUser.getExternalDirs()[0];
        return isExternalStorageRemovable(externalDir);
    }

    /**
     * Returns whether the shared/external storage media at the given path is
     * physically removable.
     *
     * @return true if the storage device can be removed (such as an SD card),
     *         or false if the storage device is built in and cannot be
     *         physically removed.
     * @throws IllegalArgumentException if the path is not a valid storage
     *             device.
     */
    public static boolean isExternalStorageRemovable(File path) {
        final StorageVolume volume = StorageManager.getStorageVolume(path, UserHandle.myUserId());
        if (volume != null) {
            return volume.isRemovable();
        } else {
            throw new IllegalArgumentException("Failed to find storage device at " + path);
        }
    }

    /**
     * Returns whether the primary shared/external storage media is emulated.
     * <p>
     * The contents of emulated storage devices are backed by a private user
     * data partition, which means there is little benefit to apps storing data
     * here instead of the private directories returned by
     * {@link Context#getFilesDir()}, etc.
     * <p>
     * This returns true when emulated storage is backed by either internal
     * storage or an adopted storage device.
     *
     * @see DevicePolicyManager#setStorageEncryption(android.content.ComponentName,
     *      boolean)
     */
    public static boolean isExternalStorageEmulated() {
        if (isStorageDisabled()) return false;
        final File externalDir = sCurrentUser.getExternalDirs()[0];
        return isExternalStorageEmulated(externalDir);
    }

    /**
     * Returns whether the shared/external storage media at the given path is
     * emulated.
     * <p>
     * The contents of emulated storage devices are backed by a private user
     * data partition, which means there is little benefit to apps storing data
     * here instead of the private directories returned by
     * {@link Context#getFilesDir()}, etc.
     * <p>
     * This returns true when emulated storage is backed by either internal
     * storage or an adopted storage device.
     *
     * @throws IllegalArgumentException if the path is not a valid storage
     *             device.
     */
    public static boolean isExternalStorageEmulated(File path) {
        final StorageVolume volume = StorageManager.getStorageVolume(path, UserHandle.myUserId());
        if (volume != null) {
            return volume.isEmulated();
        } else {
            throw new IllegalArgumentException("Failed to find storage device at " + path);
        }
    }

    static File getDirectory(String variableName, String defaultPath) {
        String path = System.getenv(variableName);
        return path == null ? new File(defaultPath) : new File(path);
    }

    /** {@hide} */
    public static void setUserRequired(boolean userRequired) {
        sUserRequired = userRequired;
    }

    private static void throwIfUserRequired() {
        if (sUserRequired) {
            Log.wtf(TAG, "Path requests must specify a user by using UserEnvironment",
                    new Throwable());
        }
    }

    /**
     * Append path segments to each given base path, returning result.
     *
     * @hide
     */
    public static File[] buildPaths(File[] base, String... segments) {
        File[] result = new File[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = buildPath(base[i], segments);
        }
        return result;
    }

    /**
     * Append path segments to given base path, returning result.
     *
     * @hide
     */
    public static File buildPath(File base, String... segments) {
        File cur = base;
        for (String segment : segments) {
            if (cur == null) {
                cur = new File(segment);
            } else {
                cur = new File(cur, segment);
            }
        }
        return cur;
    }

    private static boolean isStorageDisabled() {
        return SystemProperties.getBoolean("config.disable_storage", false);
    }

    /**
     * If the given path exists on emulated external storage, return the
     * translated backing path hosted on internal storage. This bypasses any
     * emulation later, improving performance. This is <em>only</em> suitable
     * for read-only access.
     * <p>
     * Returns original path if given path doesn't meet these criteria. Callers
     * must hold {@link android.Manifest.permission#WRITE_MEDIA_STORAGE}
     * permission.
     *
     * @hide
     */
    public static File maybeTranslateEmulatedPathToInternal(File path) {
        return StorageManager.maybeTranslateEmulatedPathToInternal(path);
    }
}
