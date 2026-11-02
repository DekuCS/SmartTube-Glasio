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
-keep class com.liskovsoft.youtubeapi.**.models.** { <fields>; <init>(); }
-keep class com.liskovsoft.youtubeapi.**.data.** { <fields>; <init>(); }
-keep class com.liskovsoft.youtubeapi.**.result.** { <fields>; <init>(); }
-keep class com.liskovsoft.googlecommon.common.models.** { <fields>; <init>(); }
-keepclassmembers class com.liskovsoft.youtubeapi.** { <fields>; <init>(); }
-keepclassmembers class com.liskovsoft.googlecommon.** { <fields>; <init>(); }
-keepclasseswithmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keepclasseswithmembers class * { @com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath <fields>; }
-keepclasseswithmembers class * { @com.liskovsoft.googlecommon.common.converters.regexp.RegExp <fields>; }
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
