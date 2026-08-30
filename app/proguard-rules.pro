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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# The persisted todo list is Gson JSON, read back by field name and through a TypeToken. R8 renames
# fields and drops generic signatures, so a minified build would deserialise every task to its
# defaults — quietly, since Gson has no field to complain about missing — and the user's list would
# come back empty rather than the build failing. Minification is off today; these are here so that
# turning it on cannot be the thing that finds this out.
-keepattributes Signature
-keep class com.menu.my.todo.model.** { <fields>; }
