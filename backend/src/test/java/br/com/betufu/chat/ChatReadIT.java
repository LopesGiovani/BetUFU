package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-profile-controller-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(ChatReadIT.TestOidcConfiguration.class)
class ChatReadIT {
  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("chat-read-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Autowired JdbcTemplate jdbc;
  @Autowired WebApplicationContext context;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  FilterChainProxy filters;

  @Autowired ClientRegistrationRepository clients;
  @Autowired ObjectMapper mapper;
  MockMvc mvc;
  UUID author;
  UUID global;

  @BeforeEach
  void setup() {
    jdbc.execute(
        "TRUNCATE public.local_identity, public.sports_competition, public.chat_channel CASCADE");
    author =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid','chat-reader') RETURNING identity_id",
            UUID.class);
    jdbc.update(
        "INSERT INTO betufu_profile(identity_id,nickname) VALUES (?, 'torcedor_chat')", author);
    global =
        jdbc.queryForObject(
            "INSERT INTO chat_channel(kind) VALUES ('GLOBAL') RETURNING channel_id", UUID.class);
    mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
  }

  @Test
  void globalRequiresSessionForHistoryAndChangesIncludingAliases() throws Exception {
    message(global, "secret-global", UUID.randomUUID(), Instant.now());
    for (String path :
        new String[] {
          "/api/v1/chat/global",
          "/api/v1/chat/global/changes",
          "/api/v1/chat/events/" + global,
          "/api/v1/chat/events/" + global + "/changes"
        }) {
      String body =
          mvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isUnauthorized())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain("secret-global");
    }
  }

  @Test
  void inactiveAuthenticatedReaderGetsGlobalAndPrivateCache() throws Exception {
    jdbc.update(
        "INSERT INTO ufu_enrollment(identity_id,matricula_hmac,hmac_version,status) VALUES (?, decode(repeat('ab',32),'hex'), 1, 'LIBERADO')",
        author);
    message(global, "global-history", UUID.randomUUID(), Instant.now());
    mvc.perform(get("/api/v1/chat/global").with(user()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(jsonPath("$.kind").value("GLOBAL"))
        .andExpect(jsonPath("$.messages[0].text").value("global-history"));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM ufu_enrollment WHERE identity_id=?", String.class, author))
        .isEqualTo("LIBERADO");
  }

  @Test
  void globalVisitorReceives401WithoutAcceptHeader() throws Exception {
    mvc.perform(get("/api/v1/chat/global")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/chat/global/changes").accept(MediaType.TEXT_HTML))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void legacyRecoveryConvergesUnderConcurrentReads() throws Exception {
    UUID event = event(true);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var calls =
          java.util.stream.IntStream.range(0, 4)
              .mapToObj(
                  i ->
                      pool.submit(
                          () ->
                              mvc.perform(
                                      get("/api/v1/chat/events/" + event)
                                          .accept(MediaType.APPLICATION_JSON))
                                  .andExpect(status().isOk())
                                  .andReturn()))
              .toList();
      for (var call : calls) call.get(20, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_channel WHERE event_id=?", Integer.class, event))
        .isEqualTo(1);
  }

  @Test
  void draftAndUnpublishedStatusDoNotLeakOrCreateChannel() throws Exception {
    UUID draft = event(false);
    UUID bogus = event(false);
    jdbc.update("UPDATE sports_event SET status='PUBLICADO' WHERE event_id=?", bogus);
    for (UUID id : new UUID[] {draft, bogus, UUID.randomUUID()})
      mvc.perform(get("/api/v1/chat/events/" + id).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_channel WHERE kind='EVENT'", Integer.class))
        .isZero();
  }

  @Test
  void publicLegacyPublishedEventRecoversOneChannelIdempotently() throws Exception {
    UUID event = event(true);
    String url = "/api/v1/chat/events/" + event;
    for (int i = 0; i < 2; i++)
      mvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.kind").value("EVENT"))
          .andExpect(jsonPath("$.eventId").value(event.toString()));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_channel WHERE event_id=?", Integer.class, event))
        .isEqualTo(1);
  }

  @Test
  void nonemptyPublicDtoUsesAllowlistAndNeverIdentityClaims() throws Exception {
    UUID event = event(true);
    UUID channel = channel(event);
    message(channel, "Ol? chat ??", UUID.randomUUID(), Instant.parse("2026-10-04T12:00:00Z"));
    String body =
        mvc.perform(get("/api/v1/chat/events/" + event).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].nickname").value("torcedor_chat"))
            .andExpect(jsonPath("$.messages[0].participantId").value(author.toString()))
            .andExpect(jsonPath("$.messages[0].text").value("Ol? chat ??"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(mapper.readTree(body).path("messages").get(0).properties())
        .extracting(java.util.Map.Entry::getKey)
        .containsExactlyInAnyOrder(
            "messageId",
            "participantId",
            "nickname",
            "text",
            "postedAt",
            "removed",
            "changeCursor");
    assertThat(body)
        .doesNotContain(
            "issuer",
            "subject",
            "identityId",
            "enrollment",
            "email",
            "hmac",
            "chat-reader",
            "https://idp.invalid");
  }

  @Test
  void removedMessageProjectsMarkerWithoutOriginalOrParticipant() throws Exception {
    UUID event = event(true);
    UUID channel = channel(event);
    UUID id = UUID.randomUUID();
    message(channel, "RESTRICTED ORIGINAL", id, Instant.now());
    jdbc.update("INSERT INTO chat_message_projection(message_id,state) VALUES (?, 'REMOVED')", id);
    String body =
        mvc.perform(get("/api/v1/chat/events/" + event).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].removed").value(true))
            .andExpect(jsonPath("$.messages[0].text").isEmpty())
            .andExpect(jsonPath("$.messages[0].participantId").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("RESTRICTED ORIGINAL", "torcedor_chat", author.toString());
    assertThat(
            jdbc.queryForObject(
                "SELECT body FROM chat_message WHERE message_id=?", String.class, id))
        .isEqualTo("RESTRICTED ORIGINAL");
  }

  @Test
  void keysetSameTimestampAndChannelIsolation() throws Exception {
    UUID event = event(true);
    UUID channel = channel(event);
    Instant at = Instant.parse("2026-10-04T12:00:00Z");
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    message(channel, "low", low, at);
    message(channel, "high", high, at);
    message(global, "never-event", UUID.randomUUID(), at.plusSeconds(10));
    String first =
        mvc.perform(
                get("/api/v1/chat/events/" + event)
                    .param("limit", "1")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].messageId").value(high.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = mapper.readTree(first).path("historyCursor").asText();
    assertThat(cursor).isNotBlank();
    mvc.perform(
            get("/api/v1/chat/events/" + event)
                .param("limit", "1")
                .param("historyCursor", cursor)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].messageId").value(low.toString()))
        .andExpect(jsonPath("$.historyCursor").isEmpty());
    UUID other = event(true);
    mvc.perform(
            get("/api/v1/chat/events/" + other)
                .param("historyCursor", cursor)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changedCursorReturnsOnlyNewFactsAndCurrentRemovalMarker() throws Exception {
    UUID event = event(true);
    UUID channel = channel(event);
    UUID first = UUID.randomUUID();
    message(channel, "before", first, Instant.now());
    long cursor =
        jdbc.queryForObject(
            "SELECT change_cursor FROM chat_message WHERE message_id=?", Long.class, first);
    UUID second = UUID.randomUUID();
    message(channel, "new-but-removed", second, Instant.now());
    jdbc.update(
        "INSERT INTO chat_message_projection(message_id,state) VALUES (?, 'REMOVED')", second);
    message(global, "private-global", UUID.randomUUID(), Instant.now());
    mvc.perform(
            get("/api/v1/chat/events/" + event + "/changes")
                .param("changeCursor", Long.toString(cursor))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].messageId").value(second.toString()))
        .andExpect(jsonPath("$.messages[0].removed").value(true));
  }

  @Test
  void malformedCursorsAndBoundsFailClosed() throws Exception {
    UUID event = event(true);
    String path = "/api/v1/chat/events/" + event;
    for (String cursor : new String[] {"bad", "../private", "e30="})
      mvc.perform(get(path).param("historyCursor", cursor).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    for (String limit : new String[] {"0", "101", "-1"})
      mvc.perform(get(path).param("limit", limit).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    mvc.perform(
            get(path + "/changes").param("changeCursor", "-1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticatedGlobalAliasIs404AndNoWriteRouteExposed() throws Exception {
    mvc.perform(
            get("/api/v1/chat/events/" + global).with(user()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/chat/global").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor user() {
    return oidcLogin()
        .clientRegistration(clients.findByRegistrationId("keycloak"))
        .idToken(
            t ->
                t.claim(IdTokenClaimNames.ISS, "https://idp.invalid")
                    .claim(IdTokenClaimNames.SUB, "chat-reader"));
  }

  private UUID channel(UUID event) {
    return jdbc.queryForObject(
        "INSERT INTO chat_channel(kind,event_id) VALUES ('EVENT',?) RETURNING channel_id",
        UUID.class,
        event);
  }

  private void message(UUID channel, String text, UUID id, Instant at) {
    jdbc.update(
        "INSERT INTO chat_message(message_id,channel_id,author_identity_id,body,posted_at) VALUES (?,?,?,?,?)",
        id,
        channel,
        author,
        text,
        java.sql.Timestamp.from(at));
  }

  private UUID event(boolean published) {
    UUID competition =
        jdbc.queryForObject(
            "INSERT INTO sports_competition(name,slug) VALUES ('Chat fixture',?) RETURNING competition_id",
            UUID.class,
            "read-" + UUID.randomUUID());
    UUID edition =
        jdbc.queryForObject(
            "INSERT INTO sports_edition(competition_id,name,slug) VALUES (?, 'Edition', 'edition') RETURNING edition_id",
            UUID.class,
            competition);
    UUID event =
        jdbc.queryForObject(
            "INSERT INTO sports_event(competition_id,edition_id,title,event_format,scheduled_at,zone_id,venue_state,source_reference) VALUES (?,?,'Chat fixture','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid') RETURNING event_id",
            UUID.class,
            competition,
            edition);
    if (published)
      jdbc.execute(
          "BEGIN; UPDATE sports_event SET status='PUBLICADO' WHERE event_id='"
              + event
              + "'; WITH p AS (INSERT INTO sports_event_publication(event_id,competition_id,edition_id,version_no,competition_name,edition_name,title,event_format,scheduled_at,zone_id,venue_state,source_reference,published_by_identity_id,base_lock_version) VALUES ('"
              + event
              + "','"
              + competition
              + "','"
              + edition
              + "',1,'Fixture','Edition','Chat fixture','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid','"
              + author
              + "',0) RETURNING publication_id) INSERT INTO sports_event_publication_slot(publication_id,slot_no,display_label) SELECT publication_id,n,'Slot '||n FROM p CROSS JOIN generate_series(1,2) n; COMMIT;");
    return event;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-profile-controller-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Profile Controller IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
