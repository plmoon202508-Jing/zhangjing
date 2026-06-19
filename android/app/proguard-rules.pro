# 默认优化规则已由 proguard-android-optimize.txt 提供
# ZXing
-keep class com.google.zxing.** { *; }
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
