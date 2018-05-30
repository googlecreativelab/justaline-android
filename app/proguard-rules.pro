# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep FirebaseCrash#getInstance since it is called by reflection when Firebase is initialized.
-keep class com.google.firebase.crash.FirebaseCrash {
  public static com.google.firebase.crash.FirebaseCrash getInstance(com.google.firebase.FirebaseApp);
}

-dontwarn com.google.android.**
-dontwarn com.google.firebase.**

# Add this global rule
-keepattributes Signature

# This rule will properly ProGuard all the model classes in
# the package com.yourcompany.models. Modify to fit the structure
# of your app.
-keepclassmembers class com.arexperiments.justaline.model.** {
  *;
}

-keepclassmembers class javax.vecmath.** {
  *;
}

