javac -cp "./AdventNet/aws/lib/*" -d ./AdventNet/aws/src ./AdventNet/aws/src/*.java

jar -cvf ./AdventNet/aws/lib/abc.jar -C ./AdventNet/aws/src com

javac --patch-module java.base="./AdventNet/aws/lib/wmsiotrace.jar:./AdventNet/aws/lib/iotrace.jar" -cp "./AdventNet/aws/lib/*" -d . ./MainServer.java

jar uf ./AdventNet/aws/lib/abc.jar -C . MainServer.class

rm MainServer.class

java --patch-module java.base="./AdventNet/aws/lib/wmsiotrace.jar:./AdventNet/aws/lib/iotrace.jar" -cp "./AdventNet/aws/lib/*" MainServer
