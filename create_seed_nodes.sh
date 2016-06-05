#! /bin/bash

if [ $# != 1 ]
then
  echo "Usage: $0 <number of seed nodes>"
  exit -1
fi

for i in `seq 1 $1`;
do
  port=$((${i}+18445))
  CONNECTCMD="./bitcoin_command.sh addnode 127.0.0.1:$port onetry"
  echo "$CONNECTCMD" >> connect.sh
done 

chmod +x connect.sh

SEEDDIR="seed_nodes"
PREFIX="/home/tjt12/lottery-app"
LOTTERYCOINLOC="/home/tjt12/lotterycoin"

mkdir $SEEDDIR

for i in `seq 1 $1`;
do
  cd $SEEDDIR
  NEWDIR=seed-daemon-$i
  mkdir -p $NEWDIR
  cp ../connect.sh $NEWDIR
  cd $NEWDIR
  mkdir data

  port=$((${i}+18445))
  rpcport=$((${i}+20222))
  dbgport=$((${i}+30111))
  args="-printtoconsole -datadir=$PREFIX/$SEEDDIR/$NEWDIR/data -regtest -daemon -printtoconsole -listen -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -acceptnonstdtxn"
  com="$LOTTERYCOINLOC/src/bitcoind $args"
  
  touch log
  echo  "$com >> log" > run_daemon.sh
  echo "$com -debug >> log" > debug_daemon.sh
  chmod +x run_daemon.sh
  chmod +x debug_daemon.sh

  echo "$LOTTERYCOINLOC/src/bitcoin-cli -regtest -port=$port -rpcport=$rpcport -rpcuser=test -rpcpassword=test -whitelist=127.0.0.1 \$@" > bitcoin_command.sh
  chmod +x bitcoin_command.sh

  echo "$NEWDIR created."
  ./debug_daemon.sh
  cd $PREFIX
done

sleep 2

for i in `seq 1 $1`;
do
  cd ${SEEDDIR}"/seed-daemon-$i"
  ./connect.sh >> log
  cd $PREFIX
done
echo "Seed nodes connected."
rm connect.sh
echo "Completed."
