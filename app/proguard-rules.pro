# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve debugging information in stacktraces
-keepattributes SourceFile,LineNumberTable

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.** { *; }
-keep class com.yourname.mymoney.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * implements androidx.sqlite.db.SupportSQLiteOpenHelper$Factory {
    <init>(...);
}

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn javax.annotation.**
-keepclassmembers class * extends okhttp3.OkHttpClient {
    <init>(...);
}

# Coroutines rules
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }
