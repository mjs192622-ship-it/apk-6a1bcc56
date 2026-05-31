# WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# Keep JavaScript interface
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }
-keepclassmembers class * extends com.google.firebase.messaging.FirebaseMessagingService {
    public void onMessageReceived(com.google.firebase.messaging.RemoteMessage);
    public void onNewToken(java.lang.String);
}

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep R8 compatible
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# Prevent stripping of parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}