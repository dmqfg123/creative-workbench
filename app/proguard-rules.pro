# R8 规则（0.10.0 起 release 开 minify）

# 崩溃日志要能读：保留我们自己类的名字与行号
-keepnames class com.creativeworkbench.writer.** { *; }
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room：实体与数据库实现由注解处理器生成，名字不能被改
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.RoomDatabase { *; }

# kotlinx.serialization：@Serializable 的伴生序列化器
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep class com.creativeworkbench.writer.core.backup.**$$serializer { *; }
-keepclassmembers class com.creativeworkbench.writer.core.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.creativeworkbench.writer.core.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio 可选依赖在运行时缺失时的告警不重要
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
