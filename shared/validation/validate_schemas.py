"""Validate API schemas, examples, and representative rejected payloads."""

import copy
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'shared' / 'schemas'
EXAMPLES = ROOT / 'shared' / 'examples'


def main():
    schemas = {
        path.stem.removesuffix('.schema'): json.loads(path.read_text())
        for path in sorted(SCHEMAS.glob('*.schema.json'))
    }
    registry = Registry().with_resources(
        (schema['$id'], Resource.from_contents(schema)) for schema in schemas.values()
    )
    validators = {}
    assert schemas['common']['$defs']['predictionMethod']['enum'] == (
        schemas['prediction']['$defs']['predictionMethod']['enum']
    ), 'Prediction method enums have drifted from the domain baseline'
    assert schemas['game-status']['enum'] == (
        schemas['game']['$defs']['gameStatus']['enum']
    ), 'Game status enums have drifted from the domain baseline'
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
    assert not missing, f'Schemas without examples: {missing}'

    checks = []

    def reject(name, mutate):
        payload = copy.deepcopy(examples[name])
        mutate(payload)
        assert list(validators[name].iter_errors(payload)), f'Accepted invalid {name}: {payload}'
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
    assert validators['game-status'].is_valid('FINAL')
    assert not validators['game-status'].is_valid('BYE')
    for name in ['prediction', 'single-prediction-result']:
        for confidence in [0, 1]:
            validators[name].validate({**examples[name], 'confidence': confidence})
    print(f'Validated {len(schemas)} schemas, {len(examples)} examples, '
          f'{len(checks) + 1} rejection cases, and probability boundaries.')
    print('Cross-record and arithmetic rules require service-level validation; see api-schemas.md.')


if __name__ == '__main__':
    main()
