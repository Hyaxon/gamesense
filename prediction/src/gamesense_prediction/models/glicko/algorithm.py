# Xander Meadows
"""Standalone schedule demonstration; imports do not load data or prompt."""

import json
from collections import defaultdict
from pathlib import Path

from .glicko2 import (
    Team,
    inactive_rd_update,
    mu_to_rating,
    phi_to_rd,
    probability_prediction,
    rating_to_mu,
    rd_to_phi,
    update_rating,
)


def main(schedule_path: Path | None = None) -> None:
    """Run the interactive demo explicitly, outside model execution."""
    if schedule_path is None:
        schedule_path = Path(__file__).resolve().parents[4] / "Schedule.json"
    with schedule_path.open("r", encoding="utf-8") as file:
        data = json.load(file)

    """
    # Test: Print names of all teams
    for team in data["teams"]:
        print(team["name"])
    """

    """
    # Test: Print all games for each team
    for game in team["schedule"]:
        if game["outcome"] != "":
            print(
                game["timestamp"],
                game["opponent"],
                game["outcome"]
            )
    """

    """
    # Test: Create a team and print its initial Glicko-2 ratings
    team = Team("Michigan State Spartans")
    print(team.name)
    print(team.rating)
    print(team.rd)
    print(team.volatility)
    """

    teams = {}

    # Make a team object for each team in the JSON data
    for team in data["teams"]:
        name = team["name"]
        teams[name] = Team(name)
        """
        # Used in test below
        team_obj = Team(team["name"])
        team_objects.append(team_obj)
        """
    print("Loaded", len(teams), "teams")

    # Some schedule sources use short opponent codes instead of the canonical
    # names used in the teams list.
    team_aliases = {
        "WEBB": "Gardner-Webb Runnin' Bulldogs",
    }

    missing_opponents = sorted(
        {
            game["opponent"]
            for team in data["teams"]
            for game in team["schedule"]
            if game["outcome"] != ""
            and game["opponent"]
            and team_aliases.get(game["opponent"], game["opponent"]) not in teams
        }
    )
    for opponent_name in missing_opponents:
        teams[opponent_name] = Team(opponent_name)

    if missing_opponents:
        print(
            "Warning: initialized opponents without team records:",
            ", ".join(missing_opponents),
        )

    def get_team(name):
        canonical_name = team_aliases.get(name, name)
        try:
            return teams[canonical_name]
        except KeyError as error:
            raise KeyError(
                f"Unknown team '{name}'. Add it to team_aliases with its "
                "canonical schedule name."
            ) from error

    """
    # Test: Print all created team objects and their initial Glicko-2 ratings
    for team_obj in team_objects:
        print(team_obj.name)
        print(team_obj.rating)
        print(team_obj.rd)
        print(team_obj.volatility)
        print()  # Add a blank line between teams for readability
    """

    games = []
    set_games = set()

    # Collecting a list of all unique games
    for team in data["teams"]:
        team_name = team["name"]
        for game in team["schedule"]:
            # Skip days without games ("")
            if game["outcome"] != "":
                opponent_name = game["opponent"]
                timestamp = game["timestamp"]
                game_tuple = (timestamp, tuple(sorted([team_name, opponent_name])))
                if game_tuple not in set_games:
                    set_games.add(game_tuple)
                    if game["outcome"] == "W":
                        result = 1
                    elif game["outcome"] == "L":
                        result = 0
                    else:
                        result = 0.5
                    games.append(
                        {
                            "timestamp": timestamp,
                            "team_a": team_name,
                            "team_b": opponent_name,
                            "result": result,
                        }
                    )

    print("Loaded", len(games), "unique games")

    games_by_month = defaultdict(list)

    for game in games:
        month, day, year = game["timestamp"].split("/")
        month_key = f"{year}-{month}"
        games_by_month[month_key].append(game)

    months = sorted(games_by_month.keys())

    print("\nRating periods:")

    for month in months:
        print(month, ":", len(games_by_month[month]), "games")

    print("==============================")

    # Now to process each month
    for month in months:
        monthly_games = games_by_month[month]

        print("PROCESSING", month)

        # Store each team's games for this rating period.
        team_games = defaultdict(list)

        for game in monthly_games:
            team_a = get_team(game["team_a"])
            team_b = get_team(game["team_b"])

            result_a = game["result"]
            result_b = 1.0 - result_a

            # Team A's perspective
            team_games[team_a.name].append({"opponent": team_b, "result": result_a})

            # Team B's perspective
            team_games[team_b.name].append({"opponent": team_a, "result": result_b})

        # Snapshot ratings so all updates in a rating period use the same
        # pre-period opponent values.
        period_ratings = {
            team_name: (team.rating, team.rd) for team_name, team in teams.items()
        }

        # --------------------------------------------------
        # UPDATE EVERY TEAM
        # --------------------------------------------------

        for team_name, team in teams.items():
            games_this_month = team_games[team_name]

            if len(games_this_month) == 0:
                # No games this month = increase RD due to inactivity.
                team.rd = phi_to_rd(inactive_rd_update(rd_to_phi(team.rd), 0))
                continue

            results = []
            for game in games_this_month:
                opponent = game["opponent"]
                opponent_rating, opponent_rd = period_ratings[opponent.name]

                results.append(
                    {
                        "opponent_mu": rating_to_mu(opponent_rating),
                        "opponent_phi": rd_to_phi(opponent_rd),
                        "result": game["result"],
                    }
                )

            # Glicko-2 update. Assign the returned values; update_rating does
            # not mutate the Team object itself.
            updated_mu, updated_phi = update_rating(
                rating_to_mu(team.rating),
                rd_to_phi(team.rd),
                [r["opponent_mu"] for r in results],
                [r["opponent_phi"] for r in results],
                [r["result"] for r in results],
            )
            team.rating = mu_to_rating(updated_mu)
            team.rd = phi_to_rd(updated_phi)

    """
    print("\n==============================")
    print("FINAL RANKINGS")
    print("==============================")

    for rank, team in enumerate(rankings, start=1):

        print(
            f"{rank:3}. "
            f"{team.name:40} "
            f"Rating: {team.rating:8.2f} "
            f"RD: {team.rd:7.2f} "
            f"Vol: {team.volatility:.5f}"
        )
    """
    #print("==============================")

    # And then get a prediction.
    # Read two teams and get a prediction.
    team_a_name = input("Enter the name of Team A: ")
    if team_a_name not in teams:
        print("Team A name is invalid.")
        exit()

    team_b_name = input("Enter the name of Team B: ")
    if team_b_name not in teams:
        print("Team B name is invalid.")
        exit()

    team_a = teams[team_a_name]
    team_b = teams[team_b_name]

    prediction = probability_prediction(team_a, team_b)
    #print(f"Probability of {team_a.name} winning over {team_b.name}: {prediction:.2%}")


if __name__ == "__main__":
    main()
