#!/usr/bin/env python3
"""Exercise the real backup loop with deterministic clocks and disposable tools."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'deploy/postgres/backup.sh'


class BackupSchedulingTest(unittest.TestCase):
    def run_loop(self, jump):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            backups = root / 'backups'
            artifacts = root / 'artifacts'
            artifacts.mkdir()
            (root / 'clock').write_text('100000')
            tools = {
                'date': 'if [ "$*" = "+%s" ]; then cat "$TEST_ROOT/clock"; else cat "$TEST_ROOT/clock"; fi',
                'sleep': 'echo "$1" >> "$TEST_ROOT/sleeps"; echo $(( $(cat "$TEST_ROOT/clock") + ${CLOCK_JUMP:-$1} )) > "$TEST_ROOT/clock"',
                'pg_dump': 'if [ -e "$TEST_ROOT/dumped" ]; then exit 9; fi; touch "$TEST_ROOT/dumped"; for arg do case "$arg" in --file=*) echo snapshot > "${arg#--file=}";; esac; done',
                'pg_restore': 'exit 0',
            }
            for name, body in tools.items():
                path = root / name
                path.write_text('#!/bin/sh\nset -eu\n' + body + '\n')
                path.chmod(0o700)
            script = SOURCE.read_text().replace('/backups', str(backups)).replace('/artifacts', str(artifacts))
            env = {**os.environ, 'PATH': str(root) + ':' + os.environ['PATH'],
                   'TEST_ROOT': str(root), 'BACKUP_INTERVAL_SECONDS': '125'}
            if jump:
                env['CLOCK_JUMP'] = '30000'
            result = subprocess.run(['sh', '-c', script], env=env, capture_output=True, text=True, timeout=5)
            sleeps = [int(value) for value in (root / 'sleeps').read_text().splitlines()]
            self.assertNotEqual(result.returncode, 0, 'Second dump failure must propagate for Docker restart')
            self.assertEqual(len(list(backups.glob('*.complete'))), 1)
            return sleeps

    def test_suspend_clock_jump_rechecks_within_one_minute(self):
        self.assertEqual(self.run_loop(True), [60])

    def test_normal_interval_waits_until_deadline(self):
        self.assertEqual(self.run_loop(False), [60, 60, 5])


if __name__ == '__main__':
    unittest.main()
