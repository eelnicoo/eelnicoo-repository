# El código nativo llama a estos métodos por su nombre (JNI).
-keep interface dev.nico.mtsamp4.RemuxListener { *; }
-keepclassmembers class * implements dev.nico.mtsamp4.RemuxListener {
    public boolean onProgress(float);
    public void onInfo(java.lang.String, java.lang.String, long, int);
    public long localToUtcMs(int, int, int, int, int, int);
}
-keep class dev.nico.mtsamp4.NativeRemuxer {
    native <methods>;
}
