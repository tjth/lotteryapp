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
  
  cd $PREFIX
  cp  ~/bitcoinj-lotterycoin/slf4j-1.7.16/slf4j-simple-1.7.16.jar ~/bitcoinj-lotterycoin/core/target/bitcoinj-core-0.14-SNAPSHOT-bundled.jar LotteryEntry.java build_and_run_app.sh $PARTICIPANTS/$NEWDIR
  echo `cat run_app.sh` "$port $SEEDIP true true" >> $PARTICIPANTS/$NEWDIR/build_and_run_app.sh
  cd $PARTICIPANTS/$NEWDIR
  touch log
  ./build_and_run_app.sh > log 2>&1 & 

  cd $PREFIX
  
  echo "$NEWDIR created."
done
