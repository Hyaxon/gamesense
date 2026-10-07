"""Fair-coin calculations, independent of requests, teams, and the runner."""

from dataclasses import dataclass
from random import Random


@dataclass(frozen=True)
class FlipCounts:
    home_wins: int
    away_wins: int

    @property
    def trials(self) -> int:
        return self.home_wins + self.away_wins

    @property
    def home_probability(self) -> float:
        return self.home_wins / self.trials

    @property
    def away_probability(self) -> float:
        return self.away_wins / self.trials


def simulate_coin_flips(trials: int, rng: Random) -> FlipCounts:
    """Heads (1) wins for home; tails (0) wins for away.

    The runner enforces the common trial limit. This function also rejects
    nonpositive/noninteger counts when used directly by algorithm tests.
    """
    if type(trials) is not int or trials < 1:
        raise ValueError("Trials must be a positive integer")
    home_wins = sum(rng.getrandbits(1) for _ in range(trials))
    return FlipCounts(home_wins=home_wins, away_wins=trials - home_wins)
