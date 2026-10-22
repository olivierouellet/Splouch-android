# Keep rules for the release build (R8). The libraries ship their own; add here only what
# R8 cannot see on its own. Line numbers are kept so Play's deobfuscated stack traces
# point at the right line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
