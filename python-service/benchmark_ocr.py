#!/usr/bin/env python
"""Compare l'ancien prétraitement (minimal, "bypass") au nouveau (voir preprocessing.py :
correction de perspective, aplanissement de fond, profil "document pâle") sur un jeu de vraies
images, côte à côte : type détecté, champs extraits, confiance moyenne, temps par document. Les
deux passes utilisent le même moteur OCR (Tesseract) — EasyOCR n'a pas pu être retenu pour ce
projet (Python 3.14 : aucune wheel PaddleOCR, et EasyOCR entre en conflit avec opencv-contrib-python
déjà imposé par mediapipe ; voir RAPPORT_MISSION.md), donc la comparaison porte sur l'effet du
prétraitement, pas sur un changement de moteur.

Usage :
    python benchmark_ocr.py chemin/vers/dossier_images/

Convention de nommage pour les documents recto/verso : "xxx_recto.jpg" + "xxx_verso.jpg" (ou
variantes _front/_back) sont associés automatiquement ; un fichier seul est traité comme recto
sans verso (cas du passeport, une seule page).

Aucun jeu de test réel n'est fourni avec ce dépôt (documents d'identité = données sensibles) : ce
script est un outil à exécuter soi-même sur ses propres photos, pas un rapport déjà généré à
l'avance — je n'ai pas eu accès à de vraies photos pour produire des chiffres dans ce projet.
"""

from __future__ import annotations

import argparse
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from app.ocr.cni_parser import parse_cni_fields
from app.ocr.document_kind import DocumentKind, detect_document_kind
from app.ocr.engine import extract_text, extract_words
from app.ocr.passport_parser import parse_passport_fields
from app.ocr.preprocessing import BYPASS_PROFILE, DEFAULT_PROFILE, PALE_DOCUMENT_PROFILE, PreprocessingProfile, preprocess_for_ocr
from app.ocr.provisional_receipt_parser import parse_provisional_receipt_fields
from app.ocr.receipt_parser import parse_receipt_fields
from app.utils.image_io import decode_image

_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp"}
_BACK_SUFFIX_PATTERN = re.compile(r"(_verso|_back|_v)$", re.IGNORECASE)
_FRONT_SUFFIX_PATTERN = re.compile(r"(_recto|_front|_r)$", re.IGNORECASE)

_PROFILES: dict[str, PreprocessingProfile] = {
    "ancien (bypass)": BYPASS_PROFILE,
    "nouveau (defaut)": DEFAULT_PROFILE,
    "nouveau (doc pale)": PALE_DOCUMENT_PROFILE,
}


@dataclass
class BenchmarkRow:
    filename: str
    profile_name: str
    document_kind: str
    fields_found: int
    average_confidence: float
    elapsed_seconds: float


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", type=Path, help="Dossier contenant les images à traiter")
    args = parser.parse_args()

    if not args.directory.is_dir():
        print(f"Erreur : {args.directory} n'est pas un dossier.", file=sys.stderr)
        sys.exit(1)

    image_paths = sorted(p for p in args.directory.iterdir() if p.suffix.lower() in _IMAGE_EXTENSIONS)
    if not image_paths:
        print(f"Aucune image trouvée dans {args.directory} (extensions attendues : {sorted(_IMAGE_EXTENSIONS)}).")
        return

    pairs = _group_front_back(image_paths)
    rows: list[BenchmarkRow] = []

    for front_path, back_path in pairs:
        for profile_name, profile in _PROFILES.items():
            row = _run_one(front_path, back_path, profile_name, profile)
            rows.append(row)
            label = front_path.name + (f" + {back_path.name}" if back_path else "")
            print(
                f"{label:40s} | {profile_name:22s} | {row.document_kind:16s} | "
                f"champs={row.fields_found:2d} | confiance={row.average_confidence:5.1f}% | "
                f"{row.elapsed_seconds:5.2f}s"
            )

    _print_summary(rows)


def _run_one(front_path: Path, back_path: Path | None, profile_name: str, profile: PreprocessingProfile) -> BenchmarkRow:
    front_bytes = front_path.read_bytes()
    back_bytes = back_path.read_bytes() if back_path else None

    start = time.perf_counter()

    front_image = preprocess_for_ocr(decode_image(front_bytes), profile)
    front_text = extract_text(front_image)
    words = extract_words(front_image)
    combined_text = front_text

    if back_bytes:
        back_image = preprocess_for_ocr(decode_image(back_bytes), profile)
        back_text = extract_text(back_image)
        words.extend(extract_words(back_image))
        combined_text = f"{front_text}\n{back_text}"

    document_kind = detect_document_kind(combined_text)
    fields_found = _count_extracted_fields(document_kind, combined_text)
    average_confidence = sum(w.confidence for w in words) / len(words) if words else 0.0

    elapsed = time.perf_counter() - start

    return BenchmarkRow(
        filename=front_path.name,
        profile_name=profile_name,
        document_kind=document_kind.value,
        fields_found=fields_found,
        average_confidence=round(average_confidence, 1),
        elapsed_seconds=elapsed,
    )


def _count_extracted_fields(document_kind: DocumentKind, combined_text: str) -> int:
    """Nombre de champs non vides extraits par le parseur dédié à ce type de document — une mesure
    simple de "quantité" d'information récupérée, à mettre en regard de la confiance OCR (qui, elle,
    ne dit rien sur si les champs individuels sont corrects)."""
    if document_kind == DocumentKind.TITRE_PROVISOIRE:
        parsed = parse_provisional_receipt_fields(combined_text)
    elif document_kind == DocumentKind.RECEPISSE:
        parsed = parse_receipt_fields(combined_text)
    elif document_kind == DocumentKind.PASSEPORT:
        parsed = parse_passport_fields(combined_text)
    else:
        parsed = parse_cni_fields(combined_text)

    return sum(1 for value in vars(parsed).values() if value not in (None, "", [], False))


def _group_front_back(image_paths: list[Path]) -> list[tuple[Path, Path | None]]:
    """Associe chaque fichier "recto" à son "verso" si un fichier de nom correspondant existe
    (même préfixe, suffixes _recto/_front et _verso/_back), sinon le traite seul (cas du
    passeport, une seule page). Insensible à la casse."""
    by_stem = {p.stem.lower(): p for p in image_paths}
    used: set[str] = set()
    pairs: list[tuple[Path, Path | None]] = []

    for path in image_paths:
        stem = path.stem.lower()
        if stem in used:
            continue
        if _BACK_SUFFIX_PATTERN.search(stem):
            continue  # rattaché par son recto correspondant plus loin dans la boucle, pas seul
        back_path: Path | None = None
        if _FRONT_SUFFIX_PATTERN.search(stem):
            base = _FRONT_SUFFIX_PATTERN.sub("", stem)
            for suffix in ("_verso", "_back", "_v"):
                candidate = by_stem.get(base + suffix)
                if candidate:
                    back_path = candidate
                    used.add(candidate.stem.lower())
                    break
        pairs.append((path, back_path))
        used.add(stem)

    return pairs


def _print_summary(rows: list[BenchmarkRow]) -> None:
    print("\n--- Résumé par profil ---")
    for profile_name in _PROFILES:
        profile_rows = [r for r in rows if r.profile_name == profile_name]
        if not profile_rows:
            continue
        total_time = sum(r.elapsed_seconds for r in profile_rows)
        avg_confidence = sum(r.average_confidence for r in profile_rows) / len(profile_rows)
        avg_fields = sum(r.fields_found for r in profile_rows) / len(profile_rows)
        docs_per_minute = (len(profile_rows) / total_time * 60) if total_time > 0 else 0.0
        recognized = sum(1 for r in profile_rows if r.document_kind != DocumentKind.UNKNOWN.value)
        print(
            f"{profile_name:22s} | documents={len(profile_rows):3d} | "
            f"reconnus={recognized:3d}/{len(profile_rows):3d} | "
            f"champs moyens={avg_fields:4.1f} | confiance moyenne={avg_confidence:5.1f}% | "
            f"{docs_per_minute:5.1f} docs/min"
        )


if __name__ == "__main__":
    main()
