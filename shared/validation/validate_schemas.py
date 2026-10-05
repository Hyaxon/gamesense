"""Validate API schemas, examples, and representative rejected payloads."""

import copy
import json
import math
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'shared' / 'schemas'
EXAMPLES = ROOT / 'shared' / 'examples'


def validate_model_relationships(request, result):
    """Check semantic relationships in synthetic success examples, not a service."""
    if request['executionId'] != result['executionId']:
        raise ValidationError('Request and result execution IDs must match')
    if request['modelId'] != result['model']['modelId']:
        raise ValidationError('Request and result model IDs must match')
    if request['matchup'] != result['matchup']:
        raise ValidationError('Request and result matchups must match')
    if request['matchup']['homeTeamId'] == request['matchup']['awayTeamId']:
        raise ValidationError('Home and away teams must differ')
    kind = request['executionKind']
    if kind not in result['model']['supportedExecutionKinds']:
        raise ValidationError('Model must support the requested execution kind')
    metadata = result['metadata']
    if metadata['executionKind'] != kind:
        raise ValidationError('Metadata execution kind must match the request')
    if metadata['dataSnapshotId'] != request['dataSnapshotId']:
        raise ValidationError('Metadata data snapshot ID must match the request')
    if datetime.fromisoformat(metadata['completedAt'].replace('Z', '+00:00')) < (
        datetime.fromisoformat(metadata['startedAt'].replace('Z', '+00:00'))
    ):
        raise ValidationError('Completion time must not precede start time')
    if 'seed' in request and metadata.get('seed') != request['seed']:
        raise ValidationError('Metadata seed must match the request')
    prediction = result['prediction']
    sides = {request['matchup']['homeTeamId'], request['matchup']['awayTeamId']}
    if prediction['predictedWinnerTeamId'] not in sides:
        raise ValidationError('Predicted winner must belong to the matchup')
    if prediction['method'] != result['model']['method']:
        raise ValidationError('Prediction method must match the model method')
    if not math.isfinite(prediction['confidence']):
        raise ValidationError('Prediction confidence must be finite')
    if prediction['confidence'] < 0.5:
        raise ValidationError('Predicted winner confidence must be at least 0.5')
    scores = result.get('supportingScores', [])
    if not all(score['teamId'] in sides for score in scores):
        raise ValidationError('Supporting score teams must belong to the matchup')
    if not all(math.isfinite(score['value']) for score in scores):
        raise ValidationError('Supporting score values must be finite')
    if len({(score['teamId'], score['name']) for score in scores}) != len(scores):
        raise ValidationError('Supporting scores must have unique team and name pairs')
    if kind == 'SIMULATION':
        simulation = result['simulation']
        if simulation['method'] != prediction['method']:
            raise ValidationError('Simulation method must match the prediction method')
        if simulation['trials'] != request['trials']:
            raise ValidationError('Simulation trials must match the request')
        home = simulation['homeWinProbability']
        away = simulation['awayWinProbability']
        if not (math.isfinite(home) and math.isfinite(away)):
            raise ValidationError('Simulation win probabilities must be finite')
        if abs(home + away - 1) > 0.000001:
            raise ValidationError('Simulation win probabilities must sum to one')
        side = 'homeTeamId' if home >= away else 'awayTeamId'
        if prediction['predictedWinnerTeamId'] != request['matchup'][side]:
            raise ValidationError('Predicted winner must follow simulation win probabilities')
        if abs(prediction['confidence'] - max(home, away)) > 0.000001:
            raise ValidationError('Prediction confidence must match the winning probability')


def main():
    schemas = {
        path.stem.removesuffix('.schema'): json.loads(path.read_text())
        for path in sorted(SCHEMAS.glob('*.schema.json'))
    }
    registry = Registry().with_resources(
        (schema['$id'], Resource.from_contents(schema)) for schema in schemas.values()
    )
    validators = {}
    if schemas['common']['$defs']['predictionMethod']['enum'] != (
        schemas['prediction']['$defs']['predictionMethod']['enum']
    ):
        raise ValidationError('Prediction method enums have drifted from the domain baseline')
    if schemas['game-status']['enum'] != (
        schemas['game']['$defs']['gameStatus']['enum']
    ):
        raise ValidationError('Game status enums have drifted from the domain baseline')
    for name, schema in schemas.items():
        Draft202012Validator.check_schema(schema)
        validators[name] = Draft202012Validator(
            schema, registry=registry, format_checker=FormatChecker()
        )
    examples = {}
    for path in sorted(EXAMPLES.glob('*.json')):
        examples[path.stem] = json.loads(path.read_text())
        validators[path.stem].validate(examples[path.stem])
    missing = set(schemas) - set(examples) - {'common'}
    if missing:
        raise ValidationError(f'Schemas without examples: {missing}')

    checks = []

    def reject(name, mutate):
        payload = copy.deepcopy(examples[name])
        mutate(payload)
        if not list(validators[name].iter_errors(payload)):
            raise ValidationError(f'Accepted invalid {name}: {payload}')
        checks.append(name)

    reject('custom-prediction-request', lambda p: p.pop('matchup'))
    reject('custom-prediction-request', lambda p: p['matchup'].update(season=None))
    reject('custom-prediction-request', lambda p: p['matchup'].update(season='2025'))
    reject('custom-prediction-request', lambda p: p.update(methods=['UNKNOWN']))
    reject('custom-prediction-request', lambda p: p.update(methods=['ELO', 'ELO']))
    reject('custom-prediction-request', lambda p: p['matchup'].update(homeTeamId='bad-id'))
    reject('custom-prediction-request', lambda p: p.update(extra=True))
    reject('simulation-configuration-request', lambda p: p.update(trials=0))
    reject('simulation-configuration-request', lambda p: p.update(trials=100001))
    reject('simulation-configuration-request', lambda p: p.update(trials=1.5))
    reject('simulation-configuration-request', lambda p: p.update(seed=-1))
    reject('single-prediction-result', lambda p: p.update(confidence=1.01))
    reject('single-prediction-result', lambda p: p.update(confidence=-0.01))
    reject('single-prediction-result', lambda p: p.update(predictedHomeScore=-1))
    reject('single-prediction-result', lambda p: p.update(generatedAt='not-a-date'))
    reject('single-prediction-result', lambda p: p.update(generatedAt='2025-08-29T12:00:00+02:00'))
    reject('single-prediction-result', lambda p: p.update(predictedHomeScore=None))
    reject('prediction-response', lambda p: p.update(predictions=[]))
    reject('aggregated-prediction-result', lambda p: p.update(contributingResultIds=[]))
    reject('aggregated-prediction-result', lambda p: p['contributingResultIds'].append(p['contributingResultIds'][0]))
    reject('error', lambda p: p.update(code='UNKNOWN'))
    reject('error', lambda p: p.update(message='  '))
    reject('error', lambda p: p['details'][0].update(path='matchup.season'))
    validators['game-status'].validate('FINAL')
    if validators['game-status'].is_valid('BYE'):
        raise ValidationError('Accepted invalid game-status: BYE')
    for name in ['prediction', 'single-prediction-result']:
        for confidence in [0, 1]:
            validators[name].validate({**examples[name], 'confidence': confidence})

    reject('model-descriptor', lambda p: p.update(supportedExecutionKinds=[]))
    reject('model-execution-request', lambda p: p.update(executionKind='UNKNOWN'))
    reject('model-execution-request', lambda p: p.update(configuration=None))
    reject('model-execution-request', lambda p: p.update(seed=42))
    reject('model-execution-request', lambda p: p.update(trials=10))
    reject('model-execution-request', lambda p: p.update(executionKind='SIMULATION'))
    reject('model-execution-result', lambda p: p.pop('prediction'))
    reject('model-execution-result', lambda p: p.update(simulation=examples['simulation-outcome']))
    reject('model-execution-result', lambda p: p['metadata'].update(executionKind='SIMULATION'))
    reject('model-execution-result', lambda p: p['metadata'].update(durationMilliseconds=-1))
    reject('model-execution-result', lambda p: p['metadata'].update(seed=42))
    reject('model-execution-error', lambda p: p.update(prediction=examples['single-prediction-result']))
    reject('model-execution-response', lambda p: p.update(status='ERROR'))

    model_examples = EXAMPLES / 'model-execution'
    model_pairs = {}
    for kind in ['deterministic', 'stochastic', 'simulation']:
        request = json.loads((model_examples / f'{kind}-request.json').read_text())
        result = json.loads((model_examples / f'{kind}-result.json').read_text())
        validators['model-execution-request'].validate(request)
        validators['model-execution-result'].validate(result)
        validators['model-execution-response'].validate(result)
        validate_model_relationships(request, result)
        model_pairs[kind] = (request, result)
    validators['model-execution-response'].validate(examples['model-execution-error'])

    # The wrapper remains valid for another planned deterministic algorithm family.
    request, result = copy.deepcopy(model_pairs['deterministic'])
    request['modelId'] = result['model']['modelId'] = 'glicko2-v1'
    result['model']['displayName'] = 'Glicko-2'
    result['model']['method'] = result['prediction']['method'] = 'GLICKO2'
    result['model'].pop('configurationSchemaId')
    request.pop('configuration')
    result.pop('configuration')
    validators['model-execution-result'].validate(result)
    validate_model_relationships(request, result)

    semantic_checks = []

    def reject_relationship(kind, mutate):
        request, result = copy.deepcopy(model_pairs[kind])
        mutate(result)
        try:
            validate_model_relationships(request, result)
        except ValidationError:
            semantic_checks.append(kind)
        else:
            raise ValidationError(f'Accepted inconsistent {kind} model example')

    reject_relationship('deterministic', lambda p: p['prediction'].update(predictedWinnerTeamId=p['executionId']))
    reject_relationship('deterministic', lambda p: p['prediction'].update(method='GLICKO2'))
    reject_relationship('deterministic', lambda p: p['prediction'].update(confidence=0.4))
    reject_relationship('deterministic', lambda p: p['supportingScores'][0].update(value=float('nan')))
    reject_relationship('deterministic', lambda p: p['model'].update(supportedExecutionKinds=['SIMULATION']))
    reject_relationship('deterministic', lambda p: p['metadata'].update(dataSnapshotId='different'))
    reject_relationship('deterministic', lambda p: p['metadata'].update(completedAt='2025-08-28T12:00:00Z'))
    reject_relationship('simulation', lambda p: p['simulation'].update(awayWinProbability=0.5))
    reject_relationship('simulation', lambda p: p['simulation'].update(trials=1))
    reject_relationship('simulation', lambda p: p['prediction'].update(confidence=0.6))
    reject_relationship('simulation', lambda p: p['metadata'].update(seed=43))
    request, result = copy.deepcopy(model_pairs['simulation'])
    result['simulation'].update(homeWinProbability=0.5, awayWinProbability=0.5)
    result['prediction']['confidence'] = 0.5
    validate_model_relationships(request, result)
    print(f'Validated {len(schemas)} schemas, {len(examples)} examples, '
          f'{len(checks) + 1} rejection cases, and probability boundaries.')
    print(f'Validated 6 model execution examples, a Glicko-2-shaped result, '
          f'{len(semantic_checks)} semantic rejection cases, and the equal-probability policy.')
    print('Runtime semantic validation remains service work; see prediction-model-interface.md.')


if __name__ == '__main__':
    main()
