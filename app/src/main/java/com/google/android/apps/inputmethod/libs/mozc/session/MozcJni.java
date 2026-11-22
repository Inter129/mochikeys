package com.google.android.apps.inputmethod.libs.mozc.session;

/** libmozc.so가 RegisterNatives로 찾는 고정 경로. 이름·시그니처를 바꾸지 말 것. */
public final class MozcJni {
    static { System.loadLibrary("mozc"); }
    private MozcJni() {}
    public static native boolean initialize();
    public static native byte[] evalCommand(byte[] input);
    public static native boolean onPostLoad(String userProfileDirectory, String dataFilePath);
    public static native String getDataVersion();
}
