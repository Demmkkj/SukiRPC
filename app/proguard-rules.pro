# Conservative RC rules: SDK/JNI ABI is not available for full inspection.
-keep class dev.nuvio.presence.NativeRpc { *; }
-keep class com.discord.** { *; }
-keep class dadb.** { *; }
# Keep native entry points (including descriptor class names).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
