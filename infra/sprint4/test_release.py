"""Release safety regressions: no network, no provider account and no deployment."""
from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('sprint4_release', Path(__file__).with_name('release.py'))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
REPOSITORY = '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/sasms'
OLD = REPOSITORY + ':' + 'a' * 40
NEW = REPOSITORY + ':' + 'b' * 40
ARN = 'arn:aws:apprunner:ap-southeast-1:123456789012:service/sasms/1234abcd'


class ReleaseTests(unittest.TestCase):
    def service(self):
        return {'Status': 'RUNNING', 'ServiceUrl': 'sasms.ap-southeast-1.awsapprunner.com',
                'SourceConfiguration': {'AutoDeploymentsEnabled': True,
                    'AuthenticationConfiguration': {'AccessRoleArn': 'test-access-role'},
                    'ImageRepository': {'ImageIdentifier': OLD, 'ImageRepositoryType': 'ECR',
                        'ImageConfiguration': {'Port': '8080',
                            'RuntimeEnvironmentVariables': {'EXISTING_SETTING': 'preserve-this-value'},
                            'RuntimeEnvironmentSecrets': {'SASMS_DB_PASSWORD': 'arn:aws:secretsmanager:secret'}}}}}

    def test_preserves_existing_configuration_and_refuses_other_repository(self):
        service = self.service()
        original = deepcopy(service)
        source = release.source_for_image(service, NEW)
        self.assertEqual(source['ImageRepository']['ImageConfiguration'], original['SourceConfiguration']['ImageRepository']['ImageConfiguration'])
        self.assertEqual(source['AuthenticationConfiguration'], original['SourceConfiguration']['AuthenticationConfiguration'])
        self.assertFalse(source['AutoDeploymentsEnabled'])
        self.assertEqual(service, original)
        with self.assertRaises(ValueError):
            release.source_for_image(service, 'another.example.com/sasms:' + 'c' * 40)

    def test_plan_never_calls_provider(self):
        args = SimpleNamespace(service_arn=ARN, image=NEW, execute=False)
        with patch.object(release, 'aws_json', side_effect=AssertionError('network forbidden')):
            result = release.update_apprunner(args)
        self.assertEqual(result['status'], 'PLAN_ONLY')

    def test_waits_for_exact_operation_and_records_only_rollback_identifiers(self):
        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(service_arn=ARN, image=NEW, execute=True, timeout_seconds=60, evidence_dir=Path(directory))
            initial, deployed = self.service(), self.service()
            deployed['SourceConfiguration']['ImageRepository']['ImageIdentifier'] = NEW
            calls = []
            responses = [ {'Service': initial}, {'OperationId': 'new-operation'},
                {'OperationSummaryList': [{'Id': 'old-operation', 'Status': 'SUCCEEDED'}, {'Id': 'new-operation', 'Status': 'IN_PROGRESS'}]},
                {'OperationSummaryList': [{'Id': 'new-operation', 'Status': 'SUCCEEDED'}]}, {'Service': deployed}]
            def provider(arguments):
                calls.append(arguments)
                if arguments[1] == 'update-service':
                    uri = arguments[arguments.index('--source-configuration') + 1]
                    from urllib.request import url2pathname
                    from urllib.parse import urlsplit
                    payload = Path(url2pathname(urlsplit(uri).path))
                    body = json.loads(payload.read_text(encoding='utf-8'))
                    self.assertEqual(body['ImageRepository']['ImageConfiguration'], initial['SourceConfiguration']['ImageRepository']['ImageConfiguration'])
                return responses.pop(0)
            with patch.object(release, 'aws_json', side_effect=provider), patch.object(release.time, 'sleep'):
                result = release.update_apprunner(args)
            self.assertEqual(len(calls), 5)
            self.assertEqual(result['status'], 'DEPLOYED_AWAITING_SMOKE')
            self.assertEqual(result['previous_image'], OLD)
            evidence = (Path(directory) / 'apprunner-deployment.json').read_text(encoding='utf-8')
            self.assertNotIn('preserve-this-value', evidence)
            self.assertNotIn('secretsmanager', evidence)
            with self.assertRaises(ValueError):
                release.update_apprunner(args)

    def test_failed_provider_operation_does_not_claim_deployed(self):
        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(service_arn=ARN, image=NEW, execute=True, timeout_seconds=60, evidence_dir=Path(directory))
            with patch.object(release, 'aws_json', side_effect=[{'Service': self.service()}, {'OperationId': 'op'},
                    {'OperationSummaryList': [{'Id': 'op', 'Status': 'ROLLBACK_SUCCEEDED'}]}]):
                with self.assertRaises(RuntimeError):
                    release.update_apprunner(args)
            evidence = json.loads((Path(directory) / 'apprunner-deployment.json').read_text(encoding='utf-8'))
            self.assertEqual(evidence['status'], 'FAILED_REQUIRES_OPERATOR_REVIEW')

    def test_packages_exact_build_bytes_without_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / 'dist', Path(directory) / 'stage'
            source.mkdir()
            (source / 'index.html').write_bytes(b'<html>release build</html>')
            args = SimpleNamespace(backend_origin='https://backend.example.com', dist=source, output=output,
                                   org_id='test-org', project_id='test-project', commit='c' * 40)
            result = release.prepare_vercel(args)
            self.assertEqual((output / '.vercel/output/static/index.html').read_bytes(), (source / 'index.html').read_bytes())
            self.assertEqual(result['deployment_status'], 'NOT_DEPLOYED')
            config = json.loads((output / '.vercel/output/config.json').read_text(encoding='utf-8'))
            self.assertEqual(config['routes'][0]['dest'], 'https://backend.example.com/api/$1')
            self.assertEqual(config['routes'][0]['headers']['Cache-Control'], 'private, no-store')
            with self.assertRaises(ValueError):
                release.prepare_vercel(args)

    def test_rejects_credentials_paths_and_insecure_origins(self):
        for origin in ['http://backend.example.com', 'https://user:secret@backend.example.com',
                       'https://backend.example.com/api', 'https://backend.example.com?secret=value', 'https://localhost']:
            with self.subTest(origin=origin), self.assertRaises(ValueError):
                release.https_origin(origin)


if __name__ == '__main__':
    unittest.main()
