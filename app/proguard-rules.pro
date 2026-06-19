# Default ProGuard rules
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn javax.annotation.**
