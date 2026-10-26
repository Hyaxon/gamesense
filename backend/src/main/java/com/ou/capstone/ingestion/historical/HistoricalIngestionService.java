package com.ou.capstone.ingestion.historical;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.mapper.ConferenceMapper;
import com.ou.capstone.ingestion.historical.mapper.GameMapper;
import com.ou.capstone.ingestion.historical.mapper.GameMappingResult;
import com.ou.capstone.ingestion.historical.mapper.TeamMapper;
import com.ou.capstone.ingestion.historical.raw.RawFeed;
import com.ou.capstone.ingestion.historical.raw.RawTeam;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The entry point for the pipeline: read the file, map teams, map conferences, map games GameMapper
 * needs the finished Teams before it can do anything
 */
public class HistoricalIngestionService {
  private final HistoricalJsonReader reader = new HistoricalJsonReader();
  private final TeamMapper teamMapper = new TeamMapper();
  private final ConferenceMapper conferenceMapper = new ConferenceMapper();
  private final GameMapper gameMapper = new GameMapper();

  public IngestionResult ingest(Path path) throws IOException {
    return ingest(reader.read(path));
  }

  public IngestionResult ingest(RawFeed feed) {
    List<RawTeam> rawTeams = feed.teams() == null ? List.of() : feed.teams();
    List<String> skipped = new ArrayList<>();
    List<Team> teams = new ArrayList<>();
    List<RawTeam> validRawTeams = new ArrayList<>();
    Map<String, Team> teamsByName = new HashMap<>();

    // teams first, games need them
    for (RawTeam raw : rawTeams) {
      try {
        Team team = teamMapper.map(raw);
        if (teamsByName.containsKey(team.externalId())) {
          skipped.add("Duplicate team: " + team.externalId());
          continue;
        }
        teams.add(team);
        teamsByName.put(team.externalId(), team);
        validRawTeams.add(raw);
      } catch (MalformedRecordException e) {
        skipped.add(e.getMessage());
      }
    }

    List<Conference> conferences = conferenceMapper.map(validRawTeams);
    GameMappingResult gameResult = gameMapper.map(validRawTeams, teamsByName);
    skipped.addAll(gameResult.skipped());

    return new IngestionResult(teams, conferences, gameResult.games(), skipped);
  }
}
