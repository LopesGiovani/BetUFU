package br.com.betufu.sports.internal.importtabular;

import java.time.LocalDate;

public record ImportedRosterRow(
    int lineNumber,
    String organizationSlug,
    String sportSlug,
    String categorySlug,
    String athleteName,
    String matriculaCode,
    Integer jerseyNumber,
    String positionName,
    LocalDate affiliationStartDate,
    LocalDate affiliationEndDate) {}
