-dontwarn io.github.libxposed.**
-keep class io.github.libxposed.api.** { *; }
-keep class io.github.nicoenhance.NicoEnhance { *; }
-keep class org.luckypray.dexkit.** { *; }
-keep class io.github.nicoenhance.ModuleConfig { *; }
-keepclassmembers class io.github.nicoenhance.MainActivity {
    public boolean isSelfHooked();
}
