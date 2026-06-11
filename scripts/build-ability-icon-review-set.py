from __future__ import annotations

import json
import shutil
import textwrap
from pathlib import Path

import requests
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "target/game-icons-source"
OUTPUT_ROOT = ROOT / "review_assets/medieval_ability_icons_game_icons"
SVG_ROOT = OUTPUT_ROOT / "original_svg"
PNG_ROOT = OUTPUT_ROOT / "preview_png"

ICONS = [
    ("quantum_chronoblade", "Bifrost Wand", "signature", "GRAVITY ARSENAL", "lorc", "black-hole-bolas"),
    ("quantum_chronoblade", "Bifrost Wand", "ultimate", "SINGULARITY ASCENSION", "lorc", "orbital-rays"),
    ("drakefire_katana", "Drakefire Katana", "signature", "WYRMRIDE CUT", "lorc", "dragon-spiral"),
    ("drakefire_katana", "Drakefire Katana", "ultimate", "RED DRAGON RAID", "lorc", "double-dragon"),
    ("goldfang_dagger", "Goldfang Dagger", "signature", "GILDED BLADE RUSH", "lorc", "quick-slash"),
    ("goldfang_dagger", "Goldfang Dagger", "ultimate", "GRAND HEIST", "lorc", "locked-chest"),
    ("bloodchain_ripper", "Bloodchain Ripper", "signature", "CHAINSAW REEL", "delapouite", "chainsaw"),
    ("bloodchain_ripper", "Bloodchain Ripper", "ultimate", "SLAUGHTER CONVEYOR", "lorc", "crossed-chains"),
    ("frostnova_chakram", "Frostnova Chakram", "signature", "COMMAND OF ICE", "delapouite", "ice-spell-cast"),
    ("frostnova_chakram", "Frostnova Chakram", "ultimate", "GLACIER MIRROR PALACE", "lorc", "mirror-mirror"),
    ("petalstorm_fanblade", "Petalstorm Fanblade", "signature", "KOI DRAGON DANCE", "delapouite", "circling-fish"),
    ("petalstorm_fanblade", "Petalstorm Fanblade", "ultimate", "HEAVENBLOOM FESTIVAL", "lorc", "flower-twirl"),
    ("stormbreaker_relic", "Stormbreaker Relic", "signature", "THUNDER VAULT", "lorc", "thunder-blade"),
    ("stormbreaker_relic", "Stormbreaker Relic", "ultimate", "JUDGEMENT OF THE SKYFORGE", "delapouite", "thor-hammer"),
    ("sanguine_pike", "Sanguine Pike", "signature", "CRIMSON JOUST", "lorc", "thrown-spear"),
    ("sanguine_pike", "Sanguine Pike", "ultimate", "IMPALEMENT COLOSSEUM", "lorc", "spears"),
    ("voidglass_lich_staff", "Voidglass Lich Staff", "signature", "COFFIN SNATCH", "lorc", "coffin"),
    ("voidglass_lich_staff", "Voidglass Lich Staff", "ultimate", "LICH KING PROCESSION", "delapouite", "skull-staff"),
    ("bifrost_wand", "Quantum Chronoblade", "signature", "PRISM CELL", "delapouite", "prism"),
    ("bifrost_wand", "Quantum Chronoblade", "ultimate", "CELESTIAL TRIBUNAL", "delapouite", "tribunal-jury"),
    ("necromancer_reaper", "Necromancer Reaper", "signature", "SOUL SEVER", "lorc", "reaper-scythe"),
    ("necromancer_reaper", "Necromancer Reaper", "ultimate", "DEATH CAROUSEL", "delapouite", "carousel"),
    ("timberlord_axe", "Timberlord Axe", "signature", "BEASTWOOD RAM", "skoll", "siege-ram"),
    ("timberlord_axe", "Timberlord Axe", "ultimate", "WALKING FORTRESS", "lorc", "locked-fortress"),
    ("bloomshot_blaster", "Bloomshot Blaster", "signature", "VENUS VOLLEY", "caro-asercion", "venus-flytrap"),
    ("bloomshot_blaster", "Bloomshot Blaster", "ultimate", "GARDEN APOCALYPSE", "lorc", "vine-flower"),
    ("hornhook_harpoon", "Hornhook Harpoon", "signature", "MONSTER LINE", "lorc", "harpoon-chain"),
    ("hornhook_harpoon", "Hornhook Harpoon", "ultimate", "KRAKEN MOORING", "delapouite", "kraken-tentacle"),
    ("tempest_sonicbow", "Tempest Sonicbow", "signature", "TRIPLE HUNTER SHOT", "lorc", "target-arrows"),
    ("tempest_sonicbow", "Tempest Sonicbow", "ultimate", "CRIMSON RAILSHOT", "lorc", "supersonic-arrow"),
]

AUTHOR_NAMES = {
    "caro-asercion": "Caro Asercion",
    "delapouite": "Delapouite",
    "lorc": "Lorc",
    "skoll": "Skoll",
}


def slug(value: str) -> str:
    return "_".join(part for part in value.lower().replace("-", " ").split() if part)


def clean_output() -> None:
    shutil.rmtree(OUTPUT_ROOT, ignore_errors=True)
    SVG_ROOT.mkdir(parents=True)
    PNG_ROOT.mkdir(parents=True)


def download_png(url: str, destination: Path) -> None:
    response = requests.get(url, timeout=30)
    response.raise_for_status()
    destination.write_bytes(response.content)
    with Image.open(destination) as image:
        image.verify()


def build_icons() -> list[dict[str, str]]:
    manifest: list[dict[str, str]] = []
    for weapon_id, display_name, slot, ability, author, icon in ICONS:
        source = SOURCE_ROOT / author / f"{icon}.svg"
        if not source.exists():
            raise FileNotFoundError(f"Missing Game-icons source: {source}")

        basename = f"{weapon_id}__{slot}__{slug(ability)}"
        svg_destination = SVG_ROOT / f"{basename}.svg"
        png_destination = PNG_ROOT / f"{basename}.png"
        png_url = f"https://game-icons.net/icons/ffffff/transparent/1x1/{author}/{icon}.png"
        shutil.copyfile(source, svg_destination)
        download_png(png_url, png_destination)

        manifest.append(
            {
                "weapon_id": weapon_id,
                "current_display_name": display_name,
                "ability_slot": slot,
                "ability": ability,
                "selected_icon": icon,
                "author": AUTHOR_NAMES[author],
                "license": "CC BY 3.0",
                "svg_file": svg_destination.relative_to(OUTPUT_ROOT).as_posix(),
                "png_preview": png_destination.relative_to(OUTPUT_ROOT).as_posix(),
                "official_page": f"https://game-icons.net/1x1/{author}/{icon}.html",
                "official_png": png_url,
                "raw_source": f"https://raw.githubusercontent.com/game-icons/icons/master/{author}/{icon}.svg",
            }
        )
    return manifest


def build_contact_sheet(manifest: list[dict[str, str]]) -> None:
    columns = 5
    card_width = 330
    card_height = 350
    rows = (len(manifest) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * card_width, rows * card_height), "#0f172a")
    draw = ImageDraw.Draw(sheet)

    for index, icon in enumerate(manifest):
        left = (index % columns) * card_width
        top = (index // columns) * card_height
        draw.rounded_rectangle(
            (left + 8, top + 8, left + card_width - 8, top + card_height - 8),
            radius=12,
            outline="#475569",
            width=2,
        )
        with Image.open(OUTPUT_ROOT / icon["png_preview"]).convert("RGBA") as preview:
            preview.thumbnail((236, 236), Image.Resampling.LANCZOS)
            x = left + (card_width - preview.width) // 2
            y = top + 24
            sheet.paste(preview, (x, y), preview)

        draw.text((left + 18, top + 272), icon["current_display_name"], fill="#f8fafc")
        label = f'{icon["ability_slot"].upper()}: {icon["ability"]}'
        for line_index, line in enumerate(textwrap.wrap(label, width=39)[:2]):
            draw.text((left + 18, top + 294 + line_index * 16), line, fill="#fbbf24")
        draw.text((left + 18, top + 329), f'{icon["selected_icon"]} - {icon["author"]}', fill="#94a3b8")

    sheet.save(OUTPUT_ROOT / "contact-sheet.png", optimize=True)


def write_review_files(manifest: list[dict[str, str]]) -> None:
    (OUTPUT_ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="ascii")
    shutil.copyfile(SOURCE_ROOT / "license.txt", OUTPUT_ROOT / "GAME_ICONS_LICENSE.txt")
    lines = [
        "# Medieval Ability Icon Review Set",
        "",
        "Review-only download bundle. Nothing in this folder is wired into the Minecraft plugin yet.",
        "",
        "Source: https://game-icons.net and https://github.com/game-icons/icons",
        "License: CC BY 3.0. Preserve author credits if approved assets are shipped.",
        "",
        "| Weapon backend ID | Current display name | Slot | Ability | Selected icon | Author |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for icon in manifest:
        lines.append(
            f'| {icon["weapon_id"]} | {icon["current_display_name"]} | {icon["ability_slot"]} | '
            f'{icon["ability"]} | [{icon["selected_icon"]}]({icon["official_page"]}) | {icon["author"]} |'
        )
    (OUTPUT_ROOT / "README.md").write_text("\n".join(lines) + "\n", encoding="ascii")


def main() -> None:
    if not SOURCE_ROOT.exists():
        raise FileNotFoundError(
            "Run: git clone --depth 1 https://github.com/game-icons/icons.git target/game-icons-source"
        )
    clean_output()
    manifest = build_icons()
    build_contact_sheet(manifest)
    write_review_files(manifest)
    print(f"Generated {len(manifest)} review icons in {OUTPUT_ROOT}")


if __name__ == "__main__":
    main()
