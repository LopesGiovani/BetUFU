package br.com.betufu.sports.internal.importtabular;

public record ImportedRosterRow(
    int lineNumber,
    String organizationSlug,
    String sportSlug,
    String categorySlug,
    String athleteName,
    String matriculaCode,
    Integer jerseyNumber,
    String positionName) {}
