"""Builds the model registry the prediction service starts with.

Every model that needs no setup is always registered. XGBoost needs a trained
artifact, so it is registered only when a path is configured and the artifact
loads. A missing or invalid artifact skips XGBoost alone and logs why; the
service still starts with every other model.
"""

import logging
from pathlib import Path

from .contracts import PredictionMethod
from .models.bradley_terry import BradleyTerryModel
from .models.coin_flip import CoinFlipModel
from .models.elo import EloModel
from .models.glicko import Glicko2Model
from .models.monte_carlo import MonteCarloModel
from .models.xgboost import XGBoostModel
from .registry import ModelRegistration, ModelRegistry

logger = logging.getLogger(__name__)

XGBOOST_MODEL_PATH_ENV = "PREDICTION_XGBOOST_MODEL_PATH"

# Each method has one model, so that model is the method's default.
DEFAULT_MODELS = {
    PredictionMethod.ELO: "elo-v1",
    PredictionMethod.GLICKO2: "glicko2",
    PredictionMethod.BRADLEY_TERRY: "bradley-terry-v1",
    PredictionMethod.MONTE_CARLO: "monte-carlo-v1",
    PredictionMethod.RANDOM: "coin-flip-v1",
}
XGBOOST_MODEL_ID = "xgboost-v1"


def build_registry(xgboost_model_path: str | Path | None = None) -> ModelRegistry:
    """Register every ready model, adding XGBoost only if its artifact loads."""
    registrations = [
        ModelRegistration(adapter=EloModel()),
        ModelRegistration(adapter=Glicko2Model()),
        ModelRegistration(adapter=BradleyTerryModel()),
        ModelRegistration(adapter=MonteCarloModel(), supports_seed=True),
        ModelRegistration(adapter=CoinFlipModel(), supports_seed=True),
    ]
    defaults = dict(DEFAULT_MODELS)

    xgboost = _load_xgboost(xgboost_model_path)
    if xgboost is not None:
        registrations.append(ModelRegistration(adapter=xgboost))
        defaults[PredictionMethod.MACHINE_LEARNING] = XGBOOST_MODEL_ID

    registry = ModelRegistry(registrations, defaults)
    logger.info("Registered prediction models: %s", ", ".join(sorted(registry.entries)))
    return registry


def _load_xgboost(model_path: str | Path | None) -> XGBoostModel | None:
    if not model_path:
        logger.warning(
            "XGBoost not registered: %s is not set. "
            "Train an artifact with src/scripts/train_xgboost.py and set the path.",
            XGBOOST_MODEL_PATH_ENV,
        )
        return None
    path = Path(model_path)
    if not path.is_file():
        logger.warning("XGBoost not registered: artifact not found at %s", path)
        return None
    try:
        # XGBoostError subclasses ValueError; OSError covers unreadable files.
        return XGBoostModel.from_file(path)
    except (ValueError, OSError) as error:
        logger.warning(
            "XGBoost not registered: artifact at %s could not be loaded (%s)",
            path,
            type(error).__name__,
        )
        return None
