import unittest

from evaluate_ollama_schema import response_format


class EvaluateOllamaSchemaTest(unittest.TestCase):
    def test_schema_requires_all_keys_and_three_changes(self) -> None:
        value = response_format("SELF_INTRO_CORRECTION")
        schema = value["json_schema"]["schema"]

        self.assertTrue(value["json_schema"]["strict"])
        self.assertEqual(10, len(schema["required"]))
        self.assertEqual(3, schema["properties"]["changes"]["minItems"])
        self.assertTrue(schema["properties"]["preserved_meaning"]["const"])
        self.assertEqual(0, schema["properties"]["added_facts"]["maxItems"])


if __name__ == "__main__":
    unittest.main()
