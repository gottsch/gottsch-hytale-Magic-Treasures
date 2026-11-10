#!/usr/bin/env python3
"""Generate Magic Treasures jewelry item assets.

For every combination of type, material, gem and style it writes:

- an item asset:
  Server/Item/Items/MagicTreasures/Jewelry/<id>.json
- a jewelry definition with the same id:
  Server/MagicTreasures/Jewelry/<id>.json
- the item's name and description in a generated block of
  Server/Languages/en-US/server.lang

Plain pieces (Regular style, no gem) get a workbench recipe of 4 of
the material's ingredient, which a Recipe scroll must teach first.
Every piece opens the imbue page on right-click.

It also writes the scrolls (M5, Nov 10) to
Server/Item/Items/MagicTreasures/Scrolls/:

- a spell scroll per spell, MT_Scroll_<SpellId>, which opens the
  imbue page;
- a recipe scroll per jewelry type, MT_Recipe_<Type>, which teaches
  that type's recipes.

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
SCROLLS_OUT = SERVER / "Item" / "Items" / "MagicTreasures" / "Scrolls"
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

# Right-click opens the imbue page (ImbuePageSupplier.ID), like a
# vanilla repair kit.
IMBUE = {"Type": "OpenCustomUI", "Page": {"Id": "MT_Imbue"}}

# Crafting (decision, Mark Nov 10): learned workbench recipes for
# plain pieces only. Gems are set by imbuing; Great and Lord's pieces
# come from loot.
RECIPE_STYLE = "Regular"
RECIPE_QUANTITY = 4
RECIPE_BENCH = {"Id": "Workbench", "Type": "Crafting",
                "Categories": ["Workbench_Tinkering"]}

# Scroll art: vanilla scroll and recipe models until we have our own.
SCROLL_MODEL = "Items/Consumables/Scrolls/Scroll.blockymodel"
SCROLL_TEXTURE = "Items/Consumables/Scrolls/Scroll_Texture.png"
SCROLL_ICON = "Icons/ItemsGenerated/Deco_Scroll.png"
RECIPE_MODEL = "Items/Consumables/Recipes/Recipe.blockymodel"
RECIPE_TEXTURE = "Items/Consumables/Recipes/Recipe_Texture.png"
RECIPE_ICON = "Icons/ItemsGenerated/Recipe_Page.png"
# a spell scroll's quality by spell level
SCROLL_QUALITIES = {1: "Common", 2: "Common", 3: "Uncommon",
                    4: "Uncommon", 5: "Rare", 6: "Rare"}


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


# Real art (Nov 10), from tools/make_ring_art.py: the starter rings'
# styles and metals. Anything else keeps the placeholder.
RING_STYLES = ["Regular", "Great"]
RING_MATERIALS = ["Copper", "Silver", "Gold"]
RING_GEMS = [None, "Topaz", "Ruby", "Sapphire"]


def ring_art(jewelry_type, material_id, gem_id, style_id, iid):
    """(model, texture, icon) for a ring with real art, else None."""
    if (jewelry_type != "Ring" or style_id not in RING_STYLES
            or material_id not in RING_MATERIALS or gem_id not in RING_GEMS):
        return None
    model = f"Items/MagicTreasures/Rings/Ring_{style_id}" +         ("_Set" if gem_id else "") + ".blockymodel"
    texture = f"Items/MagicTreasures/Rings/Ring_{material_id}" +         (f"_{gem_id}" if gem_id else "") + ".png"
    return model, texture, f"Icons/Items/MagicTreasures/{iid}.png"


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

    for folder in (ITEMS_OUT, DEFS_OUT, SCROLLS_OUT):
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
                    item = {
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
                        # the Jewelry Case only takes items with this
                        # tag ("ItemTag": "Family=MT_Jewelry")
                        "Tags": {"Family": ["MT_Jewelry"]},
                        "Model": PLACEHOLDER_MODEL,
                        "Texture": PLACEHOLDER_TEXTURE,
                        "PlayerAnimationsId": "Item",
                        "MaxStack": 1,
                        "Scale": 0.6,
                        "ItemSoundSetId": "ISS_Items_Gems",
                    }
                    art = ring_art(jewelry_type, material_id, gem_id,
                                   style_id, iid)
                    if art:
                        item["Model"], item["Texture"], item["Icon"] = art
                        item["Scale"] = 1.0
                    item["Interactions"] = {
                        "Secondary": {"Interactions": [IMBUE]}}
                    ingredient = material.get("Ingredient")
                    if (gem_id is None and style_id == RECIPE_STYLE
                            and ingredient):
                        item["Recipe"] = {
                            "TimeSeconds": 2,
                            "Input": [{"ItemId": ingredient,
                                       "Quantity": RECIPE_QUANTITY}],
                            "BenchRequirement": [RECIPE_BENCH],
                            "KnowledgeRequired": True,
                        }
                    write_json(ITEMS_OUT / f"{iid}.json", item)
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

    scrolls = write_scrolls(load("Spells"), types, lang)
    write_lang(lang)
    print(f"Generated {count} jewelry items and {scrolls} scrolls.")


def write_scrolls(spells, types, lang):
    """Writes a spell scroll per spell and a recipe scroll per type."""
    count = 0
    for spell_id, spell in spells.items():
        sid = f"MT_Scroll_{spell_id}"
        level = spell.get("Level", 1)
        write_json(SCROLLS_OUT / f"{sid}.json", {
            "TranslationProperties": {
                "Name": f"server.items.{sid}.name",
                "Description": f"server.items.{sid}.description",
            },
            "Icon": SCROLL_ICON,
            "Quality": SCROLL_QUALITIES.get(level, "Epic"),
            "ItemLevel": level,
            "Categories": ["Items.Recipes"],
            "Tags": {"Type": ["MT_Spell_Scroll"]},
            "Model": SCROLL_MODEL,
            "Texture": SCROLL_TEXTURE,
            "PlayerAnimationsId": "Item",
            "MaxStack": 10,
            "Interactions": {
                "Primary": {"Interactions": [IMBUE]},
                "Secondary": {"Interactions": [IMBUE]},
            },
        })
        name = spell.get("Name", spell_id)
        lang.append(f"items.{sid}.name=Spell Scroll: {name}")
        lang.append(f"items.{sid}.description=Level {level} spell. "
                    "Right-click, then choose a piece of jewelry to "
                    "imbue. Needs jewelry of spell level "
                    f"{level} or more, with no spell yet.")
        count += 1
    for jewelry_type in types:
        rid = f"MT_Recipe_{jewelry_type}"
        learn = {"Type": "MT_LearnJewelryRecipes",
                 "JewelryType": jewelry_type,
                 "Next": {"Type": "ModifyInventory",
                          "AdjustHeldItemQuantity": -1}}
        write_json(SCROLLS_OUT / f"{rid}.json", {
            "TranslationProperties": {
                "Name": f"server.items.{rid}.name",
                "Description": f"server.items.{rid}.description",
            },
            "Icon": RECIPE_ICON,
            "Quality": "Uncommon",
            "Categories": ["Items.Recipes"],
            "Tags": {"Type": ["Recipe"]},
            "Model": RECIPE_MODEL,
            "Texture": RECIPE_TEXTURE,
            "PlayerAnimationsId": "Item",
            "MaxStack": 10,
            "Consumable": True,
            "Interactions": {
                "Primary": {"Interactions": [learn]},
                "Secondary": {"Interactions": [learn]},
            },
        })
        lang.append(f"items.{rid}.name={jewelry_type} Recipe")
        lang.append(f"items.{rid}.description=Right-click to learn "
                    f"to craft {jewelry_type.lower()}s from "
                    f"{RECIPE_QUANTITY} copper, silver or gold bars "
                    "at a workbench. Used up once learned.")
        count += 1
    return count


if __name__ == "__main__":
    main()
