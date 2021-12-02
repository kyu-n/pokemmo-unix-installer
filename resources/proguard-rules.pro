-verbose

# Output a source map file
-printmapping build/mapping.txt

-keepattributes *Annotation*, InnerClasses, EnclosingMethod

# Output a source map file
-printmapping build/mapping.txt

-keep public class com.pokeemu.unix.UnixInstaller {
    public static void main(java.lang.String[]);
}

-dontwarn javax.**
-dontwarn net.sf.**
