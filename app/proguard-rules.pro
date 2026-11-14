-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5
-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
}

# Shizuku: el optimizador llama a Shizuku.newProcess por reflexión
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**
