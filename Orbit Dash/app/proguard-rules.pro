#LibGDX -----------------------------------------------------------------
-dontwarn javax.annotation.Nullable

-verbose

-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication

-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
-keep public class com.badlogic.gdx.graphics.Color { *; }

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# ParticleEmitter
-keepclassmembers class com.badlogic.gdx.graphics.g2d.ParticleEmitter {
    *** particles;
    boolean[] active;
}

#TikTok -----------------------------------------------------------------
-keep class com.tiktok.** { *; }
# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**
# Google Install Referrer
-keep class com.android.installreferrer.api.** { *; }
# Android Lifecycle
-keep class androidx.lifecycle.** { *; }


# Room (WorkManager з AdMob) -----------------------------------------------
# Старий room-runtime 2.2.5 тримає клас бази без конструктора; full mode R8 його викидає,
# і Room не може створити WorkDatabase_Impl через рефлексію — падіння на старті
-keep class * extends androidx.room.RoomDatabase { <init>(); }
