from __future__ import annotations

import json
from pathlib import Path

import av
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_DIR = ROOT / "src/main/resources/rp/assets/minecraft/textures/item"
SPRITE_SIZE = 128


def center_crop_square(image: Image.Image) -> Image.Image:
    width, height = image.size
    edge = min(width, height)
    left = (width - edge) // 2
    top = (height - edge) // 2
    return image.crop((left, top, left + edge, top + edge))


def sampled_frames(source: Path, target_fps: int) -> list[Image.Image]:
    container = av.open(str(source))
    stream = container.streams.video[0]
    frames = [frame.to_image().convert("RGBA") for frame in container.decode(video=0)]
    duration = float(stream.duration * stream.time_base) if stream.duration else len(frames) / float(stream.average_rate)
    container.close()

    desired = max(2, round(duration * target_fps))
    if desired >= len(frames):
        selected = frames
    else:
        indices = [round(i * (len(frames) - 1) / (desired - 1)) for i in range(desired)]
        selected = [frames[index] for index in indices]

    return [
        center_crop_square(frame).resize((SPRITE_SIZE, SPRITE_SIZE), Image.Resampling.LANCZOS)
        for frame in selected
    ]


def write_looping_texture(source_name: str, texture_name: str, target_fps: int, frame_ticks: int) -> tuple[list[Image.Image], list[int]]:
    source = ROOT / source_name
    if not source.exists():
        raise FileNotFoundError(f"Missing video clip: {source}")

    frames = sampled_frames(source, target_fps)
    strip = Image.new("RGBA", (SPRITE_SIZE, SPRITE_SIZE * len(frames)), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, SPRITE_SIZE * index))

    texture = TEXTURE_DIR / f"{texture_name}.png"
    strip.save(texture, optimize=True)

    forward = list(range(len(frames)))
    backward = list(range(len(frames) - 2, 0, -1))
    loop = forward + backward
    metadata_frames: list[int | dict[str, int]]
    if frame_ticks == 1:
        metadata_frames = loop
    else:
        metadata_frames = [{"index": index, "time": frame_ticks} for index in loop]

    metadata = {
        "animation": {
            "interpolate": True,
            "frames": metadata_frames,
        }
    }
    texture.with_suffix(".png.mcmeta").write_text(json.dumps(metadata, indent=2) + "\n", encoding="ascii")
    print(
        f"{source.name} -> {texture.name}: "
        f"{len(frames)} unique frames, {len(loop) * frame_ticks} game ticks per seamless loop"
    )
    return frames, loop


def main() -> None:
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    write_looping_texture("gamerfleet.mp4", "bat_sigil", target_fps=20, frame_ticks=1)
    write_looping_texture("gamerfleet-lip-lip.mp4", "warrior_sigil", target_fps=10, frame_ticks=2)


if __name__ == "__main__":
    main()
