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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import static com.google.common.base.Preconditions.checkNotNull;
import org.bitcoinj.core.listeners.WalletCoinsReceivedEventListener;

public class LotteryEntry {
    private static WalletAppKit kit;
    private static NetworkParameters params;

    public static void main(String[] args) throws Exception {
      BriefLogFormatter.init();
      if (args.length < 1) {
          System.err.println("Usage: LotteryEntry [regtest|testnet] [customPort?] [lotterySeed?]");
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

      // Make the wallet watch the lottery entry and claim scripts
      ArrayList<Script> scriptList = new ArrayList<Script>();
      Script script = getEntryScript();
      scriptList.add(script);
      scriptList.addAll(getAllGuessScripts());
      kit.wallet().addWatchedScripts(scriptList);

      Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
      System.out.println("My address is: " + sendToAddress);

      System.out.println("Please enter a command.");
      System.out.println("Type \"help\" for list of commands and \"quit\" to exit.");

      Scanner sc = new Scanner(System.in);
      String command = "";
      while(true) {
        command = sc.next();
        switch(command) {
         case "quit" : return;
         case "balance": 
           prettyPrint("Current balance: " + kit.wallet().getBalance().toPlainString());
           break;
         case "claimable":
           prettyPrint("Claimable: " + kit.wallet().getClaimableBalance().toPlainString());
           break;
         case "enter" : lotteryEntry(); break;
         case "claim" : 
           int guess;
           try {
             guess = Integer.parseInt(sc.next());
            } catch (Exception e) {
              System.out.println("Please provide a valid guess for claiming."); 
              e.printStackTrace();
              continue;
            }
            claimWinnings(guess);
            break;
          case "help" : 
            prettyPrint("Commands: \"balance\", \"quit\", \"enter\", \"claim x\", \"candidates\""); 
            break;
          case "candidates" :
            prettyPrint("Spend candidates (other entries):");
            for (TransactionOutput o : kit.wallet().calculateAllClaimCandidates())
              System.out.println(o.getParentTransactionHash() + " " + o.getIndex());
            break;
          default:
            prettyPrint("Unrecognized command.");
        }
      }
    }

    private static void lotteryEntry() {
      try {
        Coin lotteryEntryCost = Coin.COIN;

        // Construct an entry script
        Script script = getEntryScript();

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

      List<TransactionOutput> candidates = kit.wallet().calculateAllClaimCandidates();
      if (candidates.size() == 0) {
        System.out.println("No current claim candidates.\n");
        return;
      }

      System.out.println("Spend Candidates:");
      for (TransactionOutput to : candidates) {
        System.out.println(to.getParentTransactionHash() + " " + to.getIndex());
      }

      int currentBlock = kit.wallet().getLastBlockSeenHeight();

      //Random gen = new Random();
      //int r = gen.nextInt(10);
      int r = guess;
      for (TransactionOutput to : candidates) {
        if (!to.getScriptPubKey().isLotteryEntry()) continue;

        System.out.println("Trying to claim: " + to.getParentTransactionHash() + " " + to.getIndex());
        System.out.println("With guess: " + r);
          
        Script guessScript = getGuessScript(r);

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
          throw new RuntimeException(e);
        }

        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
          @Override
          public void onSuccess(Transaction t) {
            System.out.println("Sent out claim! Claim Transaction hash is " + sendResult.tx.getHashAsString() + "\n\n\n\n\n");
          }

          @Override
          public void onFailure(Throwable t) {
            if (t instanceof RejectedTransactionException) {
              RejectedTransactionException rte = (RejectedTransactionException) t;
              System.out.println("WARNING: Transaction " + rte.getTransaction().getHash() + " rejected. Error:");
              System.out.println(rte.getRejectMessage().getRejectedMessage());
              System.out.println(rte.getRejectMessage().getReasonString());
            } else {
              System.out.println("WARNING: Transaction rejected for some reason.");
              t.printStackTrace();
            }
          }
       }, MoreExecutors.sameThreadExecutor());
    }
  }


  private static void prettyPrint(String s) {
    System.out.println("\n\n################\n" + s + "\n################");
  }

  /* 
    IF
      <now + 100 blocks> CHECKLOCKTIMEVERIFY DROP
      OP_BEACON
      OP_EQUAL
    ELSE
      <now + 102 blocks> CHECKLOCKTIMEVERIFY DROP
      OP_DUP
      OP_HASH160
      <Rollover PubKey HASH> 
      OP_EQUALVERIFY 
      OP_CHECKSIG
    ENDIF
  */
  private static Script getEntryScript() {
    ScriptBuilder builder = new ScriptBuilder();
    builder = builder.op(ScriptOpCodes.OP_IF);

    //beacon part
    int currentBlock;
    if (null == kit || null == kit.wallet()) {
      currentBlock = 0;
    } else {
      currentBlock = kit.wallet().getLastBlockSeenHeight();
    }

    builder = builder.number(currentBlock+100)
      .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY).op(ScriptOpCodes.OP_DROP);
    addBeaconPartOfScript(builder);

    builder = builder.op(ScriptOpCodes.OP_ELSE);

    //normal part
    builder = builder.number(currentBlock+102)
      .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY).op(ScriptOpCodes.OP_DROP);
    String rolloverAddressString = "n364gXEMN4PVjVw3JFAknijbuLjLjHn333"; //TODO: change this
    Address rolloverAddress = new Address(params, rolloverAddressString);
    builder = builder.addScript(ScriptBuilder.createOutputScript(rolloverAddress));

    builder = builder.op(ScriptOpCodes.OP_ENDIF);
    System.out.println(builder.build());
    return builder.build();
  }

  private static void addBeaconPartOfScript(ScriptBuilder builder) {
    builder.op(ScriptOpCodes.OP_BEACON).op(ScriptOpCodes.OP_EQUAL);
  }

  /*
    OP_X
    1
  */
  private static List<Script> getAllGuessScripts() {
    List<Script> scriptList = new ArrayList<Script>();
      for(int i = 1; i < 10; i++) {
        scriptList.add(getGuessScript(i));
      }
    return scriptList;
  }

  private static Script getGuessScript(int guess) {
    ScriptBuilder builder = new ScriptBuilder();
    //add the guess and then "1" for the first branch of the entry script
    Script script = builder.smallNum(guess).smallNum(1).build();
    return script;
  }
}

