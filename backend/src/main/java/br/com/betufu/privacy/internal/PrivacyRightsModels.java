package br.com.betufu.privacy.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class PrivacyRightsModels {
  private PrivacyRightsModels() {}

  enum SubjectKind {
    ACCOUNT,
    ATHLETE_WITHOUT_ACCOUNT
  }

  enum RequestKind {
    NOTICE,
    ACCESS,
    CORRECTION,
    DELETION,
    OBJECTION,
    PSEUDONYMIZATION
  }

  record RequestCommand(
      SubjectKind subjectKind,
      UUID subjectRef,
      RequestKind requestKind,
      String proofReference,
      String verifiedChannel) {}

  record CaseReceipt(UUID caseId, String state, Instant requestedAt) {}

  record TimelineItem(String eventKind, Instant occurredAt, String responseText) {}

  record CaseView(
      UUID caseId,
      SubjectKind subjectKind,
      RequestKind requestKind,
      String state,
      Instant requestedAt,
      List<TimelineItem> timeline) {
    CaseView {
      timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
  }

  record ResponseReceipt(UUID caseId, String state, String eventKind, Instant occurredAt) {}
}
