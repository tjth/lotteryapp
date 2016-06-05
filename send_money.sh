. bitcoin_command.sh generate 103

for d in /home/tjt12/lottery-app/participants/*/ ; do
  (cd "$d" && . /home/tjt12/lottery-app/seed_nodes/seed-daemon-1/bitcoin_command.sh sendtoaddress `cat address.txt` 5);
done

cd /home/tjt12/lottery-app/seed_nodes/seed-daemon-1/
. bitcoin_command.sh generate 1
