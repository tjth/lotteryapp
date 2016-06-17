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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkNotNull;
import org.bitcoinj.core.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.core.listeners.WalletChangeEventListener;

public class LotteryEntry {
    private static WalletAppKit kit;
    private static NetworkParameters params;
    private static int bitsOfRandomness = 20;
    private static boolean autoenter = false;
    private static boolean autoclaim = false;  
    private static boolean alreadyEntered = false;
    private static boolean alreadyClaimed = false;

    public static void main(String[] args) throws Exception {
      BriefLogFormatter.init();
      if (args.length < 4) {
          System.err.println("Usage: LotteryEntry [customPort] [lotterySeed] [autoenter] [autoclaim]");
          return;
      }
 
      autoenter = args[2].equalsIgnoreCase("true");
      autoclaim = args[3].equalsIgnoreCase("true");

      // Figure out which network we should connect to. Each one gets its own set of files.
      String filePrefix;
      params = LotteryNetParams.get(Integer.parseInt(args[0]));
      filePrefix = "lottery-entry-lotterynet";

      // Start up a basic app using a class that automates some boilerplate.
      kit = new WalletAppKit(params, new File("."), filePrefix, true); //use lottery wallet
      String seed = args[1]; 
      InetAddress addr;
      try {
        addr = InetAddress.getByName(seed);
      } catch (UnknownHostException e) {
        System.err.println("Cannot connect to seed provided.");
        throw new RuntimeException(e);
      }
      kit.connectToLotteryNet(addr);

      // Download the block chain and wait until it's done.
      kit.startAsync();
      kit.awaitRunning();

      Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
      System.out.println("My address is: " + sendToAddress);
      writeAddressToFile(sendToAddress);

      if (autoenter) {
         kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                if (alreadyEntered) return;
                Coin value = tx.getValueSentToMe(w);
                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Received funds: entering the lottery!");
                        lotteryEntry();
                        alreadyEntered = true;
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                });
            }
        });
      }

      if (autoclaim) {
         kit.wallet().addChangeEventListener(new WalletChangeEventListener() {
            @Override
            public void onWalletChanged(Wallet w) {
              if (alreadyClaimed) return;

              int lastSeenHeight = w.getLastBlockSeenHeight();
              System.out.println("Last block wallet has seen: " + lastSeenHeight + ", current lottery: " + w.getCurrentLotteryStartBlock());
              if (lastSeenHeight >= w.getCurrentLotteryStartBlock() + w.getLotteryDelayPeriod() && w.getCurrentLotteryStartBlock() - w.getLotteryPeriod() > 0) {
                int rand = (int) (Math.random() * 100 + 1);
	        System.out.println("Block listener: in claiming period, claiming with guess " + rand);
                if(claimWinnings(rand))
		  alreadyClaimed = true;
	      }
            }
        });
      }

      handleCommands();
    }

    private static void handleCommands() {
      String commands = "height, updaterandomness x, quit, balance, claimable, prevclaimable, " +
                   "enter, claim x, help, candidates, prevcandidates";
      Scanner sc = new Scanner(System.in);
      String command = "";

      while(true) {
        System.out.println("\n\nEnter command:");
        System.out.println("Type \"help\" for list of commands and \"quit\" to exit.");

        command = sc.next();
        switch(command) {
         case "height" : prettyPrint("Height: " + kit.wallet().getLastBlockSeenHeight() + ""); break;

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
            prettyPrint("Commands: " + commands); 
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
            System.err.println("Unrecognized command.");
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
          throw new RuntimeException(e);
      }
    }

        
    private static boolean claimWinnings(int guess) {
      //try to claim all of the lottery winnings!
      if (kit.wallet().getBalance().isLessThan(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE)) {
        System.err.println("Not enough money to send off a transaction.");
        return false;
      }

      List<TransactionOutput> candidates = kit.wallet().calculateAllClaimCandidates(true);
      if (candidates == null) {
        System.err.println("Not in claiming period");
        return false;
      }
      if (candidates.size() == 0) {
        System.err.println("No current claim candidates.\n");
        return false;
      }

      int currentBlock = kit.wallet().getLastBlockSeenHeight();

      for (TransactionOutput to : candidates) {
        if (!to.getScriptPubKey().isLotteryEntry()) continue;

        System.out.print("Trying to claim tx: " + to.getParentTransactionHash() + ", output " + to.getIndex());
        System.out.println(", with guess: " + guess);
          
        Script guessScript = getGuessScript(guess);

        Transaction claimTx = Transaction.lotteryGuessTransaction(params);
        claimTx.addInput(to.getParentTransactionHash(), to.getIndex(), guessScript); 
        //arbitrarily set sequence number to 100 (not 0xff) so nLockTime is not ignored
        claimTx.getInputs().get(0).setSequenceNumber(100);
         
        //return change 
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
          System.err.println("Not enough money to send out claim!");
          return false;
        }

        sendResult.broadcastComplete.addListener(new Runnable() {
          @Override
          public void run() {
            System.out.println("Sent out claim! Claim Transaction hash is " + sendResult.tx.getHashAsString() + "\n\n\n\n\n");
          }
        }, MoreExecutors.sameThreadExecutor());
        
      }
      return true;
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
  private static Script getEntryScript() {
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

 
  /*
   * <guess>
   * <bits of randomness>
   * OP_FLEXIHASH
   * <bits of randomness>
   * OP_1
   */
  private static Script getGuessScript(int guess) {
    ScriptBuilder builder = new ScriptBuilder();

    //add the guess and then "1" for the first branch of the entry script
    Script script = builder.number(guess).number(bitsOfRandomness)
                           .op(ScriptOpCodes.OP_FLEXIHASH).number(bitsOfRandomness)
                           .smallNum(1).build();
    return script;
  }

  private static void writeAddressToFile(Address addr) {
    try {
      File fout = new File("address.txt");
      if (!fout.exists()) {
        fout.createNewFile();
      }

      FileOutputStream fos = new FileOutputStream(fout);
      BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
      bw.write(addr.toString());
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}

