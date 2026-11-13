# kotlinx.serialization ships its own rules; keep our serializable models' names stable anyway
# because backups written by one build must be readable by the next.
-keep @kotlinx.serialization.Serializable class com.nephrocare.** { *; }
-keepclassmembers class com.nephrocare.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
