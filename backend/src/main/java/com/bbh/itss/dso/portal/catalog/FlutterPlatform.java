package com.bbh.itss.dso.portal.catalog;

public enum FlutterPlatform {
    APK, APPBUNDLE, IOS, MACOS, LINUX, WINDOWS, WEB;

    public String configValue() {
        return name().toLowerCase();
    }
}
