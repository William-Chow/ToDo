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

# Line numbers, so that a crash in Play Console points at a line once the mapping file has put the
# names back. The source file name itself says nothing the mapping does not, so it is not kept.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The persisted todo list is Gson JSON, read back by field name and through a TypeToken. R8 renames
# fields and drops generic signatures, so without these a release build would deserialise every task
# to its defaults — quietly, since Gson has no field to complain about missing — and the user's list
# would come back empty on the first update rather than the build failing. Field names are the
# storage format: renaming one here is a migration, not a refactor.
-keepattributes Signature
-keep class com.menu.my.todo.model.** { <fields>; }

# WorkManager comes in with the ads SDK and is started at launch by androidx.startup. It opens its
# Room database by reflection on the generated WorkDatabase_Impl's no-argument constructor, which
# the rules work-runtime 2.10.0 and room-runtime 2.6.1 ship do not keep from this R8: the first
# shrunk release build crashed on every launch with NoSuchMethodException on WorkDatabase_Impl.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
