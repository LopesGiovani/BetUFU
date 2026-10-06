package br.com.betufu.privacy.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class PrivacyRightsRepository {
  private final JdbcTemplate jdbc;

  PrivacyRightsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  CaseFact insertCase(
      PrivacyRightsModels.SubjectKind subjectKind,
      UUID subjectRef,
      UUID ownerIdentityId,
      PrivacyRightsModels.RequestKind requestKind,
      String proofReference,
      String verifiedChannel) {
    return jdbc.queryForObject(
        """
        INSERT INTO public.privacy_rights_case_fact
            (subject_kind, subject_ref, owner_identity_id, request_kind, proof_reference, verified_channel)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING case_id, subject_kind, subject_ref, owner_identity_id, request_kind, requested_at
        """,
        this::caseFact,
        subjectKind.name(),
        subjectRef,
        ownerIdentityId,
        requestKind.name(),
        proofReference,
        verifiedChannel);
  }

  void insertState(UUID caseId, String state, UUID eventId) {
    jdbc.update(
        "INSERT INTO public.privacy_rights_case_state(case_id,state,latest_event_id) VALUES (?,?,?)",
        caseId,
        state,
        eventId);
  }

  EventFact insertEvent(UUID caseId, String kind, UUID actorIdentityId) {
    return jdbc.queryForObject(
        "INSERT INTO public.privacy_rights_case_event(case_id,event_kind,actor_identity_id,occurred_at) VALUES (?,?,?,clock_timestamp()) RETURNING event_id,event_kind,occurred_at",
        this::eventFact,
        caseId,
        kind,
        actorIdentityId);
  }

  void updateState(UUID caseId, String state, UUID eventId) {
    jdbc.update(
        "INSERT INTO public.privacy_rights_case_state(state,latest_event_id,case_id) VALUES (?,?,?) ON CONFLICT (case_id) DO UPDATE SET state=EXCLUDED.state,latest_event_id=EXCLUDED.latest_event_id,rebuilt_at=clock_timestamp()",
        state,
        eventId,
        caseId);
  }

  EventFact insertResponseEvent(UUID caseId, String kind, UUID actor, String responseText) {
    return jdbc.queryForObject(
        "INSERT INTO public.privacy_rights_case_event(case_id,event_kind,actor_identity_id,event_payload,occurred_at) VALUES (?,?,?,jsonb_build_object('responseText',CAST(? AS text)),clock_timestamp()) RETURNING event_id,event_kind,occurred_at",
        this::eventFact,
        caseId,
        kind,
        actor,
        responseText);
  }

  Optional<CaseFact> lockCase(UUID caseId) {
    return jdbc
        .query(
            "SELECT case_id,subject_kind,subject_ref,owner_identity_id,request_kind,requested_at FROM public.privacy_rights_case_fact WHERE case_id=? FOR UPDATE",
            this::caseFact,
            caseId)
        .stream()
        .findFirst();
  }

  String historicalState(UUID caseId) {
    return jdbc
        .query(
            "SELECT event_kind FROM public.privacy_rights_case_event WHERE case_id=? ORDER BY occurred_at DESC,event_id DESC LIMIT 1",
            (rs, row) ->
                switch (rs.getString(1)) {
                  case "RESPONSE_SENT" -> "RESPONDED";
                  case "REJECTED" -> "REJECTED";
                  case "ANALYZED" -> "UNDER_REVIEW";
                  default -> "PENDING";
                },
            caseId)
        .stream()
        .findFirst()
        .orElse("PENDING");
  }

  Optional<CaseFact> findCase(UUID caseId) {
    return jdbc
        .query(
            "SELECT case_id,subject_kind,subject_ref,owner_identity_id,request_kind,requested_at FROM public.privacy_rights_case_fact WHERE case_id=?",
            this::caseFact,
            caseId)
        .stream()
        .findFirst();
  }

  Optional<String> state(UUID caseId) {
    return jdbc
        .query(
            "SELECT state FROM public.privacy_rights_case_state WHERE case_id=?",
            (rs, row) -> rs.getString(1),
            caseId)
        .stream()
        .findFirst();
  }

  List<TimelineItem> timeline(UUID caseId) {
    return jdbc.query(
        "SELECT event_kind,occurred_at,event_payload->>'responseText' FROM public.privacy_rights_case_event WHERE case_id=? ORDER BY occurred_at,event_id",
        (rs, row) ->
            new TimelineItem(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getString(3)),
        caseId);
  }

  private CaseFact caseFact(ResultSet rs, int row) throws SQLException {
    return new CaseFact(
        rs.getObject("case_id", UUID.class),
        PrivacyRightsModels.SubjectKind.valueOf(rs.getString("subject_kind")),
        rs.getObject("subject_ref", UUID.class),
        rs.getObject("owner_identity_id", UUID.class),
        PrivacyRightsModels.RequestKind.valueOf(rs.getString("request_kind")),
        rs.getTimestamp("requested_at").toInstant());
  }

  private EventFact eventFact(ResultSet rs, int row) throws SQLException {
    return new EventFact(
        rs.getObject("event_id", UUID.class),
        rs.getString("event_kind"),
        rs.getTimestamp("occurred_at").toInstant());
  }

  record CaseFact(
      UUID caseId,
      PrivacyRightsModels.SubjectKind subjectKind,
      UUID subjectRef,
      UUID ownerIdentityId,
      PrivacyRightsModels.RequestKind requestKind,
      Instant requestedAt) {}

  record EventFact(UUID eventId, String eventKind, Instant occurredAt) {}

  record TimelineItem(String eventKind, Instant occurredAt, String responseText) {}
}
