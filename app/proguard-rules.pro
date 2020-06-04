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


#表示混淆时不使用大小写混合类名

-dontusemixedcaseclassnames

#表示不跳过library中的非public的类

-dontskipnonpubliclibraryclasses

#打印混淆的详细信息

-verbose

-dontoptimize

##表示不进行校验,这个校验作用 在java平台上的

-dontpreverify

-ignorewarnings

#保证是独立的jar,没有任何项目引用,如果不写就会认为我们所有的代码是无用的,从而把所有的代码压缩掉,导出一个空的jar

-dontshrink

#保护泛型

-keepattributes Signature

-keepclassmembers class * implements android.os.Parcelable {
public static final android.os.Parcelable$Creator CREATOR;
}

-keepattributes *Annotation*
