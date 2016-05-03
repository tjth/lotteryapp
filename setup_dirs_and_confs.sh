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
  args="-printtoconsole -datadir=$PREFIX/$NEWDIR/data -regtest -daemon -printtoconsole -listen -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -acceptnonstdtxn"
  com="$LOTTERYCOINLOC/src/bitcoind $args"
  
  echo  "$com" > run_daemon.sh
  echo "$com -debug" > debug_daemon.sh
  chmod +x run_daemon.sh
  chmod +x debug_daemon.sh

  echo "$LOTTERYCOINLOC/src/bitcoin-cli -regtest -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -whitelist=127.0.0.1 \$@" > bitcoin_command.sh
  chmod +x bitcoin_command.sh

  cd ../
  cp slf4j-simple-1.7.16.jar ~/bitcoinj-lotterycoin/core/target/bitcoinj-core-0.14-SNAPSHOT-bundled.jar LotteryEntry.java build_app.sh debug_app.sh $NEWDIR
  echo "$NEWDIR created."

  echo `cat run_app.sh` "$port" > $NEWDIR/run_app.sh
  chmod +x $NEWDIR/run_app.sh
done
