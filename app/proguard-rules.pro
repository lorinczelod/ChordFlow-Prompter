# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.chordflow.prompter.** {
    *** Companion;
}
-keepclasseswithmembers class com.chordflow.prompter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
