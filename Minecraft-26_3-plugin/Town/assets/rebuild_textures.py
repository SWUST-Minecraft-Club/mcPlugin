#!/usr/bin/env python3
"""从保留的生图原稿机械转换 Minecraft 贴图；--check 只验证像素。"""
from argparse import ArgumentParser
from pathlib import Path

from PIL import Image


def textures(root):
    source = root / "assets/source"
    target = root / "src/main/resources/craftengine/resourcepack/assets/town/textures"
    sheet = Image.open(source / "town-material-reference.png")
    width, height = sheet.size
    quadrants = {
        "front": (0, 0, width // 2, height // 2),
        "side": (width // 2, 0, width, height // 2),
        "top": (0, height // 2, width // 2, height),
    }
    for name, box in quadrants.items():
        converted = (sheet.crop(box).resize((32, 32), Image.Resampling.NEAREST)
                     .convert("RGB").quantize(colors=32).convert("RGB"))
        yield target / f"block/town_hall_{name}.png", converted
    tool = Image.open(source / "surveyor-reference.png").convert("RGBA")
    tool = tool.crop(tool.getchannel("A").getbbox())
    tool.thumbnail((30, 30), Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (32, 32))
    canvas.alpha_composite(tool, ((32 - tool.width) // 2, (32 - tool.height) // 2))
    yield target / "item/surveyor.png", canvas


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    for path, expected in textures(root):
        if args.check:
            actual = Image.open(path)
            if (actual.mode != expected.mode or actual.size != expected.size
                    or actual.tobytes() != expected.tobytes()):
                raise SystemExit(f"贴图不一致：{path.relative_to(root)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            expected.save(path)
        print(f"{'验证' if args.check else '写入'}：{path.relative_to(root)}")


if __name__ == "__main__":
    main()
