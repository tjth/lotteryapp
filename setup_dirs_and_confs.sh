#! /bin/bash

if [ $# != 1 ]
then
  echo "Usage: $0 <number of participants>"
  exit -1
fi

PREFIX="/home/tjt12/lottery-app"

for i in `seq 1 $1`;
do
  NEWDIR=dir-$i
  mkdir $NEWDIR
  cd $NEWDIR
  mkdir data

  port=$((${i}+18445))
  rpcport=$((${i}+20222))
  args="-printtoconsole -datadir=$PREFIX/$NEWDIR/data -regtest -daemon -printtoconsole -listen -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test"
  com="$PREFIX/lotterycoin/src/bitcoind $args"
  
  echo  "$com" > run_daemon.sh
  chmod +x run_daemon.sh

  echo "$PREFIX/lotterycoin/src/bitcoin-cli -regtest -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test \$@" > bitcoin_command.sh
  chmod +x bitcoin_command.sh

  cd ../
  cp slf4j-simple-1.7.16.jar bitcoinj-core-0.14-SNAPSHOT-bundled.jar LotteryEntry.java $NEWDIR
  echo "$NEWDIR created."

  echo `cat app_build.sh` "$port" > $NEWDIR/app_build.sh
  chmod +x $NEWDIR/app_build.sh
done