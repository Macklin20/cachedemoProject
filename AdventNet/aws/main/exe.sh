javac --patch-module java.base="../lib/wmsiotrace.jar:../lib/iotrace.jar" -cp "../lib/*" -d . ./MainServer.java

jar uf ../lib/Custom.jar -C . MainServer.class

rm MainServer.class

java --patch-module java.base="../lib/wmsiotrace.jar:../lib/iotrace.jar" -cp "../lib/*" MainServer
