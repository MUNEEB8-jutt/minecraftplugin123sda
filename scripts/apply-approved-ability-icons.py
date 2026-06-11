from __future__ import annotations

import json
import math
import shutil
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
REVIEW_ROOT = ROOT / "review_assets/medieval_ability_icons_game_icons"
PACK_ROOT = ROOT / "src/main/resources/rp"
TEXTURE_ROOT = PACK_ROOT / "assets/legendary/textures/font/ability_icons"
FONT_FILE = PACK_ROOT / "assets/legendary/font/ability_icons.json"
CREDITS_FILE = PACK_ROOT / "assets/legendary/ABILITY_ICON_CREDITS.txt"
MANIFEST_FILE = PACK_ROOT / "assets_manifest.txt"

HUD_TICKS = 30
GLYPH_BASE = 0xEC00
CANVAS_SIZE = 96
ICON_SIZE = 68
GENERATED_PREFIXES = (
    "rp/assets/legendary/font/ability_icons.json",
    "rp/assets/legendary/textures/font/ability_icons/",
    "rp/assets/legendary/ABILITY_ICON_CREDITS.txt",
)

ANIMATION_PROFILES = {
    ("quantum_chronoblade", "signature"): ("orbit", 28),
    ("quantum_chronoblade", "ultimate"): ("radiate", 30),
    ("drakefire_katana", "signature"): ("spin", 24),
    ("drakefire_katana", "ultimate"): ("roar", 26),
    ("goldfang_dagger", "signature"): ("slash", 16),
    ("goldfang_dagger", "ultimate"): ("open", 24),
    ("bloodchain_ripper", "signature"): ("chain", 22),
    ("bloodchain_ripper", "ultimate"): ("shake", 24),
    ("frostnova_chakram", "signature"): ("freeze", 24),
    ("frostnova_chakram", "ultimate"): ("shimmer", 26),
    ("petalstorm_fanblade", "signature"): ("swirl", 26),
    ("petalstorm_fanblade", "ultimate"): ("bloom", 28),
    ("stormbreaker_relic", "signature"): ("zap", 18),
    ("stormbreaker_relic", "ultimate"): ("impact", 22),
    ("sanguine_pike", "signature"): ("lunge", 18),
    ("sanguine_pike", "ultimate"): ("spike", 24),
    ("voidglass_lich_staff", "signature"): ("open", 24),
    ("voidglass_lich_staff", "ultimate"): ("soul", 26),
    ("bifrost_wand", "signature"): ("prism", 24),
    ("bifrost_wand", "ultimate"): ("judgement", 26),
    ("necromancer_reaper", "signature"): ("sweep", 20),
    ("necromancer_reaper", "ultimate"): ("carousel", 28),
    ("timberlord_axe", "signature"): ("impact", 22),
    ("timberlord_axe", "ultimate"): ("fortress", 28),
    ("bloomshot_blaster", "signature"): ("bite", 24),
    ("bloomshot_blaster", "ultimate"): ("bloom", 28),
    ("hornhook_harpoon", "signature"): ("lunge", 18),
    ("hornhook_harpoon", "ultimate"): ("wave", 26),
    ("tempest_sonicbow", "signature"): ("triple", 22),
    ("tempest_sonicbow", "ultimate"): ("rail", 18),
}


def smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def profile_for(entry: dict[str, str]) -> tuple[str, int]:
    return ANIMATION_PROFILES.get((entry["weapon_id"], entry["ability_slot"]), ("pulse", 24))


def phase_texture_filename(entry: dict[str, str], phase: int) -> str:
    return f'{entry["weapon_id"]}_{entry["ability_slot"]}/phase_{phase:02d}.png'


def load_icon(source: Path) -> Image.Image:
    icon = Image.open(source).convert("RGBA")
    icon.thumbnail((ICON_SIZE, ICON_SIZE), Image.Resampling.LANCZOS)
    fitted = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    fitted.alpha_composite(icon, ((ICON_SIZE - icon.width) // 2, (ICON_SIZE - icon.height) // 2))
    return fitted


def apply_alpha(image: Image.Image, alpha: float) -> Image.Image:
    alpha = max(0.0, min(1.0, alpha))
    result = image.copy()
    channel = result.getchannel("A").point(lambda value: int(value * alpha))
    result.putalpha(channel)
    return result


def tint(image: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    result = image.copy()
    alpha = result.getchannel("A")
    solid = Image.new("RGBA", result.size, (*color, 0))
    solid.putalpha(alpha)
    return solid


def paste_transformed(canvas: Image.Image,
                      icon: Image.Image,
                      scale: float,
                      rotation: float,
                      x_offset: float,
                      y_offset: float,
                      alpha: float,
                      color: tuple[int, int, int] | None = None) -> None:
    scale = max(0.01, scale)
    size = max(1, int(round(ICON_SIZE * scale)))
    frame = icon.resize((size, size), Image.Resampling.LANCZOS)
    if color is not None:
        frame = tint(frame, color)
    if abs(rotation) > 0.01:
        frame = frame.rotate(rotation, resample=Image.Resampling.BICUBIC, expand=True)
    if alpha < 0.999:
        frame = apply_alpha(frame, alpha)
    x = int(round((CANVAS_SIZE - frame.width) / 2 + x_offset))
    y = int(round((CANVAS_SIZE - frame.height) / 2 + y_offset))
    canvas.alpha_composite(frame, (x, y))


def animation_values(profile: str, phase: int, duration: int) -> tuple[float, float, float, float, float]:
    progress = min(1.0, phase / max(1, duration))
    enter_ticks = max(3, int(round(duration * 0.24)))
    exit_start = max(enter_ticks + 1, int(round(duration * 0.72)))
    exit_ticks = max(3, duration - exit_start)
    intro = smoothstep(phase / enter_ticks)
    outro = 0.0 if phase <= exit_start else smoothstep((phase - exit_start) / exit_ticks)
    visible = intro * (1.0 - outro)

    scale = 0.72 + 0.36 * intro - 0.22 * outro
    rotation = 0.0
    x_offset = 0.0
    y_offset = 20.0 * (1.0 - intro) - 18.0 * outro

    if profile in {"spin", "swirl", "carousel", "orbit"}:
        rotation = 360.0 * progress * (1.15 if profile == "carousel" else 0.85)
        scale += 0.05 * math.sin(progress * math.tau * (2.0 if profile == "orbit" else 1.0))
    elif profile in {"slash", "lunge", "rail"}:
        x_offset = (-28.0 + 28.0 * intro) + 14.0 * outro
        y_offset += (-8.0 + 8.0 * intro)
        rotation = -18.0 + 18.0 * intro
        scale += 0.08 * math.sin(progress * math.pi)
    elif profile == "triple":
        x_offset = math.sin(progress * math.tau * 3.0) * 5.0 * visible
        rotation = math.sin(progress * math.tau * 2.0) * 8.0
    elif profile in {"impact", "zap", "spike", "shake"}:
        kick = math.sin(min(1.0, progress * 2.0) * math.pi)
        scale += 0.16 * kick
        x_offset = math.sin(phase * 2.4) * 3.0 * visible
        y_offset += math.cos(phase * 1.9) * 2.0 * visible
        rotation = math.sin(phase * 1.8) * (8.0 if profile == "shake" else 5.0)
    elif profile in {"open", "bloom", "fortress"}:
        scale = 0.46 + 0.66 * intro - 0.18 * outro
        rotation = math.sin(progress * math.pi) * (5.0 if profile == "bloom" else 2.0)
    elif profile in {"prism", "shimmer", "freeze", "judgement", "radiate", "soul"}:
        scale += 0.08 * math.sin(progress * math.tau * 2.0)
        rotation = math.sin(progress * math.tau) * 5.0
    elif profile in {"chain", "sweep", "bite", "wave", "roar"}:
        x_offset = math.sin(progress * math.tau) * 8.0 * visible
        rotation = math.sin(progress * math.tau) * 12.0
        scale += 0.06 * math.sin(progress * math.tau * 1.5)

    return scale, rotation, x_offset, y_offset, visible


def render_frame(icon: Image.Image, profile: str, phase: int, duration: int) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    scale, rotation, x_offset, y_offset, visible = animation_values(profile, phase, duration)
    progress = min(1.0, phase / max(1, duration))

    if profile in {"slash", "lunge", "rail", "sweep"}:
        for trail in range(2, 0, -1):
            paste_transformed(canvas, icon, scale * (0.92 - trail * 0.05), rotation,
                              x_offset - trail * 12.0, y_offset + trail * 3.0,
                              visible * (0.2 / trail), (255, 80, 80))
    elif profile in {"radiate", "prism", "judgement", "shimmer", "freeze", "soul"}:
        glow = 1.05 + 0.08 * math.sin(progress * math.tau * 2.0)
        color = {
            "freeze": (120, 230, 255),
            "prism": (255, 230, 90),
            "judgement": (255, 205, 70),
            "radiate": (185, 50, 255),
            "soul": (75, 255, 170),
        }.get(profile, (210, 245, 255))
        paste_transformed(canvas, icon, scale * glow, rotation, x_offset, y_offset,
                          visible * 0.34, color)
    elif profile in {"bloom", "fortress", "roar"}:
        paste_transformed(canvas, icon, scale * 1.08, rotation, x_offset, y_offset,
                          visible * 0.22, (255, 130, 190))
    elif profile in {"spin", "swirl", "carousel", "orbit"}:
        paste_transformed(canvas, icon, scale * 0.96, -rotation * 0.35, x_offset, y_offset,
                          visible * 0.18, (120, 220, 255))

    paste_transformed(canvas, icon, scale, rotation, x_offset, y_offset, visible)
    return canvas


def generate_textures(entries: list[dict[str, str]]) -> list[str]:
    shutil.rmtree(TEXTURE_ROOT, ignore_errors=True)
    TEXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    manifest_entries = []
    for entry in entries:
        profile, duration = profile_for(entry)
        icon = load_icon(REVIEW_ROOT / entry["png_preview"])
        for phase in range(HUD_TICKS + 1):
            filename = phase_texture_filename(entry, phase)
            destination = TEXTURE_ROOT / filename
            destination.parent.mkdir(parents=True, exist_ok=True)
            render_frame(icon, profile, phase, duration).save(destination)
            manifest_entries.append(f"rp/assets/legendary/textures/font/ability_icons/{filename}")
    return manifest_entries


def font_metrics(profile: str, phase: int, duration: int) -> tuple[int, int]:
    enter_ticks = max(3, int(round(duration * 0.24)))
    exit_start = max(enter_ticks + 1, int(round(duration * 0.72)))
    exit_ticks = max(3, duration - exit_start)
    intro = smoothstep(phase / enter_ticks)
    outro = 0.0 if phase <= exit_start else smoothstep((phase - exit_start) / exit_ticks)

    height = round(32 + 10 * intro - 4 * outro)
    ascent = round(6 + 17 * intro + 12 * outro)
    if profile in {"slash", "lunge", "rail", "zap"}:
        height = round(height * 0.92)
        ascent -= 1
    elif profile in {"radiate", "bloom", "fortress", "judgement"}:
        height = round(height * 1.06)
        ascent += 1
    return max(20, height), max(0, min(height, ascent))


def generate_font(entries: list[dict[str, str]]) -> None:
    FONT_FILE.parent.mkdir(parents=True, exist_ok=True)
    providers = []
    for icon_index, entry in enumerate(entries):
        profile, duration = profile_for(entry)
        for phase in range(HUD_TICKS + 1):
            glyph = chr(GLYPH_BASE + icon_index * (HUD_TICKS + 1) + phase)
            height, ascent = font_metrics(profile, phase, duration)
            providers.append(
                {
                    "type": "bitmap",
                    "file": f"legendary:font/ability_icons/{phase_texture_filename(entry, phase)}",
                    "ascent": ascent,
                    "height": height,
                    "chars": [glyph],
                }
            )
    FONT_FILE.write_text(json.dumps({"providers": providers}, indent=2) + "\n", encoding="ascii")


def generate_credits(entries: list[dict[str, str]]) -> None:
    lines = [
        "LegendaryWeaponsSMP ability HUD icon credits",
        "",
        "Icons sourced from https://game-icons.net and https://github.com/game-icons/icons",
        "License: Creative Commons Attribution 3.0 (CC BY 3.0)",
        "",
    ]
    for entry in entries:
        profile, duration = profile_for(entry)
        lines.append(
            f'{entry["weapon_id"]} {entry["ability_slot"]}: {entry["selected_icon"]} '
            f'by {entry["author"]} - {entry["official_page"]} '
            f'(animation: {profile}, {duration} ticks)'
        )
    CREDITS_FILE.write_text("\n".join(lines) + "\n", encoding="ascii")


def update_manifest(texture_entries: list[str]) -> None:
    lines = MANIFEST_FILE.read_text(encoding="utf-8").splitlines()
    kept = [
        line
        for line in lines
        if line and not any(line.startswith(prefix) for prefix in GENERATED_PREFIXES)
    ]
    kept.extend(
        [
            "rp/assets/legendary/font/ability_icons.json",
            "rp/assets/legendary/ABILITY_ICON_CREDITS.txt",
            *texture_entries,
        ]
    )
    MANIFEST_FILE.write_text("\n".join(kept) + "\n", encoding="utf-8")


def main() -> None:
    entries = json.loads((REVIEW_ROOT / "manifest.json").read_text(encoding="utf-8"))
    if len(entries) != 30:
        raise ValueError(f"Expected 30 approved icons, found {len(entries)}")
    texture_entries = generate_textures(entries)
    generate_font(entries)
    generate_credits(entries)
    update_manifest(texture_entries)
    print(f"Applied {len(entries)} approved ability icons to the resource-pack source")


if __name__ == "__main__":
    main()
