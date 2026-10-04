package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ChatSchemaIT {

  private static final String RUNTIME_USER = "betufu_chat_runtime";
  private static final String RUNTIME_PASSWORD = "chat-schema-test-only";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:"
                  + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_chat_schema_test")
          .withUsername("betufu_chat_schema_owner")
          .withPassword("chat-owner-test-only");

  private static DriverManagerDataSource ownerSource;
  private static JdbcTemplate owner;
  private JdbcTemplate runtime;
  private UUID author;

  @BeforeAll
  static void configureDistinctRuntimeRole() {
    ownerSource = source(POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    owner = new JdbcTemplate(ownerSource);
    owner.execute("CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
    owner.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + RUNTIME_USER
            + "'");
  }

  @BeforeEach
  void migrateFreshIsolatedSchema() {
    owner.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    owner.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
    owner.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
            + RUNTIME_USER);
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO "
            + RUNTIME_USER);
    Flyway.configure().dataSource(ownerSource).locations("classpath:db/migration").load().migrate();
    runtime = new JdbcTemplate(source(RUNTIME_USER, RUNTIME_PASSWORD));
    author =
        owner.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            "https://example.invalid/realms/chat-schema",
            UUID.randomUUID().toString());
  }

  @Test
  void runtimeIsNeitherSchemaOwnerNorSuperuser() {
    assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo(RUNTIME_USER);
    assertThat(
            runtime.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class))
        .isFalse();
    assertThat(
            runtime.queryForObject(
                "SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'chat_message'",
                String.class))
        .isEqualTo(POSTGRESQL.getUsername());
  }

  @Test
  void globalIsSeededOnceAndCannotAliasAnEvent() {
    assertThat(
            runtime.queryForObject(
                "SELECT count(*) FROM public.chat_channel WHERE kind = 'GLOBAL' AND event_id IS NULL",
                Integer.class))
        .isEqualTo(1);
    assertSqlState(
        "23505", () -> runtime.update("INSERT INTO public.chat_channel (kind) VALUES ('GLOBAL')"));
    assertSqlState(
        "23514", () -> runtime.update("INSERT INTO public.chat_channel (kind) VALUES ('EVENT')"));
    UUID event = event();
    assertSqlState(
        "23514",
        () ->
            runtime.update(
                "INSERT INTO public.chat_channel (kind, event_id) VALUES ('GLOBAL', ?)", event));
    assertSqlState(
        "23514",
        () ->
            runtime.update(
                "INSERT INTO public.chat_channel (kind, event_id) VALUES ('UNKNOWN', ?)", event));
  }

  @Test
  void eventChannelsAreUniqueAndReferToRealEvents() {
    UUID event = event();
    UUID channel =
        runtime.queryForObject(
            "INSERT INTO public.chat_channel (kind, event_id) VALUES ('EVENT', ?) RETURNING channel_id",
            UUID.class,
            event);
    assertThat(channel).isNotNull().isNotEqualTo(event);
    assertSqlState(
        "23505",
        () ->
            runtime.update(
                "INSERT INTO public.chat_channel (kind, event_id) VALUES ('EVENT', ?)", event));
    assertSqlState(
        "23503",
        () ->
            runtime.update(
                "INSERT INTO public.chat_channel (kind, event_id) VALUES ('EVENT', ?)",
                UUID.randomUUID()));
    assertThat(
            runtime.queryForObject(
                "SELECT kind FROM public.chat_channel WHERE channel_id = ?",
                String.class,
                global()))
        .isEqualTo("GLOBAL");
  }

  @Test
  void messagesKeepExactTextAndEnforceAuthorAndChannelReferences() {
    String text = "Texto real <b>literal</b>\nSegunda linha \uD83D\uDE00";
    UUID message = message(global(), text);
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM public.chat_message WHERE message_id = ?", String.class, message))
        .isEqualTo(text);
    assertThat(
            runtime.queryForObject(
                "SELECT posted_at FROM public.chat_message WHERE message_id = ?",
                OffsetDateTime.class,
                message))
        .isNotNull();
    assertSqlState("23503", () -> message(UUID.randomUUID(), "canal inexistente"));
    assertSqlState(
        "23503",
        () ->
            runtime.update(
                "INSERT INTO public.chat_message (channel_id, author_identity_id, body) VALUES (?, ?, 'autor inexistente')",
                global(),
                UUID.randomUUID()));
    assertSqlState("23514", () -> message(global(), "   "));
    assertSqlState("23502", () -> message(global(), null));
  }

  @Test
  void grantsDenyRuntimeFactRewriteDeleteAndTruncate() {
    UUID message = message(global(), "original");
    for (String table : List.of("chat_channel", "chat_message")) {
      assertThat(
              runtime.queryForObject(
                  "SELECT has_table_privilege(current_user, ?, 'SELECT,INSERT')",
                  Boolean.class,
                  "public." + table))
          .isTrue();
      for (String privilege : List.of("UPDATE", "DELETE", "TRUNCATE")) {
        assertThat(
                runtime.queryForObject(
                    "SELECT has_table_privilege(current_user, ?, ?)",
                    Boolean.class,
                    "public." + table,
                    privilege))
            .isFalse();
      }
      assertSqlState("42501", () -> runtime.update("DELETE FROM public." + table));
      assertSqlState("42501", () -> runtime.execute("TRUNCATE public." + table + " CASCADE"));
    }
    assertSqlState(
        "42501",
        () ->
            runtime.update(
                "UPDATE public.chat_message SET body = 'reescrito' WHERE message_id = ?", message));
    assertSqlState("42501", () -> runtime.update("UPDATE public.chat_channel SET kind = 'EVENT'"));
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM public.chat_message WHERE message_id = ?", String.class, message))
        .isEqualTo("original");
  }

  @Test
  void triggersProtectFactsEvenWhenMutationGrantsAreAccidentallyRestored() {
    UUID message = message(global(), "original protegido");
    owner.execute(
        "GRANT UPDATE, DELETE ON public.chat_channel, public.chat_message TO " + RUNTIME_USER);
    assertSqlState(
        "P0001",
        () ->
            runtime.update(
                "UPDATE public.chat_message SET body = 'alterado' WHERE message_id = ?", message));
    assertSqlState("P0001", () -> runtime.update("DELETE FROM public.chat_message"));
    assertSqlState(
        "P0001", () -> runtime.update("UPDATE public.chat_channel SET created_at = now()"));
    assertSqlState("P0001", () -> runtime.update("DELETE FROM public.chat_channel"));
    assertSqlState("P0001", () -> owner.update("DELETE FROM public.chat_message"));
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM public.chat_message WHERE message_id = ?", String.class, message))
        .isEqualTo("original protegido");
  }

  @Test
  void projectionCanBeChangedAndRebuiltWithoutTouchingOriginals() {
    UUID message = message(global(), "fato para reconstruir");
    runtime.update("INSERT INTO public.chat_message_projection (message_id) VALUES (?)", message);
    runtime.update(
        "UPDATE public.chat_message_projection SET state = 'REMOVED' WHERE message_id = ?",
        message);
    assertThat(
            runtime.queryForObject(
                "SELECT state FROM public.chat_message_projection WHERE message_id = ?",
                String.class,
                message))
        .isEqualTo("REMOVED");
    runtime.update("DELETE FROM public.chat_message_projection");
    runtime.update(
        "INSERT INTO public.chat_message_projection (message_id) SELECT message_id FROM public.chat_message");
    assertThat(
            runtime.queryForObject(
                "SELECT state FROM public.chat_message_projection WHERE message_id = ?",
                String.class,
                message))
        .isEqualTo("VISIBLE");
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM public.chat_message WHERE message_id = ?", String.class, message))
        .isEqualTo("fato para reconstruir");
    assertSqlState(
        "23514",
        () -> runtime.update("UPDATE public.chat_message_projection SET state = 'UNKNOWN'"));
    assertSqlState(
        "23503",
        () ->
            runtime.update(
                "INSERT INTO public.chat_message_projection (message_id) VALUES (?)",
                UUID.randomUUID()));
  }

  @Test
  void keysetHandlesEqualTimestampsAndKeepsChannelsSeparate() {
    UUID channel = global();
    OffsetDateTime time = OffsetDateTime.parse("2026-10-04T12:00:00Z");
    for (int i = 1; i <= 5; i++) {
      runtime.update(
          "INSERT INTO public.chat_message (message_id, channel_id, author_identity_id, body, posted_at) VALUES (?, ?, ?, ?, ?)",
          new UUID(0, i),
          channel,
          author,
          "mensagem " + i,
          time);
    }
    UUID other =
        runtime.queryForObject(
            "INSERT INTO public.chat_channel (kind, event_id) VALUES ('EVENT', ?) RETURNING channel_id",
            UUID.class,
            event());
    message(other, "outro canal");
    List<UUID> first =
        runtime.queryForList(
            "SELECT message_id FROM public.chat_message WHERE channel_id = ? ORDER BY posted_at DESC, message_id DESC LIMIT 2",
            UUID.class,
            channel);
    List<UUID> next =
        runtime.queryForList(
            "SELECT message_id FROM public.chat_message WHERE channel_id = ? AND (posted_at, message_id) < (?, ?) ORDER BY posted_at DESC, message_id DESC LIMIT 2",
            UUID.class,
            channel,
            time,
            first.getLast());
    assertThat(first).containsExactly(new UUID(0, 5), new UUID(0, 4));
    assertThat(next).containsExactly(new UUID(0, 3), new UUID(0, 2));
    assertThat(
            runtime.queryForList(
                "SELECT message_id FROM public.chat_message WHERE channel_id = ? AND (posted_at, message_id) < (?, ?) ORDER BY posted_at DESC, message_id DESC",
                UUID.class,
                channel,
                time,
                new UUID(0, 1)))
        .isEmpty();
  }

  @Test
  void changeCursorSupportsIncrementalChannelReadsAndHasDedicatedIndexes() {
    UUID first = message(global(), "primeira");
    long cursor =
        runtime.queryForObject(
            "SELECT change_cursor FROM public.chat_message WHERE message_id = ?",
            Long.class,
            first);
    UUID second = message(global(), "segunda");
    assertThat(cursor).isPositive();
    assertThat(
            runtime.queryForList(
                "SELECT message_id FROM public.chat_message WHERE channel_id = ? AND change_cursor > ? ORDER BY change_cursor",
                UUID.class,
                global(),
                cursor))
        .containsExactly(second);
    assertThat(
            runtime.queryForList(
                "SELECT message_id FROM public.chat_message WHERE channel_id = ? AND change_cursor > ?",
                UUID.class,
                global(),
                Long.MAX_VALUE))
        .isEmpty();
    List<String> indexes =
        runtime.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'chat_message'",
            String.class);
    assertThat(indexes)
        .contains("idx_chat_message_keyset", "idx_chat_message_changes", "uq_chat_message_cursor");
    assertSqlState(
        "42501",
        () -> runtime.execute("SELECT setval('public.chat_message_change_cursor_seq', 1)"));
    assertSqlState(
        "428C9",
        () ->
            runtime.update(
                "INSERT INTO public.chat_message (channel_id, author_identity_id, body, change_cursor) VALUES (?, ?, 'cursor imposto', 0)",
                global(),
                author));
  }

  private UUID global() {
    return runtime.queryForObject(
        "SELECT channel_id FROM public.chat_channel WHERE kind = 'GLOBAL'", UUID.class);
  }

  private UUID message(UUID channel, String body) {
    return runtime.queryForObject(
        "INSERT INTO public.chat_message (channel_id, author_identity_id, body) VALUES (?, ?, ?) RETURNING message_id",
        UUID.class,
        channel,
        author,
        body);
  }

  private UUID event() {
    UUID competition =
        owner.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Chat fixture', ?) RETURNING competition_id",
            UUID.class,
            "chat-" + UUID.randomUUID());
    UUID edition =
        owner.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Chat edition', 'chat-edition') RETURNING edition_id",
            UUID.class,
            competition);
    return owner.queryForObject(
        "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Chat event', 'HEAD_TO_HEAD', now(), 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://example.invalid/chat') RETURNING event_id",
        UUID.class,
        competition,
        edition);
  }

  private static DriverManagerDataSource source(String user, String password) {
    return new DriverManagerDataSource(POSTGRESQL.getJdbcUrl(), user, password);
  }

  private static void assertSqlState(String state, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            error -> {
              Throwable cause = error;
              while (cause.getCause() != null) cause = cause.getCause();
              assertThat(cause).isInstanceOf(SQLException.class);
              assertThat(((SQLException) cause).getSQLState()).isEqualTo(state);
            });
  }
}
