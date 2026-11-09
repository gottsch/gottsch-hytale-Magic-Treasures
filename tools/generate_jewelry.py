#!/usr/bin/env python3
"""Generate Magic Treasures jewelry item assets.

For every combination of type, material, gem and style it writes:

- an item asset:
  Server/Item/Items/MagicTreasures/Jewelry/<id>.json
- a jewelry definition with the same id:
  Server/MagicTreasures/Jewelry/<id>.json
- the item's name and description in a generated block of
  Server/Languages/en-US/server.lang

Ids look like MT_Ring_Gold, MT_Ring_Gold_Ruby_Great or
MT_Necklace_Silver_Lords; the Regular style is left out.

It reads the material, style and gem JSON in
Server/MagicTreasures/, so their quality decides the item's
quality, with the same rule as JewelryStats.quality.

Both generated folders are emptied first, so remove a
combination by leaving it out here and running again.

Usage (from the repo root):
    python tools/generate_jewelry.py          # the starter set
    python tools/generate_jewelry.py --all    # every combination
"""
import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVER = ROOT / "src" / "main" / "resources" / "Server"
DATA = SERVER / "MagicTreasures"
ITEMS_OUT = SERVER / "Item" / "Items" / "MagicTreasures" / "Jewelry"
DEFS_OUT = DATA / "Jewelry"
LANG = SERVER / "Languages" / "en-US" / "server.lang"

BEGIN = "# === BEGIN generated jewelry (tools/generate_jewelry.py) ==="
END = "# === END generated jewelry ==="

TYPES = ["Ring", "Necklace", "Bracelet"]
QUALITIES = ["Junk", "Common", "Uncommon", "Rare", "Epic", "Legendary"]

# The starter set (Nov 9): 2 x 3 x 4 x 2 = 48 pieces. Grow it by
# adding to these lists. Bracelets, iron, bone, the other gems and
# Lord's come later.
STARTER_TYPES = ["Ring", "Necklace"]
STARTER_MATERIALS = ["Copper", "Silver", "Gold"]
STARTER_GEMS = [None, "Topaz", "Ruby", "Sapphire"]
STARTER_STYLES = ["Regular", "Great"]

# Placeholder art until real jewelry models exist.
PLACEHOLDER_MODEL = "Items/Runes/Rune_Ability.blockymodel"
PLACEHOLDER_TEXTURE = "Items/Runes/Rune_Textures/Rune_Ability_Blank.png"
MATERIAL_ICONS = {
    "Bone": "Icons/ItemsGenerated/Bone_Pile.png",
}


def load(folder):
    return {p.stem: json.loads(p.read_text(encoding="utf-8"))
            for p in sorted((DATA / folder).glob("*.json"))}


def quality_index(q):
    try:
        return QUALITIES.index(q)
    except ValueError:
        return 1


def quality(material, style, gem):
    index = quality_index(material.get("Quality", "Common"))
    if gem is not None:
        index = max(index, quality_index(gem.get("Quality", "Common")))
    index = min(index + style.get("QualitySteps", 0),
                len(QUALITIES) - 1)
    return QUALITIES[index]


def fits(style, jewelry_type):
    types = style.get("Types")
    return not types or jewelry_type in types


def icon(material_id, gem_id):
    if gem_id:
        return f"Icons/ItemsGenerated/Rock_Gem_{gem_id}.png"
    return MATERIAL_ICONS.get(
        material_id,
        f"Icons/ItemsGenerated/Ingredient_Bar_{material_id}.png")


def item_id(jewelry_type, material_id, gem_id, style_id):
    parts = ["MT", jewelry_type, material_id]
    if gem_id:
        parts.append(gem_id)
    if style_id != "Regular":
        parts.append(style_id)
    return "_".join(parts)


def display_name(jewelry_type, material_id, gem_id, style):
    words = [style.get("Name", ""), material_id, gem_id or "",
             jewelry_type]
    return " ".join(w for w in words if w)


def description(jewelry_type, material_id, gem_id):
    text = f"A {material_id.lower()} {jewelry_type.lower()}"
    if gem_id:
        text += f" set with a {gem_id.lower()}"
    return text + "."


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n",
                    encoding="utf-8", newline="\n")


def write_lang(entries):
    text = LANG.read_text(encoding="utf-8") if LANG.exists() else ""
    if BEGIN in text:
        before = text[:text.index(BEGIN)]
        after = text[text.index(END) + len(END):] if END in text else ""
    else:
        before, after = text, ""
    before = before.rstrip("\n") + "\n\n" if before.strip() else ""
    block = "\n".join([BEGIN, *entries, END])
    LANG.write_text(before + block + "\n" + after.lstrip("\n"),
                    encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true",
                        help="every combination, not the starter set")
    args = parser.parse_args()

    materials = load("Materials")
    styles = load("Styles")
    gems = load("Gems")

    types = TYPES if args.all else STARTER_TYPES
    material_ids = list(materials) if args.all else STARTER_MATERIALS
    gem_ids = [None, *gems] if args.all else STARTER_GEMS
    style_ids = list(styles) if args.all else STARTER_STYLES

    for folder in (ITEMS_OUT, DEFS_OUT):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)

    lang = []
    count = 0
    for jewelry_type in types:
        for material_id in material_ids:
            material = materials[material_id]
            for gem_id in gem_ids:
                gem = gems[gem_id] if gem_id else None
                for style_id in style_ids:
                    style = styles[style_id]
                    if not fits(style, jewelry_type):
                        continue
                    iid = item_id(jewelry_type, material_id, gem_id,
                                  style_id)
                    q = quality(material, style, gem)
                    write_json(ITEMS_OUT / f"{iid}.json", {
                        "TranslationProperties": {
                            "Name": f"server.items.{iid}.name",
                            "Description":
                                f"server.items.{iid}.description",
                        },
                        "Icon": icon(material_id, gem_id),
                        "Quality": q,
                        "ItemLevel": material.get("MaxLevel", 1)
                        + style.get("MaxLevel", 0),
                        "Categories": ["Items.Ingredients"],
                        "Model": PLACEHOLDER_MODEL,
                        "Texture": PLACEHOLDER_TEXTURE,
                        "PlayerAnimationsId": "Item",
                        "MaxStack": 1,
                        "Scale": 0.6,
                        "ItemSoundSetId": "ISS_Items_Gems",
                    })
                    definition = {"Type": jewelry_type,
                                  "Material": material_id,
                                  "Style": style_id}
                    if gem_id:
                        definition["Gem"] = gem_id
                    write_json(DEFS_OUT / f"{iid}.json", definition)
                    lang.append(f"items.{iid}.name="
                                + display_name(jewelry_type,
                                               material_id, gem_id,
                                               style))
                    lang.append(f"items.{iid}.description="
                                + description(jewelry_type,
                                              material_id, gem_id))
                    count += 1

    write_lang(lang)
    print(f"Generated {count} jewelry items.")


if __name__ == "__main__":
    main()
