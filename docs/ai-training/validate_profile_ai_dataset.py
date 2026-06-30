import json
import re
import statistics
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path


REQUIRED_OUTPUT_FIELDS = {
    "summary",
    "extractedSkills",
    "strengths",
    "gaps",
    "recommendations",
    "criterionScores",
}

REQUIRED_CRITERIA = {
    "GOAL_CLARITY",
    "EXPERIENCE_SPECIFICITY",
    "ACHIEVEMENT_EVIDENCE",
    "JOB_SKILL_ALIGNMENT",
    "DOCUMENT_CONSISTENCY",
    "IMPROVEMENT_READINESS",
}

KNOWN_JOB_FAMILIES = {
    "DEVELOPMENT_DATA",
    "SALES_MARKETING",
    "DESIGN_CONTENT",
    "BUSINESS_OFFICE",
    "HEALTHCARE_SERVICE",
    "EDUCATION_PUBLIC",
    "PRODUCTION_LOGISTICS",
    "ENGINEERING_TECHNICAL",
    "GENERAL",
}

MIN_TRAINING_READY_SAMPLES = 30
MIN_RECOMMENDATION_COUNT = 2

VAGUE_RECOMMENDATION_PHRASES = [
    "열심히",
    "노력하세요",
    "역량을 강화하세요",
    "경험을 더 쌓으세요",
    "준비하세요",
]

EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_PATTERN = re.compile(r"01[016789][-. ]?\d{3,4}[-. ]?\d{4}")
KOREAN_RRN_PATTERN = re.compile(r"\d{6}[- ]?[1-4]\d{6}")


@dataclass
class DatasetQualityReport:
    sample_count: int = 0
    job_family_counts: Counter = field(default_factory=Counter)
    criterion_scores: dict[str, list[int]] = field(default_factory=lambda: defaultdict(list))
    warnings: list[str] = field(default_factory=list)
    profile_signatures: dict[str, int] = field(default_factory=dict)

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def load_assistant_json(line_no: int, row: dict) -> dict:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 3:
        raise ValueError(f"line {line_no}: messages must contain system, user, assistant")

    assistant_message = messages[-1]
    if assistant_message.get("role") != "assistant":
        raise ValueError(f"line {line_no}: last message must be assistant")

    content = assistant_message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ValueError(f"line {line_no}: assistant content is empty")

    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        raise ValueError(f"line {line_no}: assistant content is not valid JSON: {exc}") from exc


def load_user_json(line_no: int, row: dict) -> dict:
    messages = row.get("messages")
    if not isinstance(messages, list) or len(messages) < 2:
        raise ValueError(f"line {line_no}: messages must contain a user message")

    user_message = messages[1]
    if user_message.get("role") != "user":
        raise ValueError(f"line {line_no}: second message must be user")

    content = user_message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ValueError(f"line {line_no}: user content is empty")

    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        raise ValueError(f"line {line_no}: user content is not valid JSON: {exc}") from exc


def validate_output(line_no: int, output: dict) -> None:
    missing_fields = REQUIRED_OUTPUT_FIELDS - set(output)
    if missing_fields:
        raise ValueError(f"line {line_no}: missing output fields {sorted(missing_fields)}")

    for field in ["extractedSkills", "strengths", "gaps", "recommendations"]:
        if not isinstance(output[field], list):
            raise ValueError(f"line {line_no}: {field} must be a list")

    criterion_scores = output["criterionScores"]
    if not isinstance(criterion_scores, list):
        raise ValueError(f"line {line_no}: criterionScores must be a list")

    found_criteria = set()
    for item in criterion_scores:
        criterion = item.get("criterion")
        found_criteria.add(criterion)

        raw_score = item.get("rawScore")
        if not isinstance(raw_score, int) or raw_score < 0 or raw_score > 100:
            raise ValueError(f"line {line_no}: rawScore must be an integer between 0 and 100")

        if not item.get("evidence"):
            raise ValueError(f"line {line_no}: evidence is required for {criterion}")

        if not item.get("improvement"):
            raise ValueError(f"line {line_no}: improvement is required for {criterion}")

    if found_criteria != REQUIRED_CRITERIA:
        missing = REQUIRED_CRITERIA - found_criteria
        unknown = found_criteria - REQUIRED_CRITERIA
        raise ValueError(
            f"line {line_no}: criterion mismatch. missing={sorted(missing)}, unknown={sorted(unknown)}"
        )


def inspect_quality(line_no: int, row: dict, user_input: dict, output: dict, report: DatasetQualityReport) -> None:
    report.sample_count += 1

    job_family = user_input.get("jobFamily")
    if job_family not in KNOWN_JOB_FAMILIES:
        report.warn(f"line {line_no}: unknown jobFamily {job_family!r}")
    else:
        report.job_family_counts[job_family] += 1

    signature = profile_signature(user_input)
    if signature in report.profile_signatures:
        first_line = report.profile_signatures[signature]
        raise ValueError(f"line {line_no}: duplicated or near-duplicated profile sample; first seen at line {first_line}")
    report.profile_signatures[signature] = line_no

    recommendations = output.get("recommendations", [])
    if len(recommendations) < MIN_RECOMMENDATION_COUNT:
        report.warn(f"line {line_no}: recommendations should contain at least {MIN_RECOMMENDATION_COUNT} items")

    for recommendation in recommendations:
        if len(recommendation.strip()) < 12:
            report.warn(f"line {line_no}: recommendation is too short: {recommendation!r}")
        if any(phrase in recommendation for phrase in VAGUE_RECOMMENDATION_PHRASES):
            report.warn(f"line {line_no}: recommendation may be too vague: {recommendation!r}")

    for item in output["criterionScores"]:
        criterion = item["criterion"]
        raw_score = item["rawScore"]
        report.criterion_scores[criterion].append(raw_score)

    all_scores = [item["rawScore"] for item in output["criterionScores"]]
    if all(score >= 90 for score in all_scores):
        report.warn(f"line {line_no}: all criterion scores are very high; check whether this is too generous")
    if all(score <= 45 for score in all_scores):
        report.warn(f"line {line_no}: all criterion scores are very low; check whether this is too harsh")

    row_text = json.dumps(row, ensure_ascii=False)
    emails = [email for email in EMAIL_PATTERN.findall(row_text) if not email.endswith("@example.com")]
    if emails:
        report.warn(f"line {line_no}: possible real email found {emails}")
    if PHONE_PATTERN.search(row_text):
        report.warn(f"line {line_no}: possible phone number found")
    if KOREAN_RRN_PATTERN.search(row_text):
        report.warn(f"line {line_no}: possible Korean resident registration number found")


def profile_signature(user_input: dict) -> str:
    profile = user_input.get("profile") if isinstance(user_input.get("profile"), dict) else {}
    projects = profile.get("projects") if isinstance(profile.get("projects"), list) else []
    career = profile.get("career") if isinstance(profile.get("career"), list) else []

    project_parts = []
    for project in projects:
        if isinstance(project, dict):
            project_parts.append(
                "|".join(
                    normalize_text(project.get(key, ""))
                    for key in ["name", "role", "result"]
                )
            )

    career_parts = []
    for item in career:
        if isinstance(item, dict):
            career_parts.append(
                "|".join(
                    normalize_text(item.get(key, ""))
                    for key in ["company", "role", "duties"]
                )
            )

    parts = [
        normalize_text(user_input.get("jobFamily", "")),
        normalize_text(profile.get("desiredJob", "")),
        normalize_text(profile.get("desiredIndustry", "")),
        normalize_text(profile.get("resumeText", ""))[:80],
        normalize_text(profile.get("selfIntro", ""))[:80],
        "||".join(project_parts),
        "||".join(career_parts),
    ]
    return "###".join(parts)


def normalize_text(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "").strip().lower())


def finalize_quality(report: DatasetQualityReport) -> None:
    if report.sample_count < MIN_TRAINING_READY_SAMPLES:
        report.warn(
            f"dataset has {report.sample_count} samples; use at least {MIN_TRAINING_READY_SAMPLES} samples before real training"
        )

    missing_families = KNOWN_JOB_FAMILIES - set(report.job_family_counts)
    if missing_families:
        report.warn(f"missing job families: {sorted(missing_families)}")

    if report.job_family_counts:
        most_common_family, most_common_count = report.job_family_counts.most_common(1)[0]
        if report.sample_count >= MIN_TRAINING_READY_SAMPLES and most_common_count / report.sample_count > 0.4:
            report.warn(
                f"jobFamily distribution is skewed: {most_common_family} has {most_common_count}/{report.sample_count} samples"
            )

    for criterion, scores in sorted(report.criterion_scores.items()):
        average = statistics.mean(scores)
        if average >= 88:
            report.warn(f"{criterion}: average score is high ({average:.1f}); check score inflation")
        if average <= 45:
            report.warn(f"{criterion}: average score is low ({average:.1f}); check score deflation")


def validate_file(path: Path) -> DatasetQualityReport:
    report = DatasetQualityReport()
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue

        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"line {line_no}: row is not valid JSON: {exc}") from exc

        user_input = load_user_json(line_no, row)
        output = load_assistant_json(line_no, row)
        validate_output(line_no, output)
        inspect_quality(line_no, row, user_input, output, report)

    finalize_quality(report)
    return report


def print_report(report: DatasetQualityReport) -> None:
    print(f"OK: {report.sample_count} samples passed structural validation")

    print("\nJob family distribution:")
    for family in sorted(KNOWN_JOB_FAMILIES):
        print(f"- {family}: {report.job_family_counts.get(family, 0)}")

    print("\nCriterion score averages:")
    for criterion in sorted(REQUIRED_CRITERIA):
        scores = report.criterion_scores.get(criterion, [])
        if scores:
            print(f"- {criterion}: avg={statistics.mean(scores):.1f}, min={min(scores)}, max={max(scores)}")
        else:
            print(f"- {criterion}: no scores")

    if report.warnings:
        print("\nQuality warnings:")
        for warning in report.warnings:
            print(f"- {warning}")
    else:
        print("\nQuality warnings: none")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python validate_profile_ai_dataset.py <dataset.jsonl>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.exists():
        print(f"file not found: {path}", file=sys.stderr)
        return 2

    try:
        report = validate_file(path)
    except ValueError as exc:
        print(f"INVALID: {exc}", file=sys.stderr)
        return 1

    print_report(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
