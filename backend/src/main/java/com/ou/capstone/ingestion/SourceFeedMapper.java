package com.ou.capstone.ingestion;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.mapper.ConferenceMapper;
import com.ou.capstone.ingestion.mapper.GameMapper;
import com.ou.capstone.ingestion.mapper.GameMappingResult;
import com.ou.capstone.ingestion.mapper.TeamMapper;
import com.ou.capstone.ingestion.source.SourceFeed;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a source feed into canonical teams, conferences, and games. Both the historical and the
 * current-season path use this one mapper, so they produce the same canonical objects. Records that
 * cannot be mapped are skipped and reported in the result instead of failing the whole feed.
 */
@Component
public class SourceFeedMapper {
  private final TeamMapper teamMapper = new TeamMapper();
  private final ConferenceMapper conferenceMapper = new ConferenceMapper();
  private final GameMapper gameMapper = new GameMapper();

  // Maps teams first (games need them), then conferences, then games.
  public IngestionResult map(SourceFeed feed) {
    List<SourceTeam> sourceTeams = feed.teams() == null ? List.of() : feed.teams();
    List<String> skipped = new ArrayList<>();
    List<Team> teams = new ArrayList<>();
    List<SourceTeam> validSourceTeams = new ArrayList<>();
    Map<String, Team> teamsByName = new HashMap<>();

    for (SourceTeam sourceTeam : sourceTeams) {
      try {
        Team team = teamMapper.map(sourceTeam);
        if (teamsByName.containsKey(team.externalId())) {
          skipped.add("Duplicate team: " + team.externalId());
          continue;
        }
        teams.add(team);
        teamsByName.put(team.externalId(), team);
        validSourceTeams.add(sourceTeam);
      } catch (MalformedRecordException e) {
        skipped.add(e.getMessage());
      }
    }

    List<Conference> conferences = conferenceMapper.map(validSourceTeams);
    GameMappingResult gameResult = gameMapper.map(validSourceTeams, teamsByName);
    skipped.addAll(gameResult.skipped());

    return new IngestionResult(teams, conferences, gameResult.games(), skipped);
  }
}
