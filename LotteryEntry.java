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
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.*;
import org.bitcoinj.signers.LocalTransactionSigner;
import org.bitcoinj.utils.BriefLogFormatter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
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
            System.err.println("Usage: LotteryEntry [regtest|testnet] [customPort?]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        String filePrefix;
        if (args[0].equals("testnet")) {
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
        kit = new WalletAppKit(params, new File("."), filePrefix);

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();



        // Make the wallet watch the lottery entry scripts
        ArrayList<Script> scriptList = new ArrayList<Script>();
        ScriptBuilder builder = new ScriptBuilder();
        Script script = builder.op(ScriptOpCodes.OP_BEACON).op(ScriptOpCodes.OP_EQUAL).build();
        scriptList.add(script);
        kit.wallet().addWatchedScripts(scriptList);

        Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
        System.out.println("My address is: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
         @Override
         public void run() {
          //spin while we have < 1 BTC
          System.out.println("Wallet balance: " + kit.wallet().getBalance());
          if (kit.wallet().getBalance().isLessThan(Coin.COIN)) {
            System.out.println("Not enough balance to enter lottery!");
            return;
          } 

          lotteryEntry();
           
         }
        }, 1000, 2*60*1000); 
    }

    private static void lotteryEntry() {
        try {
            Coin lotteryEntryCost = Coin.COIN;

            // Now send the entry!
            ScriptBuilder builder = new ScriptBuilder();
            Script script = builder.op(ScriptOpCodes.OP_BEACON).op(ScriptOpCodes.OP_EQUAL).build();
            TransactionOutput txoGuess = new TransactionOutput(params, null, lotteryEntryCost, script.getProgram());

            Transaction newtx = new Transaction(params);
            newtx.addOutput(txoGuess);
            
            // wallet will deal with inputs and signing
            Wallet.SendRequest req = Wallet.SendRequest.forTx(newtx);
            Wallet.SendResult sendResult = kit.wallet().sendCoins(req);

            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Sent entry onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                    //sleep for 15 seconds
                    try {
                      Thread.sleep(15000);
                    } catch (InterruptedException ignored) {}


                    for (TransactionOutput to : kit.wallet().calculateAllSpendCandidates(true, false)) {
                      System.out.println("Spend Candidates:");
                      System.out.println("DBG***** " + to.getParentTransactionHash() + " " + to.getIndex());
                    }
                      
                    claimWinnings();
                }
            }, MoreExecutors.sameThreadExecutor());
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

        
    private static void claimWinnings() {
        //try to claim all of the lottery winnings!
        //TODO:
        Address myAddress = kit.wallet().currentReceiveKey().toAddress(params);
        Random gen = new Random();
        //int r = gen.nextInt(10);
        int r = 1;
        for (TransactionOutput to : kit.wallet().calculateAllSpendCandidates(true, false)) {
          //construct input with my guess
          //construct output with my address
          //send transaction
          //handle error (guess is wrong)
          if (to.getScriptPubKey().isPayToScriptHash()) {
            System.out.println("hello****");
            continue;
          }

          System.out.println("Trying to claim: " + to.getParentTransactionHash() + " " + to.getIndex());
          System.out.println("With guess: " + r);
            
          ScriptBuilder b = new ScriptBuilder();
          Script claimScript = b.smallNum(r).build();

          Transaction claimTx = Transaction.lotteryGuessTransaction(params);
          claimTx.addInput(to.getParentTransactionHash(), to.getIndex(), claimScript); 
            
          TransactionOutput returnToMe = new TransactionOutput(
            params,
            null,
            to.getValue().subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE),
            kit.wallet().getChangeAddress()
          );
          claimTx.addOutput(returnToMe);
          

          Wallet.SendRequest req = Wallet.SendRequest.forTx(claimTx);
          Wallet.SendResult sendResult; 
          try {
            sendResult = kit.wallet().sendCoins(req);
          } catch (InsufficientMoneyException e) {
            throw new RuntimeException(e);
          }

          sendResult.broadcastComplete.addListener(new Runnable() {
            @Override
            public void run() {
              System.out.println("Sent out claim! Claim Transaction hash is " + sendResult.tx.getHashAsString() + "\n\n\n\n\n");
              //sleep for 30 seconds
              /*try {
                Thread.sleep(30000);
              } catch (InterruptedException ignored) {}
              System.out.println("Confidence: " + req.tx.getConfidence());
              for (Map.Entry<Sha256Hash, Integer> entry : req.tx.getAppearsInHashes().entrySet()) {
                System.out.println("In block: " + entry.getKey() + " " + entry.getValue());
              }
              System.out.println("\n\n\n\n");*/
            }
         }, MoreExecutors.sameThreadExecutor());
      }
    }
}

