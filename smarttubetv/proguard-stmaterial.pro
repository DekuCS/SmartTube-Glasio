# MATERIAL: R8 rules for the stmaterial release build only (wired in build.gradle, flavor stmaterial).
#
# Names are NOT obfuscated (-dontobfuscate is in proguard-rules.pro and stays): the app reads private fields of Leanback,
# Fragment and ExoPlayer by name through Helpers.getField/setField, Gson/JsonPath/RegExp read model fields by name, and
# several native libraries call Java back by name. Shrinking and optimizing give the size win; renaming would add
# risk that a signed-out test cannot cover.

# --- Reflection by field name (Helpers.getField / setField) -------------------------------------------------------
# R8 would otherwise treat a field that is only written reflectively as a constant, or drop it.
-keepclassmembers class androidx.leanback.** { <fields>; }
-keepclassmembers class androidx.fragment.app.Fragment { <fields>; }
-keepclassmembers class androidx.recyclerview.widget.RecyclerView$ViewHolder { <fields>; }
-keepclassmembers class com.google.android.exoplayer2.** { <fields>; }
-keepclassmembers class com.liskovsoft.smartyoutubetv2.tv.ui.** extends androidx.fragment.app.Fragment { <fields>; }

# --- Gson / JsonPath / RegExp / QueryString models of MediaServiceCore ----------------------------------------------
# Read and written by field name. Classes named only in Retrofit's Call<T> are not reachable for R8.
# Classes are kept whole, not only the ones named models/data/result: Kotlin classes under `gen` and others are built by
# Gson too, and a signed-out run cannot show which of them a signed-in feed needs. This is the YouTube API layer only.
-keep class com.liskovsoft.youtubeapi.** { *; }
-keep class com.liskovsoft.googlecommon.** { *; }
-keepclasseswithmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keepclasseswithmembers class * { @com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath <fields>; }
-keepclasseswithmembers class * { @com.liskovsoft.googlecommon.common.converters.regexp.RegExp <fields>; }
# Gson (the version in use ships no consumer rules): anonymous TypeToken subclasses are read through their generic
# Signature, custom adapters and the Unsafe fallback by name.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class sun.misc.Unsafe { *; }
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }

# Retrofit interfaces: R8 strips the generic return type (Call<T>) of methods it does not keep, and Retrofit then cannot
# build the call adapter. Keep every interface that declares HTTP methods, with its methods and signatures.
-keep interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keepattributes Exceptions

# The converters read the annotations themselves (getAnnotations on Retrofit interfaces, model classes and fields), so the
# annotation types and the annotated classes must survive; otherwise R8 drops them and RetrofitHelper.create throws.
-keep @interface com.liskovsoft.googlecommon.common.converters.** { *; }
-keep @com.liskovsoft.googlecommon.common.converters.** class * { *; }
-keep @com.liskovsoft.googlecommon.common.converters.** interface * { *; }
-keepclassmembers class * { @com.liskovsoft.googlecommon.common.converters.** <fields>; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# --- J2V8 (JavaScript engine for signature / n-parameter decoding): JNI calls Java by name ------------------------------
-keep class com.eclipsesource.v8.** { *; }

# --- Native libraries that call back into Java --------------------------------------------------------------------
-keep class org.chromium.** { *; }
-keep class org.conscrypt.** { *; }
-keep class com.bumptech.glide.integration.webp.WebpImage { *; }
-keep class com.bumptech.glide.integration.webp.WebpFrame { *; }
-keepclasseswithmembernames class * { native <methods>; }

# --- ExoPlayer: extensions are loaded with Class.forName by DefaultRenderersFactory --------------------------------------
-keep class com.google.android.exoplayer2.ext.** { *; }
-keep class com.google.android.exoplayer2.source.sabr.** { *; }
-keep class com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.** { *; }

# --- Glide generated module, WorkManager workers ---------------------------------------------------------------------
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# --- Classes started by name ----------------------------------------------------------------------------------------
-keep class com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity

-dontwarn org.chromium.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn sun.net.spi.nameservice.**
-dontwarn org.xbill.DNS.spi.**

# Shrink only: no R8 optimization (inlining, merging, constant propagation). The app is full of reflection and signed-in
# paths that cannot be tested here; optimization changed behaviour in a way that was not found (see task report).
-dontoptimize
