"""Run a Glicko-2 rating period from a JSON document.

Usage:
    python examples/json_to_glicko.py examples/glicko2-input.json
"""

import json
import sys
from pathlib import Path

from src.gamesense_prediction.models.glicko.algorithm import update_player
from src.gamesense_prediction.models.glicko.glicko2 import Team


def calculate_result(data: dict) -> dict:
    """Convert the wire-shaped example input into the wire-shaped result."""
    player_data = data["player"]
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
        for match in data["matches"]
    ]
    update_player(player, matches, data["system_constant_tau"])

    return {
        "player": {
            "rating": round(player.rating, 2),
            "rd": round(player.rd, 2),
            "volatility": round(player.volatility, 5),
        }
    }


def main() -> None:
    input_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("-")
    if str(input_path) == "-":
        data = json.load(sys.stdin)
    else:
        with input_path.open(encoding="utf-8") as input_file:
            data = json.load(input_file)
    print(json.dumps(calculate_result(data), indent=2))


if __name__ == "__main__":
    main()
