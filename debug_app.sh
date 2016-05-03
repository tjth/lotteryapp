javac -cp slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar -g LotteryEntry.java
jdb -sourcepath /home/tjt12/bitcoinj-lotterycoin/core/src/main/java:. -classpath slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar:app.jar:. LotteryEntry regtest 18446
