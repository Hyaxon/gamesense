package com.ou.capstone.ingestion.historical.raw;

/** one row from a team's "schedule" list in Schedule.json */
public record RawScheduleEntry(
    String timestamp, // date as MM/dd/yyyy
    String
        location, // vs = this team is home, @ = this team is away, N = neutral site, BYE = no game
    String opponent, // opponent team name, blank for BYE rows
    String outcome, // W or L for played games, - for an unplayed game
    String pointsScored, // current team's score
    String pointsAllowed // opponents score
    ) {}
