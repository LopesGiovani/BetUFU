package br.com.betufu.phase5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Phase5PrivacyIT {
  @Test
  void publicTicketVocabularyHasNoPrivateFields() {
    String source = br.com.betufu.betting.PredictionTicket.class.getRecordComponents()[0].getName();
    assertThat(source).isEqualTo("predictionId");
    assertThat(br.com.betufu.betting.PredictionTicket.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("email", "matricula", "hmac", "token", "role", "scope");
  }
}
