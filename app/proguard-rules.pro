-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5
-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
}
