/*
 * Class holding details of an entry into a lottery.
 *   hash - hash of the transaction the entry is an output of
 *   guess - integer that is committed to in the entry script
 *   date - datetime transaction was sent
 */

import org.bitcoinj.core.Sha256Hash;

import java.util.Date;

public class Entry {
  private Sha256Hash hash;
  private int guess;
  private Date date;

  public Entry(Sha256Hash hash, int guess, Date date) {
    this.hash = hash;
    this.guess = guess;
    this.date = date;
  }

  public int getGuess() { return guess; }
  public Date getDate() { return date; }
  public Sha256Hash getHash() { return hash; }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Entry)) return false;

    Entry other = (Entry) o;
    return (other.hash == this.hash && other.guess == this.guess && other.date == this.date);
  }

  @Override
  public String toString() {
    return new String("Entry: hash=" + hash + ", guess=" + guess + ", date=" + date.toString() + ".");
  }
}