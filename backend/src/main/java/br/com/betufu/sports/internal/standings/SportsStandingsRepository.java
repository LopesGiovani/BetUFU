package br.com.betufu.sports.internal.standings;

import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCreateRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsRow;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsSummary;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsUpdateRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsStandingsRepository {

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate namedJdbc;

  SportsStandingsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  record EditionContext(
      UUID competitionId, UUID editionId, String competitionName, String editionName) {}

  record StandingsRecord(
      UUID standingsId,
      UUID competitionId,
      UUID editionId,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      String name,
      String slug,
      String status,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt,
      String competitionName,
      String editionName,
      String sportName,
      String categoryName,
      String phaseName) {}

  record PublicationRecord(
      UUID publicationId,
      UUID standingsId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String standingsName,
      String competitionName,
      String editionName,
      String sportName,
      String categoryName,
      String phaseName,
      String sourceReference,
      String reason,
      Instant publishedAt,
      long baseLockVersion) {}

  Optional<EditionContext> findEditionContext(UUID editionId) {
    return jdbc
        .query(
            """
                SELECT e.competition_id, e.edition_id, c.name AS competition_name, e.name AS edition_name
                FROM public.sports_edition e
                JOIN public.sports_competition c ON c.competition_id = e.competition_id
                WHERE e.edition_id = ?
                """,
            (rs, rowNum) ->
                new EditionContext(
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("competition_name"),
                    rs.getString("edition_name")),
            editionId)
        .stream()
        .findFirst();
  }

  Optional<StandingsRecord> findStandingsRecord(UUID standingsId) {
    return jdbc
        .query(
            """
                SELECT s.standings_id, s.competition_id, s.edition_id, s.sport_id, s.category_id, s.phase_id,
                       s.name, s.slug, s.status, s.lock_version, s.created_at, s.updated_at,
                       c.name AS competition_name, e.name AS edition_name,
                       sp.name AS sport_name, cat.name AS category_name, p.name AS phase_name
                FROM public.sports_standings s
                JOIN public.sports_competition c ON c.competition_id = s.competition_id
                JOIN public.sports_edition e ON e.edition_id = s.edition_id
                LEFT JOIN public.sports_sport sp ON sp.sport_id = s.sport_id
                LEFT JOIN public.sports_category cat ON cat.category_id = s.category_id
                LEFT JOIN public.sports_phase p ON p.phase_id = s.phase_id
                WHERE s.standings_id = ?
                """,
            this::mapStandingsRecord,
            standingsId)
        .stream()
        .findFirst();
  }

  List<SportsStandingsSummary> listStandingsByEdition(UUID editionId) {
    return jdbc.query(
        """
                SELECT standings_id, competition_id, edition_id, sport_id, category_id, phase_id,
                       name, slug, status, lock_version, created_at, updated_at
                FROM public.sports_standings
                WHERE edition_id = ?
                ORDER BY name ASC
                """,
        (rs, rowNum) ->
            new SportsStandingsSummary(
                rs.getObject("standings_id", UUID.class),
                rs.getObject("competition_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("sport_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getObject("phase_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("status"),
                rs.getLong("lock_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))),
        editionId);
  }

  SportsStandingsSummary createStandings(
      UUID competitionId, UUID editionId, SportsStandingsCreateRequest request) {
    UUID standingsId = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO public.sports_standings (
                    standings_id, competition_id, edition_id, sport_id, category_id, phase_id,
                    name, slug, status, lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RASCUNHO', 0)
                """,
        standingsId,
        competitionId,
        editionId,
        request.sportId(),
        request.categoryId(),
        request.phaseId(),
        request.name().trim(),
        request.slug().trim());

    if (request.columns() != null && !request.columns().isEmpty()) {
      for (int i = 0; i < request.columns().size(); i++) {
        jdbc.update(
            """
                        INSERT INTO public.sports_standings_column (standings_id, column_order, label)
                        VALUES (?, ?, ?)
                        """,
            standingsId,
            i + 1,
            request.columns().get(i).trim());
      }
    }

    return findStandingsRecord(standingsId)
        .map(
            r ->
                new SportsStandingsSummary(
                    r.standingsId(),
                    r.competitionId(),
                    r.editionId(),
                    r.sportId(),
                    r.categoryId(),
                    r.phaseId(),
                    r.name(),
                    r.slug(),
                    r.status(),
                    r.lockVersion(),
                    r.createdAt(),
                    r.updatedAt()))
        .orElseThrow();
  }

  int updateStandingsMetadata(
      UUID standingsId, SportsStandingsUpdateRequest request, long expectedLockVersion) {
    return jdbc.update(
        """
                UPDATE public.sports_standings
                SET name = ?, slug = ?, sport_id = ?, category_id = ?, phase_id = ?,
                    lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE standings_id = ? AND lock_version = ?
                """,
        request.name().trim(),
        request.slug().trim(),
        request.sportId(),
        request.categoryId(),
        request.phaseId(),
        standingsId,
        expectedLockVersion);
  }

  int updateStandingsLockVersion(UUID standingsId, long expectedLockVersion) {
    return jdbc.update(
        """
                UPDATE public.sports_standings
                SET lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE standings_id = ? AND lock_version = ?
                """,
        standingsId,
        expectedLockVersion);
  }

  int setStandingsPublished(UUID standingsId, long expectedLockVersion) {
    return jdbc.update(
        """
                UPDATE public.sports_standings
                SET status = 'PUBLICADO', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE standings_id = ? AND lock_version = ?
                """,
        standingsId,
        expectedLockVersion);
  }

  List<String> findColumns(UUID standingsId) {
    return jdbc.query(
        """
                SELECT label
                FROM public.sports_standings_column
                WHERE standings_id = ?
                ORDER BY column_order ASC
                """,
        (rs, rowNum) -> rs.getString("label"),
        standingsId);
  }

  List<SportsStandingsRow> findRows(UUID standingsId) {
    List<RowData> rawRows =
        jdbc.query(
            """
                SELECT r.row_id, r.row_order, r.position, r.team_id, r.participant_name
                FROM public.sports_standings_row r
                WHERE r.standings_id = ?
                ORDER BY r.row_order ASC
                """,
            (rs, rowNum) ->
                new RowData(
                    rs.getObject("row_id", UUID.class),
                    rs.getInt("row_order"),
                    rs.getInt("position"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("participant_name")),
            standingsId);

    if (rawRows.isEmpty()) {
      return List.of();
    }

    List<UUID> rowIds = rawRows.stream().map(RowData::rowId).toList();
    MapSqlParameterSource params = new MapSqlParameterSource("rowIds", rowIds);
    Map<UUID, Map<Integer, String>> cellsByRow = new HashMap<>();

    namedJdbc.query(
        """
                SELECT row_id, column_order, cell_value
                FROM public.sports_standings_row_cell
                WHERE row_id IN (:rowIds)
                ORDER BY column_order ASC
                """,
        params,
        rs -> {
          UUID rowId = rs.getObject("row_id", UUID.class);
          int colOrder = rs.getInt("column_order");
          String val = rs.getString("cell_value");
          cellsByRow.computeIfAbsent(rowId, k -> new HashMap<>()).put(colOrder, val);
        });

    List<SportsStandingsRow> result = new ArrayList<>();
    for (RowData row : rawRows) {
      Map<Integer, String> cellMap = cellsByRow.getOrDefault(row.rowId(), Collections.emptyMap());
      List<String> values = new ArrayList<>();
      for (int col = 1; col <= cellMap.size(); col++) {
        values.add(cellMap.getOrDefault(col, ""));
      }
      result.add(
          new SportsStandingsRow(row.position(), row.teamId(), row.participantName(), values));
    }
    return result;
  }

  void replaceColumnsAndRows(
      UUID standingsId, List<String> columns, List<SportsStandingsRow> rows) {
    jdbc.update("DELETE FROM public.sports_standings_column WHERE standings_id = ?", standingsId);
    jdbc.update("DELETE FROM public.sports_standings_row WHERE standings_id = ?", standingsId);

    if (columns != null) {
      for (int i = 0; i < columns.size(); i++) {
        jdbc.update(
            """
                        INSERT INTO public.sports_standings_column (standings_id, column_order, label)
                        VALUES (?, ?, ?)
                        """,
            standingsId,
            i + 1,
            columns.get(i).trim());
      }
    }

    if (rows != null) {
      for (int r = 0; r < rows.size(); r++) {
        SportsStandingsRow row = rows.get(r);
        UUID rowId = UUID.randomUUID();
        jdbc.update(
            """
                        INSERT INTO public.sports_standings_row (
                            row_id, standings_id, row_order, position, team_id, participant_name
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
            rowId,
            standingsId,
            r + 1,
            row.position(),
            row.teamId(),
            row.participantName().trim());

        if (row.values() != null) {
          for (int c = 0; c < row.values().size(); c++) {
            jdbc.update(
                """
                                INSERT INTO public.sports_standings_row_cell (row_id, column_order, cell_value)
                                VALUES (?, ?, ?)
                                """,
                rowId,
                c + 1,
                row.values().get(c));
          }
        }
      }
    }
  }

  Optional<PublicationRecord> findLatestPublication(UUID standingsId) {
    return jdbc
        .query(
            """
                SELECT publication_id, standings_id, competition_id, edition_id, version_no,
                       standings_name, competition_name, edition_name, sport_name, category_name, phase_name,
                       source_reference, reason, published_at, base_lock_version
                FROM public.sports_standings_publication
                WHERE standings_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
            this::mapPublicationRecord,
            standingsId)
        .stream()
        .findFirst();
  }

  List<String> findPublicationColumns(UUID publicationId) {
    return jdbc.query(
        """
                SELECT label
                FROM public.sports_standings_publication_column
                WHERE publication_id = ?
                ORDER BY column_order ASC
                """,
        (rs, rowNum) -> rs.getString("label"),
        publicationId);
  }

  List<SportsStandingsRow> findPublicationRows(UUID publicationId) {
    List<PublicationRowData> rawRows =
        jdbc.query(
            """
                SELECT row_order, position, team_id, participant_name
                FROM public.sports_standings_publication_row
                WHERE publication_id = ?
                ORDER BY row_order ASC
                """,
            (rs, rowNum) ->
                new PublicationRowData(
                    rs.getInt("row_order"),
                    rs.getInt("position"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("participant_name")),
            publicationId);

    if (rawRows.isEmpty()) {
      return List.of();
    }

    Map<Integer, Map<Integer, String>> cellsByRowOrder = new HashMap<>();
    jdbc.query(
        """
                SELECT row_order, column_order, cell_value
                FROM public.sports_standings_publication_cell
                WHERE publication_id = ?
                ORDER BY row_order ASC, column_order ASC
                """,
        rs -> {
          int rowOrder = rs.getInt("row_order");
          int colOrder = rs.getInt("column_order");
          String val = rs.getString("cell_value");
          cellsByRowOrder.computeIfAbsent(rowOrder, k -> new HashMap<>()).put(colOrder, val);
        },
        publicationId);

    List<SportsStandingsRow> result = new ArrayList<>();
    for (PublicationRowData row : rawRows) {
      Map<Integer, String> cellMap =
          cellsByRowOrder.getOrDefault(row.rowOrder(), Collections.emptyMap());
      List<String> values = new ArrayList<>();
      for (int col = 1; col <= cellMap.size(); col++) {
        values.add(cellMap.getOrDefault(col, ""));
      }
      result.add(
          new SportsStandingsRow(row.position(), row.teamId(), row.participantName(), values));
    }
    return result;
  }

  UUID createPublication(
      StandingsRecord standings,
      UUID actorIdentityId,
      String sourceReference,
      String reason,
      long baseLockVersion,
      List<String> columns,
      List<SportsStandingsRow> rows) {

    UUID publicationId = UUID.randomUUID();
    long nextVersion =
        findLatestPublication(standings.standingsId()).map(p -> p.versionNo() + 1).orElse(1L);
    UUID supersedesId =
        nextVersion > 1
            ? findLatestPublication(standings.standingsId())
                .map(PublicationRecord::publicationId)
                .orElse(null)
            : null;

    jdbc.update(
        """
                INSERT INTO public.sports_standings_publication (
                    publication_id, standings_id, competition_id, edition_id, sport_id, category_id, phase_id,
                    version_no, standings_name, competition_name, edition_name, sport_name, category_name, phase_name,
                    source_reference, reason, published_by_identity_id, supersedes_publication_id, base_lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        publicationId,
        standings.standingsId(),
        standings.competitionId(),
        standings.editionId(),
        standings.sportId(),
        standings.categoryId(),
        standings.phaseId(),
        nextVersion,
        standings.name(),
        standings.competitionName(),
        standings.editionName(),
        standings.sportName(),
        standings.categoryName(),
        standings.phaseName(),
        sourceReference.trim(),
        reason != null ? reason.trim() : null,
        actorIdentityId,
        supersedesId,
        baseLockVersion);

    if (columns != null) {
      for (int i = 0; i < columns.size(); i++) {
        jdbc.update(
            """
                        INSERT INTO public.sports_standings_publication_column (publication_id, column_order, label)
                        VALUES (?, ?, ?)
                        """,
            publicationId,
            i + 1,
            columns.get(i).trim());
      }
    }

    if (rows != null) {
      for (int r = 0; r < rows.size(); r++) {
        SportsStandingsRow row = rows.get(r);
        jdbc.update(
            """
                        INSERT INTO public.sports_standings_publication_row (
                            publication_id, row_order, position, team_id, participant_name
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
            publicationId,
            r + 1,
            row.position(),
            row.teamId(),
            row.participantName().trim());

        if (row.values() != null) {
          for (int c = 0; c < row.values().size(); c++) {
            jdbc.update(
                """
                                INSERT INTO public.sports_standings_publication_cell (
                                    publication_id, row_order, column_order, cell_value
                                ) VALUES (?, ?, ?, ?)
                                """,
                publicationId,
                r + 1,
                c + 1,
                row.values().get(c));
          }
        }
      }
    }

    return publicationId;
  }

  boolean catalogEntitiesExist(
      UUID competitionId, UUID editionId, UUID sportId, UUID categoryId, UUID phaseId) {
    if (sportId != null) {
      Integer count =
          jdbc.queryForObject(
              "SELECT count(*) FROM public.sports_competition_sport "
                  + "WHERE competition_id = ? AND sport_id = ?",
              Integer.class,
              competitionId,
              sportId);
      if (count == null || count == 0) {
        return false;
      }
    }

    if (categoryId != null) {
      Integer count =
          jdbc.queryForObject(
              "SELECT count(*) FROM public.sports_category WHERE category_id = ? AND edition_id = ?",
              Integer.class,
              categoryId,
              editionId);
      if (count == null || count == 0) {
        return false;
      }
    }

    if (phaseId != null) {
      Integer count =
          jdbc.queryForObject(
              "SELECT count(*) FROM public.sports_phase WHERE phase_id = ? AND edition_id = ?",
              Integer.class,
              phaseId,
              editionId);
      if (count == null || count == 0) {
        return false;
      }
    }

    return true;
  }

  boolean teamsBelongToEdition(UUID editionId, Collection<UUID> teamIds) {
    if (teamIds == null || teamIds.isEmpty()) {
      return true;
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("editionId", editionId).addValue("teamIds", teamIds);

    Integer count =
        namedJdbc.queryForObject(
            """
                SELECT count(DISTINCT team_id)
                FROM public.sports_team
                WHERE edition_id = :editionId AND team_id IN (:teamIds)
                """,
            params,
            Integer.class);
    return count != null && count == teamIds.size();
  }

  private StandingsRecord mapStandingsRecord(ResultSet rs, int rowNum) throws SQLException {
    return new StandingsRecord(
        rs.getObject("standings_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getObject("sport_id", UUID.class),
        rs.getObject("category_id", UUID.class),
        rs.getObject("phase_id", UUID.class),
        rs.getString("name"),
        rs.getString("slug"),
        rs.getString("status"),
        rs.getLong("lock_version"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")),
        rs.getString("competition_name"),
        rs.getString("edition_name"),
        rs.getString("sport_name"),
        rs.getString("category_name"),
        rs.getString("phase_name"));
  }

  private PublicationRecord mapPublicationRecord(ResultSet rs, int rowNum) throws SQLException {
    return new PublicationRecord(
        rs.getObject("publication_id", UUID.class),
        rs.getObject("standings_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getLong("version_no"),
        rs.getString("standings_name"),
        rs.getString("competition_name"),
        rs.getString("edition_name"),
        rs.getString("sport_name"),
        rs.getString("category_name"),
        rs.getString("phase_name"),
        rs.getString("source_reference"),
        rs.getString("reason"),
        toInstant(rs.getTimestamp("published_at")),
        rs.getLong("base_lock_version"));
  }

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  private record RowData(
      UUID rowId, int rowOrder, int position, UUID teamId, String participantName) {}

  private record PublicationRowData(
      int rowOrder, int position, UUID teamId, String participantName) {}
}
