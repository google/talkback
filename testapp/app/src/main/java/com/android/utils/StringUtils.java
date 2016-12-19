package com.android.utils;

public class StringUtils {

    public static boolean containsIgnoreCase(String where, String what) {
        if (where == null || what == null) {
            return false;
        }
        return where.toLowerCase().contains(what.toLowerCase());
    }
}