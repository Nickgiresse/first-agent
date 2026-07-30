import re
from datetime import date

DATE_PATTERN = re.compile(r"(\d{1,2})\s*[.\-/]\s*(\d{1,2})\s*[.\-/]\s*(\d{2,4})")
_NAME_NOISE_PATTERN = re.compile(r"[^A-ZÀ-Ÿ\s\-']")


def normalize_line(line: str) -> str:
    return re.sub(r"\s+", " ", line).strip()


def clean_name(value: str | None) -> str | None:
    if not value:
        return None
    cleaned = _NAME_NOISE_PATTERN.sub("", value.upper()).strip()
    return cleaned or None


def to_date(day_str: str, month_str: str, year_str: str) -> date | None:
    try:
        day, month, year = int(day_str), int(month_str), int(year_str)
        if year < 100:
            year += 2000 if year < 50 else 1900
        return date(year, month, day)
    except ValueError:
        return None


def all_dates(text: str) -> list[date]:
    dates: list[date] = []
    for match in DATE_PATTERN.finditer(text):
        parsed = to_date(*match.groups())
        if parsed:
            dates.append(parsed)
    return sorted(dates)


def earliest(dates: list[date]) -> date | None:
    return dates[0] if dates else None


def latest(dates: list[date]) -> date | None:
    return dates[-1] if dates else None


def value_after_label(lines: list[str], index: int, label_end: int) -> str | None:
    remainder = lines[index][label_end:].strip(" :/-–—")
    if remainder:
        return remainder
    if index + 1 < len(lines):
        return lines[index + 1].strip(" :/-–—")
    return None
