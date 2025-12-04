javac --patch-module java.base="../../lib/wmsiotrace.jar:../../lib/iotrace.jar" -cp "../../../../../junit-platform-console-standalone-1.5.2.jar:../../lib/*:.:./lib/*" -d . FileReadServletTest.java

java  --patch-module java.base="../../lib/wmsiotrace.jar:../../lib/iotrace.jar" -cp "../../../../../junit-platform-console-standalone-1.5.2.jar:../../lib/*:.:./lib/*" org.junit.platform.console.ConsoleLauncher --select-class out.FileReadServletTest
