//stuff for java --enable-native-access=com.github.codeplea.tinyexpr ...jar

//to get rid of:
/*
WARNING: A restricted method in java.lang.foreign.SymbolLookup has been called
WARNING: java.lang.foreign.SymbolLookup::libraryLookup has been called by com.valdisdot.util.jtinyexpr.TinyExpressionCompiler in an unnamed module (file:/home/valdisdot/Projects/java/NOW/jtinyexpr/target/classes/)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
*/

module com.github.codeplea.tinyexpr {
    requires java.base;
    exports com.valdisdot.util.jtinyexpr;
}