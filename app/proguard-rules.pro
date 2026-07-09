# ProGuard rules for FileBeacon
# This file specifies rules for code shrinking and obfuscation.

# Add default ProGuard rules for a new Android project.
-dontobfuscate
-dontpreverify

# Keep annotations, which are often used by libraries.
-keepattributes *Annotation*

# Keep enumeration classes from being stripped.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all classes that are part of the Android framework's interaction model.
# This includes Activities, Services, BroadcastReceivers, etc.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep necessary members of Activities and other components that are called by the framework.
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Keep Parcelable classes and their Creator field.
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep native methods.
-keepclasseswithmembernames class * {
    native <methods>;
}

# For ViewBinding, these rules ensure that the generated binding classes are kept.
-keepclassmembers class **.databinding.*Binding {
    public <init>(...);
}

# For NanoHTTPD, it's safest to keep the library classes to prevent issues
# with reflection or dynamic class loading.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

