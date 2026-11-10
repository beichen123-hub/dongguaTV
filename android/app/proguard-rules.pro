# Gson 模型类保留（反序列化需要反射）
-keep class com.beichen.dongguatv.data.model.** { *; }

# Retrofit
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(); }

# Kotlin 协程
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
