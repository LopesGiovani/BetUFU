package br.com.betufu.ledger;

/** Public, non-sensitive rejection raised when an atomic debit would overdraw an account. */
public final class InsufficientCreditException extends RuntimeException {

  public InsufficientCreditException() {
    super("insufficient credit balance");
  }
}
