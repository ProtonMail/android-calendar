# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-dontwarn kotlin.**

-dontobfuscate
-dontoptimize
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Biweekly
-keep class biweekly.** { *; }

# Gson
#-keepclassmembers class me.proton.android.calendar.data.** { <fields>; }
#-keep class me.proton.android.calendar.data.** { <fields>; }

-dontwarn sun.misc.**
#-keep class sun.misc.Unsafe { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson: Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
#-dontnote retrofit2.Platform
#-dontwarn retrofit2.Platform$Java8
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class me.proton.android.calendar.**$$serializer { *; }
-keepclassmembers class me.proton.android.calendar.** {
    *** Companion;
}
-keepclasseswithmembers class me.proton.android.calendar.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class me.proton.core.**$$serializer { *; }
-keepclassmembers class me.proton.core.** {
    *** Companion;
}
-keepclasseswithmembers class me.proton.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

