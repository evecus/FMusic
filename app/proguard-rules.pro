# ── quickjs wrapper：JNI native 方法 + 反射注入 JS 的桥接类，禁止混淆/裁剪 ──
-keep class com.whl.quickjs.** { *; }
-dontwarn com.whl.quickjs.**

# JS 桥接层（UserApiRuntime / BuiltinSdkRuntime / ScriptHttp / UserApiController 等）
# 通过 wrapper 反射按方法名暴露给 JS，方法名不能被 R8 改写
-keep class cn.toside.music.compose.js.** { *; }

# ── 通用保护 ──
# JNI RegisterNatives 按名字绑定
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization（当前只用 JsonObject 核心 API；若将来加 @Serializable 数据类也能安全工作）
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class cn.toside.music.compose.**$$serializer { *; }
-keepclassmembers class cn.toside.music.compose.** { *** Companion; }

# WebView / JS interface 兜底（如有）
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

-dontwarn java.lang.invoke.**
-dontwarn org.slf4j.**

# jaudiotagger（音频标签写入，反射 + java.awt 可选路径）
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
