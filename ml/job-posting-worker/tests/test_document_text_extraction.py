import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "14_extract_document_text.py"
FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "quality_gate_cases.json"


def load_script():
    spec = importlib.util.spec_from_file_location("document_text_extraction", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class DocumentTextExtractionTest(unittest.TestCase):
    def test_configures_writable_ocr_cache_environment(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            cache = Path(tmp) / "ocr-cache"
            original = {
                "FLAGS_use_mkldnn": os.environ.get("FLAGS_use_mkldnn"),
                "XDG_CACHE_HOME": os.environ.get("XDG_CACHE_HOME"),
                "PADDLE_OCR_BASE_DIR": os.environ.get("PADDLE_OCR_BASE_DIR"),
            }
            try:
                for key in original:
                    os.environ.pop(key, None)

                configured = module.configure_ocr_cache_env(cache)

                self.assertEqual(configured, cache)
                # PaddlePaddle 3.x 의 oneDNN 런타임 크래시를 막기 위해 mkldnn 을 끈다.
                self.assertEqual(os.environ["FLAGS_use_mkldnn"], "0")
                self.assertEqual(os.environ["XDG_CACHE_HOME"], str(cache / ".cache"))
                self.assertEqual(os.environ["PADDLE_OCR_BASE_DIR"], str(cache / ".paddleocr"))
                self.assertTrue(cache.exists())
            finally:
                for key, value in original.items():
                    if value is None:
                        os.environ.pop(key, None)
                    else:
                        os.environ[key] = value

    def test_quality_gate_shared_fixture_statuses_match_backend_contract(self):
        module = load_script()
        cases = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))

        for case in cases:
            with self.subTest(case=case["name"]):
                analysis = module.analyze_quality(case["text"])

                self.assertEqual(analysis["qualityStatus"], case["expectedStatus"])

    def test_scores_clean_text_as_pass_and_writes_contract_files(self):
        module = load_script()
        text = "\n".join(
            [
                "모집부문",
                "백엔드 개발자",
                "담당업무",
                "Spring Boot 기반 API를 개발하고 MySQL 쿼리를 개선합니다.",
                "자격요건",
                "Java와 SQL 경험이 필요합니다.",
                "우대사항",
                "AWS 운영 경험을 우대합니다.",
                "근무조건",
                "정규직이며 주 5일 근무합니다.",
            ]
        )
        text = text + "\n" + ("서비스 운영 경험을 바탕으로 장애를 분석하고 개선합니다. " * 16)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            input_path = root / "posting.txt"
            output_dir = root / "out"
            input_path.write_text(text, encoding="utf-8")

            result = module.extract_document(input_path=input_path, output_dir=output_dir)

            self.assertEqual(result["strategy"], "TEXT_DIRECT")
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertGreaterEqual(result["qualityScore"], 70)
            self.assertFalse(result["fallbackEligible"])
            self.assertTrue((output_dir / "posting.txt").exists())
            meta = json.loads((output_dir / "posting.meta.json").read_text(encoding="utf-8"))
            self.assertIn("metrics", meta)
            self.assertIn("sectionHints", meta)
            self.assertEqual(meta["qualityStatus"], "PASS")

    def test_marks_short_or_empty_extraction_as_failed(self):
        module = load_script()

        analysis = module.analyze_quality("로그인\n회원가입\n")

        self.assertEqual(analysis["qualityStatus"], "FAILED")
        self.assertLess(analysis["qualityScore"], 40)
        self.assertIn("text_too_short", analysis["warnings"])

    def test_korean_joined_ocr_job_sections_require_review_instead_of_failed(self):
        module = load_script()
        text = "\n".join(
            [
                "상시채용",
                "홈페이지 지원",
                "근무지역",
                "근무형태",
                "합류하면함께할업무예요",
                "리더십과동행하며미팅의흐름을파악하고 필요한지원을선제적으로제공해요",
                "이력서는이렇게작성해주세요",
                "포지션에지원한동기를들려주세요",
                "지원방법",
                "홈페이지지원",
                "로그인 공유하기 추천공고 지도 스크랩",
            ]
        )
        text = text + "\n" + ("조직을지원하며업무가매끄럽게진행되도록돕는경험을확인합니다. " * 30)

        analysis = module.analyze_quality(text)

        self.assertIn(analysis["qualityStatus"], {"PASS", "REVIEW_REQUIRED"})
        self.assertGreaterEqual(analysis["qualityScore"], 40)
        self.assertNotIn("section_keywords_missing", analysis["warnings"])

    def test_section_hints_match_spaced_and_mixed_case_headers(self):
        module = load_script()

        # 띄어쓴 한글 헤더도 붙여쓰기 키워드와 매칭되어야 한다(예: "담당 업무" -> "담당업무").
        spaced = "\n".join(
            ["회사 소개", "담당 업무", "자격 요건", "근무 조건", "접수 방법"]
        )
        spaced_hints = module.section_hints(spaced)
        for keyword in ("회사소개", "담당업무", "자격요건", "근무조건", "접수방법"):
            self.assertIn(keyword, spaced_hints)

        # 영어 섹션 키워드는 대소문자 무관하게 매칭되어야 한다.
        english_hints = module.section_hints("Company\nRESPONSIBILITIES\nqualifications\napply")
        for keyword in ("Company", "Responsibilities", "Qualifications", "Apply"):
            self.assertIn(keyword, english_hints)

        # 동일 개념의 띄어쓰기 변형은 한 번만 집계되어야 한다(함께할업무 / 함께할 업무).
        dedup_hints = module.section_hints("우리와 함께할 업무를 소개합니다")
        self.assertEqual(dedup_hints.count("함께할업무"), 1)
        self.assertNotIn("함께할 업무", dedup_hints)

    def test_garbled_critical_section_demotes_pass_to_review(self):
        module = load_script()
        # 케이스 55 패턴: 회사소개/자격요건/우대사항은 정상이지만 주요업무 본문이 OCR 파편으로 소실된 공고.
        # 섹션 키워드·길이만 보면 PASS 지만, 핵심 업무 섹션에 useful line 이 0 이라 REVIEW_REQUIRED 로 강등되어야 한다.
        text = "\n".join(
            [
                "회사소개",
                "넥스트클라우드는 핀테크 결제 플랫폼을 운영하는 기업입니다. 대용량 트래픽을 안정적으로 처리합니다.",
                "주요업무",
                "티",
                "공 Y (을 ) AY",
                "우표눔를용라어를ㅋ크이극아",
                "자격요건",
                "Java와 Spring Boot 개발 경력 5년 이상. SQL 활용 능력과 Git 협업 경험이 있으신 분.",
                "우대사항",
                "Kubernetes, Docker 운영 경험과 AWS 클라우드 인프라 경험을 우대합니다.",
                "근무조건",
                "정규직이며 서울 강남구에서 근무합니다. 연봉은 협의합니다.",
            ]
        )
        text = text + "\n" + ("핀테크 결제 서비스를 안정적으로 제공하기 위해 노력합니다. " * 12)

        analysis = module.analyze_quality(text)

        self.assertEqual(analysis["qualityStatus"], "REVIEW_REQUIRED")
        self.assertIn("critical_section_content_insufficient", analysis["warnings"])
        self.assertEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 0)

    def test_valid_short_critical_section_is_not_demoted(self):
        module = load_script()
        # "API 개발 및 운영" 처럼 짧지만 유용한 업무 본문은 강등되면 안 된다(false positive 방지).
        text = "\n".join(
            [
                "회사소개",
                "핀테크 결제 플랫폼을 운영하는 기업입니다. 대용량 트래픽을 안정적으로 처리하며 성장하고 있습니다.",
                "주요업무",
                "Spring Boot 기반 REST API 개발 및 운영을 담당합니다.",
                "MySQL 데이터 모델링과 쿼리 성능 개선을 수행합니다.",
                "자격요건",
                "Java와 Spring Boot 개발 경력 5년 이상. SQL 활용 능력과 Git 협업 경험이 있으신 분.",
                "우대사항",
                "Kubernetes, Docker 운영 경험과 AWS 클라우드 인프라 경험을 우대합니다.",
                "근무조건",
                "정규직이며 서울 강남구에서 근무합니다. 연봉은 협의합니다.",
            ]
        )
        text = text + "\n" + ("안정적인 서비스 운영 경험을 바탕으로 시스템을 개선합니다. " * 12)

        analysis = module.analyze_quality(text)

        self.assertNotIn("critical_section_content_insufficient", analysis["warnings"])
        self.assertGreaterEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 1)

    def test_garbled_critical_section_with_colon_headers_demotes(self):
        module = load_script()
        # 헤더에 콜론/불릿이 붙어도(자격요건:, 주요업무:) 섹션 경계를 끊어 본문 소실을 잡아야 한다.
        text = "\n".join(
            [
                "회사소개:",
                "넥스트클라우드는 핀테크 결제 플랫폼을 운영하는 기업입니다. 대용량 트래픽을 안정적으로 처리합니다.",
                "주요업무:",
                "티",
                "공 Y (을 ) AY",
                "우표눔를용라어를ㅋ크이극아",
                "자격요건:",
                "Java와 Spring Boot 개발 경력 5년 이상. SQL 활용 능력과 Git 협업 경험.",
                "우대사항:",
                "Kubernetes, Docker 운영 경험과 AWS 클라우드 인프라 경험을 우대합니다.",
                "근무조건:",
                "정규직이며 서울 강남구에서 근무합니다.",
            ]
        )
        text = text + "\n" + ("핀테크 결제 서비스를 안정적으로 제공하기 위해 노력합니다. " * 12)

        analysis = module.analyze_quality(text)

        self.assertEqual(analysis["qualityStatus"], "REVIEW_REQUIRED")
        self.assertIn("critical_section_content_insufficient", analysis["warnings"])
        self.assertEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 0)

    def test_inline_garbled_critical_section_demotes(self):
        module = load_script()
        cases = [
            "\n".join(
                [
                    "Company: Acme Payments",
                    "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                    "Responsibilities: 티",
                    "공 Y (을 ) AY",
                    "우표눔를용라어를ㅋ크이극아",
                    "Qualifications: Java Spring Boot SQL",
                    "Benefits: remote option and flexible work",
                ]
            ),
            "\n".join(
                [
                    "Company: Acme Payments",
                    "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                    "What you will do: 티",
                    "공 Y (을 ) AY",
                    "우표눔를용라어를ㅋ크이극아",
                    "Qualifications: Java Spring Boot SQL",
                    "Benefits: remote option and flexible work",
                ]
            ),
            "\n".join(
                [
                    "회사소개: 넥스트클라우드는 핀테크 결제 플랫폼을 운영하는 기업입니다.",
                    "주요업무: 티",
                    "공 Y (을 ) AY",
                    "우표눔를용라어를ㅋ크이극아",
                    "자격요건: Java와 Spring Boot 개발 경험",
                    "우대사항: Kubernetes, Docker 운영 경험",
                ]
            ),
        ]

        for text in cases:
            with self.subTest(text=text.splitlines()[0]):
                text = text + "\n" + ("핀테크 결제 서비스를 안정적으로 제공하기 위해 노력합니다. " * 12)

                analysis = module.analyze_quality(text)

                self.assertEqual(analysis["qualityStatus"], "REVIEW_REQUIRED")
                self.assertIn("critical_section_content_insufficient", analysis["warnings"])
                self.assertTrue(analysis["metrics"]["criticalSectionExists"])
                self.assertEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 0)

    def test_inline_valid_critical_section_is_not_demoted(self):
        module = load_script()
        cases = [
            "\n".join(
                [
                    "Company: Acme Payments",
                    "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                    "Responsibilities: build APIs and operate services",
                    "Qualifications: Java Spring Boot SQL",
                    "Benefits: remote option and flexible work",
                ]
            ),
            "\n".join(
                [
                    "Company: Acme Payments",
                    "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                    "What you will do: build APIs and operate services",
                    "Qualifications: Java Spring Boot SQL",
                    "Benefits: remote option and flexible work",
                ]
            ),
            "\n".join(
                [
                    "회사소개: 넥스트클라우드는 핀테크 결제 플랫폼을 운영하는 기업입니다.",
                    "주요업무: API 개발 및 운영",
                    "자격요건: Java와 Spring Boot 개발 경험",
                    "우대사항: Kubernetes, Docker 운영 경험",
                ]
            ),
        ]

        for text in cases:
            with self.subTest(text=text.splitlines()[0]):
                text = text + "\n" + ("안정적인 서비스 운영 경험을 바탕으로 시스템을 개선합니다. " * 12)

                analysis = module.analyze_quality(text)

                self.assertEqual(analysis["qualityStatus"], "PASS")
                self.assertNotIn("critical_section_content_insufficient", analysis["warnings"])
                self.assertTrue(analysis["metrics"]["criticalSectionExists"])
                self.assertGreaterEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 1)

    def test_real_world_korean_duties_headers_are_recognized(self):
        module = load_script()
        # 두레팜(고용24)·카카오(자체공고) 실측 헤더: "직무내용", "합류하게 되면 이런 일을 하게 됩니다".
        for header, body in (
            ("직무내용", "데이터 수집 및 분석, AI 모델 개발과 운영을 담당합니다."),
            ("합류하게 되면 이런 일을 하게 됩니다", "QA 자동화 설계 및 구축, 릴리즈 품질 관리를 수행합니다."),
        ):
            with self.subTest(header=header):
                text = "\n".join([
                    "회사소개",
                    "핀테크 결제 플랫폼을 운영하는 기업입니다.",
                    header,
                    body,
                    "이런동료를기다립니다",
                    "Java와 Spring Boot 개발 경험이 있으신 분.",
                ]) + "\n" + ("안정적인 서비스를 제공하기 위해 노력합니다. " * 12)
                analysis = module.analyze_quality(text)
                self.assertTrue(analysis["metrics"]["criticalSectionExists"])
                self.assertGreaterEqual(analysis["metrics"]["criticalSectionUsefulLineCount"], 1)
                self.assertNotIn("critical_section_content_insufficient", analysis["warnings"])

    def test_strip_site_noise_removes_footer_and_keeps_body(self):
        module = load_script()
        text = "\n".join([
            "출력일자:2026-06-17 11:50:46",
            "주요업무",
            "데이터 분석 및 AI 개발을 담당합니다.",
            "Contact",
            "•jobs@example.com",
            "https://www.work24.go.kr/",
            "©카카오모빌리티 Kakaomobility All rights reserved.",
        ])
        cleaned = module.strip_site_noise(text)
        # 잡음 줄은 제거
        for noise in ("출력일자", "Contact", "jobs@example.com", "work24.go.kr", "All rights reserved"):
            self.assertNotIn(noise, cleaned)
        # 본문은 보존
        self.assertIn("데이터 분석 및 AI 개발을 담당합니다.", cleaned)
        self.assertIn("주요업무", cleaned)

    def test_position_name_header_is_not_treated_as_duties_section(self):
        module = load_script()
        # "모집직무"는 직무명 헤더라 critical duties 로 잡으면 안 된다(업무 본문 없이 직무명만으로 PASS 방지).
        text = "\n".join([
            "회사소개",
            "핀테크 결제 플랫폼을 운영하는 기업입니다.",
            "모집직무",
            "AI 개발자, 데이터 분석가",
            "자격요건",
            "Python 경험이 있으신 분.",
        ]) + "\n" + ("안정적인 서비스를 제공합니다. " * 40)

        analysis = module.analyze_quality(text)

        self.assertFalse(analysis["metrics"]["criticalSectionExists"])
        # 경계/힌트로는 SECTION_KEYWORDS 에 남아 있어야 한다.
        self.assertIn("모집직무", module.SECTION_KEYWORDS)

    def test_classifies_long_image_and_uses_existing_ocr_text(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            image_path = root / "long.jpg"
            existing_ocr_dir = root / "ocr"
            output_dir = root / "out"
            existing_ocr_dir.mkdir()
            Image.new("RGB", (540, 5000), "white").save(image_path)
            (existing_ocr_dir / "long.txt").write_text(
                "담당업무\n데이터 파이프라인 운영\n자격요건\nPython 경험\n",
                encoding="utf-8",
            )

            result = module.extract_document(
                input_path=image_path,
                output_dir=output_dir,
                existing_ocr_dir=existing_ocr_dir,
            )

            self.assertEqual(result["strategy"], "LONG_IMAGE_TILING")
            self.assertEqual(result["textSource"], "EXISTING_OCR")
            self.assertTrue(result["fallbackEligible"])
            self.assertEqual((output_dir / "long.txt").read_text(encoding="utf-8").splitlines()[0], "담당업무")

    def test_pdf_without_extractable_text_is_image_pdf_ocr_candidate(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_path = root / "scan.pdf"
            output_dir = root / "out"
            pdf_path.write_bytes(b"%PDF-1.4\n% image-only placeholder\n")

            result = module.extract_document(
                input_path=pdf_path,
                output_dir=output_dir,
                options=module.ExtractionOptions(enable_paddle_ocr=False),
            )

            self.assertEqual(result["strategy"], "IMAGE_PDF_OCR")
            self.assertEqual(result["qualityStatus"], "FAILED")
            self.assertTrue(result["fallbackEligible"])
            self.assertIn("ocr_not_executed", result["warnings"])

    def test_image_pdf_falls_back_to_line_ocr_when_ppstructure_unavailable(self):
        module = load_script()

        class FakePaddleOCR:
            def __init__(self, **kwargs):
                self.kwargs = kwargs

            def ocr(self, path, cls=True):
                text = [
                    "Company: Acme",
                    "Role: Backend Engineer",
                    "Responsibilities: build APIs and operate services",
                    "Qualifications: Java Spring MySQL Docker testing",
                    "Skills: Java Spring MySQL Docker Python React TypeScript",
                    "Apply before deadline",
                ]
                repeated = text + [
                    f"Responsibilities detail {index}: production ownership and collaboration experience required"
                    for index in range(12)
                ]
                return [[None, [line, 0.98]] for line in repeated]

        # 레이아웃 인식(PPStructureV3)이 불가하면 기본 line-OCR 로 폴백한다.
        def _ppstructure_unavailable():
            raise RuntimeError("PPStructureV3 unavailable in test")

        module.load_ppstructure_class = _ppstructure_unavailable
        module.load_paddleocr_class = lambda: FakePaddleOCR
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_path = root / "scan.pdf"
            output_dir = root / "out"
            pdf_path.write_bytes(b"%PDF-1.4\n% image-only placeholder\n")

            result = module.extract_document(input_path=pdf_path, output_dir=output_dir)

            self.assertEqual(result["strategy"], "IMAGE_PDF_OCR")
            self.assertEqual(result["textSource"], "PADDLE_OCR")
            self.assertIn("ppstructure_failed", result["warnings"])
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertGreaterEqual(result["qualityScore"], 70)
            self.assertEqual(result["modelVersions"]["ocr"], "existing_output_or_ppstructurev3")
            self.assertIn("Responsibilities", (output_dir / "scan.txt").read_text(encoding="utf-8"))

    def test_image_pdf_uses_ppstructure_layout_text_when_available(self):
        module = load_script()

        class FakeBlock:
            def __init__(self, label, content):
                self.label = label
                self.content = content

        class FakeResult(dict):
            pass

        class FakePPStructure:
            def __init__(self, **kwargs):
                self.kwargs = kwargs

            def predict(self, path, **kwargs):
                blocks = [
                    FakeBlock("text", "Company: Acme\nRole: Backend Engineer"),
                    FakeBlock("text", "Responsibilities: build APIs and operate services"),
                    FakeBlock("text", "Qualifications: Java Spring MySQL Docker testing"),
                    FakeBlock("text", "Skills: Java Spring MySQL Docker Python React TypeScript"),
                ] + [
                    FakeBlock(
                        "text",
                        f"Responsibilities detail {index}: production ownership and collaboration experience required",
                    )
                    for index in range(12)
                ]
                result = FakeResult()
                result["parsing_res_list"] = blocks
                return [result]

        module.load_ppstructure_class = lambda: FakePPStructure
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pdf_path = root / "scan.pdf"
            output_dir = root / "out"
            pdf_path.write_bytes(b"%PDF-1.4\n% image-only placeholder\n")

            result = module.extract_document(input_path=pdf_path, output_dir=output_dir)

            self.assertEqual(result["strategy"], "IMAGE_PDF_OCR")
            self.assertEqual(result["textSource"], "PPSTRUCTURE")
            self.assertEqual(result["qualityStatus"], "PASS")
            self.assertEqual(result["modelVersions"]["ocr"], "existing_output_or_ppstructurev3")
            self.assertIn("Responsibilities", (output_dir / "scan.txt").read_text(encoding="utf-8"))

    def test_batch_run_writes_report_without_warning_format_collision(self):
        module = load_script()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            input_dir = root / "input"
            output_dir = root / "out"
            report_path = root / "report.md"
            input_dir.mkdir()
            (input_dir / "posting.txt").write_text(
                "\n".join([
                    "Company: Acme",
                    "Role: Backend Engineer",
                    "Responsibilities: build APIs and operate services",
                    "Qualifications: Java Spring MySQL Docker testing",
                    "Skills: Java Spring MySQL Docker Python React TypeScript",
                ])
                + "\n"
                + "\n".join(f"Responsibilities detail {index}: production engineering ownership" for index in range(12)),
                encoding="utf-8",
            )

            exit_code = module.run(
                input_path=input_dir,
                output_dir=output_dir,
                existing_ocr_dir=None,
                report_path=report_path,
                limit=None,
            )

            self.assertEqual(exit_code, 0)
            self.assertTrue(report_path.exists())
            self.assertIn("posting.txt", report_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
