javac --patch-module java.base="./AdventNet/aws/lib/wmsiotrace.jar:./AdventNet/aws/lib/iotrace.jar" -cp "./AdventNet/aws/lib/*" -d . ./MainServer.java

jar uf ./AdventNet/aws/lib/Custom.jar -C . MainServer.class

rm MainServer.class

java --patch-module java.base="./AdventNet/aws/lib/wmsiotrace.jar:./AdventNet/aws/lib/iotrace.jar" -cp "./AdventNet/aws/lib/*" MainServer
