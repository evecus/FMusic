# quickjs wrapper 的 JNI 类由 native 方法注册，保持原样
-keep class com.whl.quickjs.** { *; }
-dontwarn com.whl.quickjs.**
