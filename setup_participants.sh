#! /bin/bash

if [ $# != 1 ]
then
  echo "Usage: $0 <number of participants>"
  exit -1
fi

PARTICIPANTS="participants"
PREFIX="/home/tjt12/lotteryapp"
LOTTERYCOINLOC="/home/tjt12/lotterycoin"

mkdir $PARTICIPANTS

SEEDIP="146.169.46.15"
numseeds=5

for i in `seq 1 $1`;
do
  cd $PARTICIPANTS
  NEWDIR=participant-$i
  mkdir $NEWDIR
  cd $NEWDIR

  node=$((${i}%${numseeds} + 1))
  port=$((${node}+18445))
  rpcport=$((${i}+20222))
  dbgport=$((${i}+30111))
  
  echo "javac -cp slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar:. -g LotteryEntry.java" > debug_app.sh
  #echo "java -cp slf4j-simple-1.7.16.jar:bitcoinj-core-0.14-SNAPSHOT-bundled.jar:app.jar:. -Xdebug -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:$dbgport,server=y,suspend=y LotteryEntry $port $SEEDIP" >> debug_app.sh

#  echo "java -cp .:jline-1.0.jar:/usr/lib/jvm/java-1.8.0-openjdk-amd64/lib/tools.jar jline.ConsoleRunner com.sun.tools.example.debug.tty.TTY -sourcepath /home/tjt12/bitcoinj-lotterycoin/core/src/main/java:. -attach 127.0.0.1:$dbgport" > attach_debugger.sh
  
#  chmod +x debug_app.sh  
 # chmod +x attach_debugger.sh  

  cd $PREFIX
  cp  ~/bitcoinj-lotterycoin/slf4j-1.7.16/slf4j-simple-1.7.16.jar ~/bitcoinj-lotterycoin/core/target/bitcoinj-core-0.14-SNAPSHOT-bundled.jar LotteryEntry.java build_and_run_app.sh $PARTICIPANTS/$NEWDIR
  echo `cat run_app.sh` "lotterynet $port $SEEDIP" >> $PARTICIPANTS/$NEWDIR/build_and_run_app.sh
  
  echo "$NEWDIR created."
done
