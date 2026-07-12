import unittest
from pathlib import Path

from ensure_aasa_nginx_location import choose_tls_server, ensure_aasa_location, server_blocks


HTTP_AND_TLS = """
server {
    listen 80;
    server_name careertuner.kro.kr;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name careertuner.kro.kr;
    root /var/www/careertuner;
    location /api/ { proxy_pass http://127.0.0.1:8080; }
}
"""


class EnsureAasaNginxLocationTest(unittest.TestCase):
    def test_selects_tls_server_and_inserts_exact_json_location(self):
        path = Path("/etc/nginx/sites-enabled/careertuner")
        selected_path, block = choose_tls_server({path: HTTP_AND_TLS}, "careertuner.kro.kr")
        updated, changed = ensure_aasa_location(HTTP_AND_TLS, block)

        self.assertEqual(selected_path, path)
        self.assertTrue(changed)
        self.assertEqual(updated.count("location = /.well-known/apple-app-site-association"), 1)
        self.assertIn("default_type application/json;", updated)
        self.assertLess(updated.index("location = /.well-known/apple-app-site-association"), updated.rindex("}"))

    def test_is_idempotent(self):
        block = [block for block in server_blocks(HTTP_AND_TLS) if block.is_tls][0]
        once, changed = ensure_aasa_location(HTTP_AND_TLS, block)
        self.assertTrue(changed)
        second_block = [candidate for candidate in server_blocks(once) if candidate.is_tls][0]
        twice, changed_again = ensure_aasa_location(once, second_block)
        self.assertFalse(changed_again)
        self.assertEqual(twice, once)

    def test_repairs_existing_exact_location_with_wrong_mime(self):
        wrong = HTTP_AND_TLS.replace(
            "    location /api/ { proxy_pass http://127.0.0.1:8080; }",
            "    location /api/ { proxy_pass http://127.0.0.1:8080; }\n"
            "    location = /.well-known/apple-app-site-association {\n"
            "        default_type application/octet-stream;\n"
            "        try_files $uri =404;\n"
            "    }",
        )
        block = [candidate for candidate in server_blocks(wrong) if candidate.is_tls][0]
        updated, changed = ensure_aasa_location(wrong, block)

        self.assertTrue(changed)
        self.assertEqual(updated.count("location = /.well-known/apple-app-site-association"), 1)
        self.assertIn("default_type application/json;", updated)
        self.assertNotIn("default_type application/octet-stream;", updated)

    def test_rejects_ambiguous_tls_servers(self):
        duplicate = HTTP_AND_TLS + "\n" + HTTP_AND_TLS.split("server {", 1)[1].join(["server {", ""])
        with self.assertRaisesRegex(ValueError, "하나로 결정"):
            choose_tls_server({Path("/etc/nginx/conf.d/duplicate.conf"): duplicate}, "careertuner.kro.kr")


if __name__ == "__main__":
    unittest.main()
