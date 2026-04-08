# Add project specific ProGuard rules here.

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.vitbon.kkm.data.local.entity.** { *; }

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class com.vitbon.kkm.data.remote.dto.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
