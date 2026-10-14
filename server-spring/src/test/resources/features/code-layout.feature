Feature: The garden's code says what it is
  Since the HEART library serves everything on the wire, the garden server is
  its domain, its storage, and its declaration to the library. Its packages
  and files say so, and nothing it no longer uses is kept.

  Scenario: The declaration to HEART has its own package, and no package is named for the web
    Then the package "com.artie.gardenbed.declaration" holds "GardenProfile, GardenBeds, BedActs"
    And no garden package is named "com.artie.gardenbed.web"

  Scenario: How a bed is event-sourced is declared beside the domain
    Then "GARDEN_BEDS" is declared in the package "com.artie.gardenbed.domain"

  Scenario: The garden profile keeps only the vocabulary and the reasons
    Its relations and its reasons are tables, read by profile and whyAbsent,
    since a garden with frost declares one more of each. Decided 2026-10-14.
    Then "GardenProfile" declares exactly "profile, whyAbsent, relations, reasons"
