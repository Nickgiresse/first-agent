from datetime import date

from app.ocr.validation import (
    validate_cni_unique_identifier,
    validate_date_format,
    validate_height_meters,
    validate_issue_before_expiry,
    validate_mrz_check_digits,
    validate_sex,
)


def test_validate_date_format_accepts_well_formed_calendar_date():
    assert validate_date_format("22.10.2005").valid is True
    assert bool(validate_date_format("22.10.2005")) is True


def test_validate_date_format_rejects_wrong_separator():
    result = validate_date_format("22/10/2005")
    assert result.valid is False
    assert result.reason


def test_validate_date_format_rejects_non_calendar_date():
    result = validate_date_format("31.02.2020")
    assert result.valid is False


def test_validate_issue_before_expiry_accepts_correct_order():
    assert validate_issue_before_expiry(date(2022, 5, 17), date(2032, 5, 17)).valid is True


def test_validate_issue_before_expiry_rejects_reversed_dates():
    result = validate_issue_before_expiry(date(2032, 5, 17), date(2022, 5, 17))
    assert result.valid is False


def test_validate_sex_accepts_m_or_f():
    assert validate_sex("M").valid is True
    assert validate_sex("f").valid is True  # insensible à la casse


def test_validate_sex_rejects_anything_else():
    assert validate_sex("X").valid is False


def test_validate_height_accepts_plausible_range():
    assert validate_height_meters(1.86).valid is True


def test_validate_height_rejects_implausible_values():
    assert validate_height_meters(0.1).valid is False
    assert validate_height_meters(3.5).valid is False


def test_validate_cni_unique_identifier_requires_exactly_17_digits():
    assert validate_cni_unique_identifier("20220101224610554").valid is True
    assert validate_cni_unique_identifier("2022010122461055").valid is False  # 16 chiffres
    assert validate_cni_unique_identifier("2022010122461055A").valid is False  # lettre


def test_validate_mrz_check_digits_reports_failures():
    assert validate_mrz_check_digits([]).valid is True
    result = validate_mrz_check_digits(["documentNumber", "global"])
    assert result.valid is False
    assert "documentNumber" in result.reason
