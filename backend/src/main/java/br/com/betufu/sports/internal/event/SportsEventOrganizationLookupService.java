package br.com.betufu.sports.internal.event;

import br.com.betufu.ranking.SportsEventOrganizationLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SportsEventOrganizationLookupService implements SportsEventOrganizationLookup {
  private final JdbcTemplate jdbc;

  public SportsEventOrganizationLookupService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<EventOrganizations> findEventOrganizations(UUID eventId) {
    return jdbc.query(
        """
            SELECT e.edition_id, e.phase_id,
                   home_team.organization_id AS home_organization_id,
                   away_team.organization_id AS away_organization_id
            FROM public.sports_event e
            LEFT JOIN public.sports_event_slot home_slot
              ON home_slot.event_id = e.event_id AND home_slot.slot_no = 1
            LEFT JOIN public.sports_team home_team ON home_team.team_id = home_slot.team_id
            LEFT JOIN public.sports_event_slot away_slot
              ON away_slot.event_id = e.event_id AND away_slot.slot_no = 2
            LEFT JOIN public.sports_team away_team ON away_team.team_id = away_slot.team_id
            WHERE e.event_id = ?
            """,
        rs ->
            rs.next()
                ? Optional.of(
                    new EventOrganizations(
                        rs.getObject("edition_id", UUID.class),
                        rs.getObject("phase_id", UUID.class),
                        rs.getObject("home_organization_id", UUID.class),
                        rs.getObject("away_organization_id", UUID.class)))
                : Optional.empty(),
        eventId);
  }
}
