# Keep the bundled build toolchain reachable via reflection / direct calls.
-dontwarn com.android.tools.r8.**
-dontwarn org.eclipse.jdt.**
-keep class com.android.tools.r8.** { *; }
-keep class org.eclipse.jdt.core.compiler.batch.BatchCompiler { *; }
-dontwarn java.lang.invoke.**
-dontwarn javax.**
-dontwarn org.slf4j.**
