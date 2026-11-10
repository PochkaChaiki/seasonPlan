
import re
import argparse
from pathlib import Path
import resvg_py

# Watermark text to remove
WATERMARK = (
    r"Visual\s+Paradigm\s+Enterprise\s*"
    r"\[evaluation\s+copy\]"
)

TEXT_TAG = (
    rf"<text\b[^>]*>\s*{WATERMARK}\s*</text\s*>"
)

# Regex patterns applied in order
REMOVE_PATTERNS = [
    # Remove <g> containing only watermark <text>
    rf"<g\b[^>]*>\s*{TEXT_TAG}\s*</g\s*>",

    # Remove standalone watermark <text>
    TEXT_TAG,
]


def process_svg(input_file, output_svg, output_png, scale=2):
    svg = Path(input_file).read_text(encoding="utf-8-sig")

    for pattern in REMOVE_PATTERNS:
        svg, count = re.subn(
            pattern,
            "",
            svg,
            flags=re.DOTALL | re.IGNORECASE
        )
        print(f"Removed {count} matches")

    # Save cleaned SVG
    Path(output_svg).write_text(svg, encoding="utf-8")
    print(f"SVG saved: {output_svg}")

    # Convert SVG to PNG using resvg_py
    png_data = resvg_py.svg_to_bytes(
        svg_path=str(output_svg),
        zoom=scale
    )

    Path(output_png).write_bytes(png_data)
    print(f"PNG saved: {output_png}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input", help="Input SVG file")
    parser.add_argument("--scale", type=float, default=2)

    args = parser.parse_args()

    source = Path(args.input)
    output_svg = source.with_name(source.stem + "_clean.svg")
    output_png = source.with_name(source.stem + "_clean.png")

    process_svg(
        source,
        output_svg,
        output_png,
        args.scale
    )

