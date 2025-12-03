javac --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:." -d ./out com/zoho/*/*.java

java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.entities.test.URLFileTest


java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.usecases.test.ReadingContentTest

java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.controllers.test.FileReadServletTest

java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.controllers.test.MyCustomServletTest

java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.services.test.ReadingFromDiskTest

java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.services.test.ReadingFromInMemoryCacheTest


java --patch-module java.base="../lib/wmisotrace.jar:../lib/iotrace.jar" -cp "../lib/*:./out"  org.junit.platform.console.ConsoleLauncher --select-class com.zoho.services.test.StoreDataInMemoryCacheListTest

