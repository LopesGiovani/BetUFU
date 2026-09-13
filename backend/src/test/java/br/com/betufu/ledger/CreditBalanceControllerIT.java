package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CreditBalanceControllerIT {
  @Test
  void anonymousBalanceAccessIsUnauthorized() {
    assertThat(HttpStatus.UNAUTHORIZED.value()).isEqualTo(401);
  }
}
