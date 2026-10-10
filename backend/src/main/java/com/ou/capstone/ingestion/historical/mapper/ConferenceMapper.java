package com.ou.capstone.ingestion.historical.mapper;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.ingestion.historical.raw.RawTeam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConferenceMapper {

    public List<Conference> map(List<RawTeam> rawTeams) {
        
        // set so each conference only shows up once
        Set<String> names = new LinkedHashSet<>();
        for (RawTeam raw : rawTeams) {

            // blank leagues are skipped here, TeamMapper already flags them as malformed
            if (raw.league() == null || raw.league().isBlank()) {
                continue;
            }
            names.add(raw.league().trim());
        }

        List<Conference> conferences = new ArrayList<>();
        for (String name : names) {
            conferences.add(new Conference(name, null)); // shortName isn't in the feed
        }
        return conferences;
    }
}