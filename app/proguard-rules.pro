# Keep model classes used with reflection-free code; nothing external required.
# View binding generated classes are kept automatically by AGP.

# Strip Android logging from release builds so nothing leaks to logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
