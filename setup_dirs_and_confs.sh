#! /bin/bash

if [ $# != 1 ]
then
  echo "Usage: $0 <number of participants>"
  exit -1
fi

PREFIX="/home/tjt12/lottery-app"
LOTTERYCOINLOC="/home/tjt12/lotterycoin"

for i in `seq 1 $1`;
do
  NEWDIR=dir-$i
  mkdir $NEWDIR
  cd $NEWDIR
  mkdir data

  port=$((${i}+18445))
  rpcport=$((${i}+20222))
  dbgport=$((${i}+30111))
  args="-printtoconsole -datadir=$PREFIX/$NEWDIR/data -regtest -daemon -printtoconsole -listen -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -acceptnonstdtxn"
  com="$LOTTERYCOINLOC/src/bitcoind $args"
  
  echo  "$com" > run_daemon.sh
  echo "$com -debug" > debug_daemon.sh
  chmod +x run_daemon.sh
  chmod +x debug_daemon.sh

  echo "$LOTTERYCOINLOC/src/bitcoin-cli -regtest -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -whitelist=127.0.0.1 \$@" > bitcoin_command.sh
  chmod +x bitcoin_command.sh

  echo "javac -cp slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar -g LotteryEntry.java" > debug_app.sh
  echo "java -cp slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar:app.jar:. -Xdebug -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:$dbgport,server=y,suspend=y LotteryEntry regtest $port" >> debug_app.sh

  echo "java -cp .:jline-1.0.jar:/usr/lib/jvm/java-1.8.0-openjdk-amd64/lib/tools.jar jline.ConsoleRunner com.sun.tools.example.debug.tty.TTY -sourcepath /home/tjt12/bitcoinj-lotterycoin/core/src/main/java:. -attach 127.0.0.1:$dbgport" > attach_debugger.sh
  
  chmod +x debug_app.sh  
  chmod +x attach_debugger.sh  

  cd ../
  cp jline-1.0.jar slf4j-simple-1.7.16.jar ~/bitcoinj-lotterycoin/core/target/bitcoinj-core-0.14-SNAPSHOT-bundled.jar LotteryEntry.java build_app.sh $NEWDIR
  echo "$NEWDIR created."

  echo `cat run_app.sh` "$port" > $NEWDIR/run_app.sh
  chmod +x $NEWDIR/run_app.sh
done
