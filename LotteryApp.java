/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.bitcoinj.core.*;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.LotteryNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.*;
import org.bitcoinj.signers.LocalTransactionSigner;
import org.bitcoinj.utils.BriefLogFormatter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import static com.google.common.base.Preconditions.checkNotNull;
import org.bitcoinj.core.listeners.WalletCoinsReceivedEventListener;

public class LotteryApp {
    private static WalletAppKit kit;
    private static NetworkParameters params;
    private static int bitsOfRandomness = 20;

    /* map from entries with a boolean indicating if it was the previous lottery or this one */
    private static HashSet<Entry> entries = new HashSet<Entry>();

    public static void main(String[] args) throws Exception {

      BriefLogFormatter.init();
      if (args.length < 1) {
          System.err.println("Usage: LotterApp [regtest|testnet] [customPort?] [lotterySeed?]");
          return;
      }

      // Figure out which network we should connect to. Each one gets its own set of files.
      String filePrefix;
      if (args[0].equals("lotterynet")) {
          if (args.length != 3) {
            System.err.println("Please supply a port and seed for lottery net!");
            return;
          }

          params = LotteryNetParams.get(Integer.parseInt(args[1]));
          filePrefix = "lottery-entry-lotterynet";
      } else if (args[0].equals("testnet")) {
          params = TestNet3Params.get();
          filePrefix = "lottery-entry-testnet";
      } else if (args[0].equals("regtest")) {
          if (args.length == 2) 
            params = RegTestParams.get(Integer.parseInt(args[1]));
          else 
            params = RegTestParams.get();
          filePrefix = "lottery-entry-regtest";
      } else {
          params = MainNetParams.get();
          filePrefix = "lottery-entry";
      }

      // Start up a basic app using a class that automates some boilerplate.
      kit = new WalletAppKit(params, new File("."), filePrefix, true); //use lottery wallet

      if (params == RegTestParams.get()) {
          // Regression test mode is designed for testing and development only, so there's no public network for it.
          // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
          kit.connectToLocalHost();
      } else if (params == LotteryNetParams.get()) {
          String seed = args[2]; 
          InetAddress addr;
          try {
            addr = InetAddress.getByName(seed);
          } catch (UnknownHostException e) {
            System.err.println("Cannot connect to seed provided.");
            throw new RuntimeException(e);
          }
          kit.connectToLotteryNet(addr);
      }

      // Download the block chain and wait until it's done.
      kit.startAsync();
      kit.awaitRunning();

      ///TODO: can we remove this?
      // Make the wallet watch the lottery entry and claim scripts
      //ArrayList<Script> scriptList = new ArrayList<Script>();
      //Script script = getEntryScript();
      //scriptList.add(script);
      //scriptList.addAll(getAllGuessScripts());
      //kit.wallet().addWatchedScripts(scriptList);

      Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
      System.out.println("My address is: " + sendToAddress);

      System.out.println("Please enter a command.");
      System.out.println("Type \"help\" for list of commands and \"quit\" to exit.");

      Scanner sc = new Scanner(System.in);
      String command = "";
      while(true) {
        System.out.println("\n\nEnter command:");
        command = sc.next();
        switch(command) {
         case "entries" :
           prettyPrint("Your entries:");
           for (Entry e : entries) {
             System.out.println(e);
           }
           break;
         case "height" : prettyPrint(kit.wallet().getLastBlockSeenHeight() + ""); break;
         case "updaterandomness" : 
           try {
             bitsOfRandomness = Integer.parseInt(sc.next());
            } catch (Exception e) {
              System.out.println("Please provide a valid number of bits of randomness."); 
              e.printStackTrace();
              continue;
            }
           prettyPrint("Updates randomness to: " + bitsOfRandomness); 
           break;
         case "quit" : return;
         case "balance": 
           prettyPrint("Current balance: " + kit.wallet().getBalance().toPlainString());
           break;
         case "claimable":
           prettyPrint("Claimable: " + kit.wallet().getClaimableBalance(false).toPlainString());
           break;
         case "prevclaimable":
           prettyPrint("Claimable: " + kit.wallet().getClaimableBalance(true).toPlainString());
           break;
         case "enter" :
           int eguess;
           try {
             eguess = Integer.parseInt(sc.next());
            } catch (Exception e) {
              System.out.println("Please provide a valid guess for entering the lottery."); 
              e.printStackTrace();
              continue;
            }
           enterLottery(eguess); 
           break;
         case "claim" : 
           int cguess;
           try {
             cguess = Integer.parseInt(sc.next());
            } catch (Exception e) {
              System.out.println("Please provide a valid guess for claiming (the same as you entered with."); 
              e.printStackTrace();
              continue;
            }
            claimWinnings(cguess);
            break;
          case "help" : 
            prettyPrint("Commands: \"balance\", \"quit\", \"enter x\", \"claim x\"," +
                     "\"candidates\", , \"prevcandidates\", \"height\", \"updaterandomness x\"," +
                     "\"help\", \"entries\""); 
            break;
          case "candidates" :
            prettyPrint("Spend candidates (other entries):");
            for (TransactionOutput o : kit.wallet().calculateAllClaimCandidates(false))
              System.out.println(o.getParentTransactionHash() + " " + o.getIndex());
            break;
          case "prevcandidates" :
            prettyPrint("Previous spend candidates (other entries):");
            List<TransactionOutput> candidates = kit.wallet().calculateAllClaimCandidates(true);
            if (null == candidates) {
              prettyPrint("Not in lottery claimable period.");
              break;
            }
            for (TransactionOutput o : candidates)
              System.out.println(o.getParentTransactionHash() + " " + o.getIndex());
            break;
          default:
            prettyPrint("Unrecognized command.");
        }
      }
    }

    private static void enterLottery(int guess) {
      try {
        Coin lotteryEntryCost = Coin.COIN;

        // Construct an entry script
        Script script = getEntryScript(guess);

        // Construct an entry
        TransactionOutput txoGuess = 
          new TransactionOutput(params, null, lotteryEntryCost, script.getProgram());
          
        Coin valueNeeded = Coin.COIN;
        valueNeeded = valueNeeded.add(txoGuess.getMinNonDustValue());
        valueNeeded = valueNeeded.add(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        if (kit.wallet().getBalance().isLessThan(valueNeeded)) {
          System.out.println("Not enough balance to enter lottery!");
          System.out.println("Needed balance: " + valueNeeded);
          System.out.println("Wallet balance: " + kit.wallet().getBalance());
          return;
        } 

        Transaction newtx = new Transaction(params);
        newtx.addOutput(txoGuess);
        
        // wallet will deal with inputs and signing
        Wallet.SendRequest req = Wallet.SendRequest.forTx(newtx);
        Wallet.SendResult sendResult = kit.wallet().sendCoins(req);

        sendResult.broadcastComplete.addListener(new Runnable() {
          @Override
          public void run() {
            System.out.println("Sent entry onwards! Transaction hash is " +
                       sendResult.tx.getHashAsString());
            Entry entry = new Entry(sendResult.tx.getHash(), guess, new Date());
            entries.add(entry);
          }
        }, MoreExecutors.sameThreadExecutor());
      } catch (KeyCrypterException | InsufficientMoneyException e) {
          // We don't use encrypted wallets in this example - can never happen.
          throw new RuntimeException(e);
      }
    }

        
    private static void claimWinnings(int guess) {
      //try to claim all of the lottery winnings!
      if (kit.wallet().getBalance().isLessThan(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE)) {
        System.out.println("Not enough money to send off a transaction.");
        return;
      }

      List<TransactionOutput> candidates = kit.wallet().calculateAllClaimCandidates(true);
      if (candidates == null) {
        System.out.println("Not in claiming period");
        return;
      }
      if (candidates.size() == 0) {
        System.out.println("No current claim candidates.\n");
        return;
      }

      System.out.println("Spend Candidates:");
      for (TransactionOutput to : candidates) {
        System.out.println(to.getParentTransactionHash() + " " + to.getIndex());
      }

      int currentBlock = kit.wallet().getLastBlockSeenHeight();

 
      for (TransactionOutput to : candidates) {
        if (!to.getScriptPubKey().isLotteryEntry()) continue;

        System.out.println("Trying to claim: " + to.getParentTransactionHash() + " " + to.getIndex());
        System.out.println("With guess: " + guess);
          
        int startBlock = Math.max(currentBlock-50, 5);
        Script guessScript = getGuessScript(guess, startBlock, startBlock+3);

        if (guessScript == null) {
          System.err.println("Haven't sent an entry with this guess before.");
          return;
        }

        Transaction claimTx = Transaction.lotteryGuessTransaction(params);
        claimTx.addInput(to.getParentTransactionHash(), to.getIndex(), guessScript); 
        //arbitrarily set sequence number to 100 (not 0xff)
        claimTx.getInputs().get(0).setSequenceNumber(100);
          
        TransactionOutput returnToMe = new TransactionOutput(
          params,
          null,
          to.getValue().subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE),
          kit.wallet().getChangeAddress()
        );
        claimTx.addOutput(returnToMe);
        claimTx.setLockTime(currentBlock);

        Wallet.SendRequest req = Wallet.SendRequest.forTx(claimTx);
        Wallet.SendResult sendResult; 
        try {
          sendResult = kit.wallet().sendCoins(req);
        } catch (InsufficientMoneyException e) {
          System.out.println("Not enough money to send out claim!");
          return;
        }

        sendResult.broadcastComplete.addListener(new Runnable() {
          @Override
          public void run() {
            System.out.println("Sent out claim! Claim Transaction hash is " + sendResult.tx.getHashAsString() + "\n\n\n\n\n");
          }
       }, MoreExecutors.sameThreadExecutor());
    }
  }


  private static void prettyPrint(String s) {
    System.out.println("\n\n################\n" + s + "\n################");
  }

  /* 
    IF
      <now + 104 blocks> CHECKLOCKTIMEVERIFY DROP
      <beacon start block>
      <beacon end block>
      OP_BEACON
      OP_EQUAL
    ELSE
      <now + 106 blocks> CHECKLOCKTIMEVERIFY DROP
      OP_DUP
      OP_HASH160
      <Rollover PubKey HASH> 
      OP_EQUALVERIFY 
      OP_CHECKSIG
    ENDIF
  */
  private static Script getEntryScript(int guess) {
    if (kit == null || kit.wallet() == null) {
      System.err.println("Cannot get entry script when wallet is null.");
      return null;
    }

    byte[] guessBytes = ByteBuffer.allocate(4).putInt(guess).array();
    ScriptBuilder builder = new ScriptBuilder();
    builder = builder.op(ScriptOpCodes.OP_IF);

    //beacon part
    int currentLottery = kit.wallet().getCurrentLotteryStartBlock();
    int lotteryPeriod = kit.wallet().getLotteryPeriod();
    int lotteryDelayTime = kit.wallet().getLotteryDelayPeriod();
    int lotteryClaimingPeriod = kit.wallet().getLotteryClaimingPeriod();

    builder = builder.number(currentLottery+lotteryPeriod+lotteryDelayTime)
      .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY).op(ScriptOpCodes.OP_DROP)
      .number(currentLottery+lotteryPeriod).number(currentLottery+lotteryPeriod+lotteryDelayTime-1);
    addBeaconPartOfScript(builder);
    builder = builder.data(guessBytes);

    builder = builder.op(ScriptOpCodes.OP_ELSE);

    //rollover part
    builder = builder.number(currentLottery+lotteryPeriod+lotteryDelayTime+lotteryClaimingPeriod)
      .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY).op(ScriptOpCodes.OP_DROP);
    String rolloverAddressString = "n364gXEMN4PVjVw3JFAknijbuLjLjHn333"; //TODO: change this
    Address rolloverAddress = new Address(params, rolloverAddressString);
    builder = builder.addScript(ScriptBuilder.createOutputScript(rolloverAddress));

    builder = builder.op(ScriptOpCodes.OP_ENDIF);
    return builder.build();
  }

  private static void addBeaconPartOfScript(ScriptBuilder builder) {
    builder.op(ScriptOpCodes.OP_BEACON).op(ScriptOpCodes.OP_EQUAL);
  }


  private static List<Script> getAllGuessScripts() {
    List<Script> scriptList = new ArrayList<Script>();
      for(int i = 1; i < 10; i++) {
        //scriptList.add(getGuessScript(i));
      }
    return scriptList;
  }

  /* 
   * <entry tx hash>
   * <plaintext guess>
   * <bits of randomness>
   * OP_FLEXIHASH
   * <bits of randomness>
   * OP_1
   */
  private static Script getGuessScript(int guess, int startBlock, int endBlock) {
    ScriptBuilder builder = new ScriptBuilder();
    //add the guess and then "1" for the first branch of the entry script
    Entry e = removeFirstEntryGivenGuess(guess, entries);

    if (e == null) {
      //haven't sent out a transaction with this guess before
      return null;
    }

    System.out.println("Found earliest entry: " + e);

    byte[] hashBytes = e.getHash().getBytes();

    Script script = builder.data(hashBytes).number(guess).number(bitsOfRandomness)
                           .op(ScriptOpCodes.OP_FLEXIHASH).number(bitsOfRandomness)
                           .smallNum(1).build();
    return script;
  }

  private static Entry removeFirstEntryGivenGuess(int guess, HashSet<Entry> entries) {
    Entry earliest = null;

    for (Entry e : entries) {
      if (e.getGuess() == guess) {
        if (earliest == null) {
          earliest = e;
          continue;
        }

        if (e.getDate().compareTo(earliest.getDate()) < 0) {
          earliest = e;
        }
      }
    }

    if (earliest != null) entries.remove(earliest);
    return earliest;
  }
}
