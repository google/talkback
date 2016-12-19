package com.android;

public class PrimitiveUtils {
    public static int compare(int a, int b) {
        return a < b ? -1 : (a > b ? 1 : 0);
    }

    public static int compare(long a, long b) {
        return a < b ? -1 : (a > b ? 1 : 0);
    }
}
