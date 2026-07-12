import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class DbPatchDeploymentContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/deploy-backend.yml").read_text(encoding="utf-8")
        cls.script = (ROOT / ".github/scripts/apply-db-patches.sh").read_text(encoding="utf-8")
        cls.schema = (ROOT / "backend/src/main/resources/db/schema.sql").read_text(encoding="utf-8")

    def test_deploy_retries_every_patch_added_after_automation_baseline(self):
        self.assertIn("DB_AUTOMATION_BASELINE: 95edad0c2e62aab2b9700b32e43724eef35876cf", self.workflow)
        self.assertIn("git merge-base --is-ancestor", self.workflow)
        self.assertIn("git diff --diff-filter=A", self.workflow)
        self.assertIn("managed-db-patches.txt", self.workflow)

    def test_existing_patch_modification_blocks_deploy(self):
        self.assertIn("git diff --diff-filter=M", self.workflow)
        self.assertIn("기존 DB patch는 수정할 수 없습니다", self.workflow)

    def test_runner_uses_immutable_checksum_ledger(self):
        self.assertIn("CREATE TABLE IF NOT EXISTS schema_migration", self.script)
        self.assertIn("sha256sum", self.script)
        self.assertIn("이미 적용된 DB patch 내용이 변경됐습니다", self.script)
        self.assertRegex(
            self.script,
            r"\^backend/src/main/resources/db/patches/\[A-Za-z0-9\._-\]\+\\\.sql\$",
        )

    def test_fresh_schema_contains_migration_ledger(self):
        self.assertIn("CREATE TABLE IF NOT EXISTS schema_migration", self.schema)
        self.assertIn("migration_name VARCHAR(255) NOT NULL", self.schema)
        self.assertIn("checksum       CHAR(64)     NOT NULL", self.schema)


if __name__ == "__main__":
    unittest.main()
