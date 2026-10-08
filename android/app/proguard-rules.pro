# Squeezed test builds (minified buildType). The app ships no reflection except
# Retrofit service interfaces and kotlinx.serialization DTOs, so the keep
# surface is small and explicit.

# Retrofit: keep generic signatures and annotated service methods.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp/Okio platform detection notes.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.**

# kotlinx.serialization: keep generated serializers and @Serializable models.
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.caregiver.mobile.data.api.** { *; }
-keepclassmembers class com.caregiver.mobile.data.api.** {
    *** Companion;
}

# DataStore/protobuf runtime ships its own consumer rules; silence its notes.
-dontnote com.google.protobuf.**

# Drop verbose/debug logging in the squeezed build.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
