"""Run the complete Glicko-2 example through the production algorithm."""

import json

from gamesense_prediction.models.glicko.algorithm import update_player
from gamesense_prediction.models.glicko.glicko2 import Team

EXAMPLE = {
    "system_constant_tau": 0.5,
    "player": {"rating": 1500.0, "rd": 200.0, "volatility": 0.06},
    "matches": [
        {"opponent_rating": 1400.0, "opponent_rd": 30.0, "outcome": 1.0},
        {"opponent_rating": 1550.0, "opponent_rd": 100.0, "outcome": 0.0},
        {"opponent_rating": 1700.0, "opponent_rd": 300.0, "outcome": 0.0},
    ],
}


def main() -> None:
    player_data = EXAMPLE["player"]
    player = Team("player")
    player.rating = player_data["rating"]
    player.rd = player_data["rd"]
    player.volatility = player_data["volatility"]
    matches = [
        {
            "opponent": {
                "rating": match["opponent_rating"],
                "rd": match["opponent_rd"],
            },
            "outcome": match["outcome"],
        }
        for match in EXAMPLE["matches"]
    ]
    update_player(player, matches, EXAMPLE["system_constant_tau"])
    print(
        json.dumps(
            {
                "player": {
                    "rating": round(player.rating, 2),
                    "rd": round(player.rd, 2),
                    "volatility": round(player.volatility, 5),
                }
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
