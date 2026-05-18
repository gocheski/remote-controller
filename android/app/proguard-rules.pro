-keep class com.google.protobuf.** { *; }
-keep class com.google.polo.wire.protobuf.** { *; }
-keep class remote.** { *; }

-keep class androidx.security.crypto.** { *; }
-keep class com.kire.remotecontroller.** { *; }

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

-keep class com.burgstaller.okhttp.digest.** { *; }

-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
