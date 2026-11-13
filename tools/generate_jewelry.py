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
the material's ingredient plus that type's Recipe scroll, which the
craft uses up.
Every piece opens the imbue page on right-click.

It also writes the scrolls (M5, Nov 10) to
Server/Item/Items/MagicTreasures/Scrolls/:

- a spell scroll per spell, MT_Scroll_<SpellId>, which opens the
  imbue page;
- a recipe scroll per jewelry type, MT_Recipe_<Type>: an ingredient,
  one per craft, of that type's recipes.

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
GEMS_OUT = SERVER / "Item" / "Items" / "MagicTreasures" / "Gems"
LANG = SERVER / "Languages" / "en-US" / "server.lang"

BEGIN = "# === BEGIN generated jewelry (tools/generate_jewelry.py) ==="
END = "# === END generated jewelry ==="

TYPES = ["Ring", "Necklace", "Bracelet"]
QUALITIES = ["Junk", "Common", "Uncommon", "Rare", "Epic", "Legendary"]

# The starter set (Nov 9), grown since: 3 types x 3 metals x 8 gem
# choices x 2 styles = 144 pieces (bracelets Nov 10; Emerald, Zephyr
# and Voidstone Nov 12). Grow it by adding to these lists. Iron, bone
# and Lord's come later.
STARTER_TYPES = ["Ring", "Necklace", "Bracelet"]
STARTER_MATERIALS = ["Copper", "Silver", "Gold"]
STARTER_GEMS = [None, "Topaz", "Ruby", "Sapphire", "Diamond", "Emerald", "Zephyr", "Voidstone"]
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

# Crafting (decision, Mark Nov 10; scroll as ingredient Nov 12): workbench
# recipes for plain pieces only, each needing one of the type's Recipe
# scrolls, used up by the craft (nothing is learned). Gems are set by
# imbuing; Great and Lord's pieces come from loot.
RECIPE_STYLE = "Regular"
RECIPE_QUANTITY = 4
RECIPE_BENCH = {"Id": "Workbench", "Type": "Crafting",
                "Categories": ["Workbench_Tinkering"]}

# Scroll art: the vanilla scroll model with our violet parchment, and its
# icon rendered from the model, like Mage Flame's scrolls (Nov 11,
# tools/make_scroll_art.py); vanilla recipe page until we have our own.
SCROLL_MODEL = "Items/Consumables/Scrolls/Scroll.blockymodel"
SCROLL_TEXTURE = "Items/MagicTreasures/Scrolls/Spell_Scroll_Texture.png"
SCROLL_ICON = "Icons/Items/MagicTreasures/MT_Spell_Scroll.png"
SCROLL_ICON_PROPERTIES = {"Scale": 0.735, "Translation": [0, -7.495],
                          "Rotation": [22.5, 45, 22.5]}
RECIPE_MODEL = "Items/Consumables/Recipes/Recipe.blockymodel"
RECIPE_TEXTURE = "Items/Consumables/Recipes/Recipe_Texture.png"
RECIPE_ICON = "Icons/ItemsGenerated/Recipe_Page.png"
# a spell scroll's quality by spell level: the MT_Spell_* qualities are the
# vanilla tiers with a violet slot (tools/make_slot_art.py)
SCROLL_QUALITIES = {1: "MT_Spell_Common", 2: "MT_Spell_Common",
                    3: "MT_Spell_Uncommon", 4: "MT_Spell_Uncommon",
                    5: "MT_Spell_Rare", 6: "MT_Spell_Rare"}


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
RING_GEMS = [None, "Topaz", "Ruby", "Sapphire", "Diamond", "Emerald", "Zephyr", "Voidstone"]


def ring_art(jewelry_type, material_id, gem_id, style_id, iid):
    """(model, texture, icon) for a ring with real art, else None."""
    if (jewelry_type != "Ring" or style_id not in RING_STYLES
            or material_id not in RING_MATERIALS or gem_id not in RING_GEMS):
        return None
    model = f"Items/MagicTreasures/Rings/Ring_{style_id}" +         ("_Set" if gem_id else "") + ".blockymodel"
    texture = f"Items/MagicTreasures/Rings/Ring_{material_id}" +         (f"_{gem_id}" if gem_id else "") + ".png"
    return model, texture, f"Icons/Items/MagicTreasures/{iid}.png"


def necklace_art(jewelry_type, material_id, gem_id, style_id, iid):
    """(model, texture, icon) for a necklace with real art, else None.
    Necklaces use the ring textures (tools/make_necklace_art.py)."""
    if (jewelry_type != "Necklace" or style_id not in RING_STYLES
            or material_id not in RING_MATERIALS or gem_id not in RING_GEMS):
        return None
    model = f"Items/MagicTreasures/Necklaces/Necklace_{style_id}" +         ("_Set" if gem_id else "") + ".blockymodel"
    texture = f"Items/MagicTreasures/Rings/Ring_{material_id}" +         (f"_{gem_id}" if gem_id else "") + ".png"
    return model, texture, f"Icons/Items/MagicTreasures/{iid}.png"


def bracelet_art(jewelry_type, material_id, gem_id, style_id, iid):
    """(model, texture, icon) for a bracelet with real art, else None.
    Bracelets use the ring textures (tools/make_bracelet_art.py)."""
    if (jewelry_type != "Bracelet" or style_id not in RING_STYLES
            or material_id not in RING_MATERIALS or gem_id not in RING_GEMS):
        return None
    model = f"Items/MagicTreasures/Bracelets/Bracelet_{style_id}" +         ("_Set" if gem_id else "") + ".blockymodel"
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

    for folder in (ITEMS_OUT, DEFS_OUT, SCROLLS_OUT, GEMS_OUT):
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
                        # the Treasures Case only takes items with this
                        # tag ("ItemTag": "Family=MT_Jewelry")
                        "Tags": {"Family": ["MT_Jewelry"]},
                        "Model": PLACEHOLDER_MODEL,
                        "Texture": PLACEHOLDER_TEXTURE,
                        "PlayerAnimationsId": "Item",
                        "MaxStack": 1,
                        "Scale": 0.6,
                        "ItemSoundSetId": "ISS_Items_Gems",
                    }
                    art = (ring_art(jewelry_type, material_id, gem_id,
                                    style_id, iid)
                           or necklace_art(jewelry_type, material_id,
                                           gem_id, style_id, iid)
                           or bracelet_art(jewelry_type, material_id,
                                           gem_id, style_id, iid))
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
                                       "Quantity": RECIPE_QUANTITY},
                                      {"ItemId": f"MT_Recipe_{jewelry_type}",
                                       "Quantity": 1}],
                            "BenchRequirement": [RECIPE_BENCH],
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
    gems = write_gems(load("Gems"), lang)
    write_lang(lang)
    print(f"Generated {count} jewelry items, {scrolls} scrolls and "
          f"{gems} cut gems and kits.")


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
            "IconProperties": SCROLL_ICON_PROPERTIES,
            "Quality": SCROLL_QUALITIES.get(level, "MT_Spell_Epic"),
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
        write_json(SCROLLS_OUT / f"{rid}.json", {
            "TranslationProperties": {
                "Name": f"server.items.{rid}.name",
                "Description": f"server.items.{rid}.description",
            },
            "Icon": RECIPE_ICON,
            "Quality": "MT_Recipe_Uncommon",
            "Categories": ["Items.Recipes"],
            "Tags": {"Type": ["Recipe"]},
            "Model": RECIPE_MODEL,
            "Texture": RECIPE_TEXTURE,
            "PlayerAnimationsId": "Item",
            "MaxStack": 10,
        })
        lang.append(f"items.{rid}.name={jewelry_type} Recipe")
        lang.append(f"items.{rid}.description=Needed to craft a "
                    f"{jewelry_type.lower()} at a workbench, with "
                    f"{RECIPE_QUANTITY} copper, silver or gold bars. "
                    "Each craft uses up one.")
        count += 1
    return count


# Gem cutting (M6, Nov 10). Cuts and their quality steps match the
# GemCut enum in Java. Each gem has one shape; the cut changes its
# colour and shine (tools/make_cut_gem_art.py).
CUTS = [("Flawed", -1, "a flawed cut", "75%"),
        ("Fine", 0, "a fine cut", "100%"),
        ("Flawless", 1, "a flawless cut", "125%"),
        ("Perfect", 2, "a perfect cut", "150%")]
# Mark (Nov 10): brilliant for diamond and ruby, the emerald cut for
# emerald and sapphire, the princess cut for topaz and voidstone;
# zephyr brilliant until decided
GEM_SHAPES = {"Diamond": "Brilliant", "Ruby": "Brilliant",
              "Emerald": "Emerald_Cut", "Sapphire": "Emerald_Cut",
              "Topaz": "Princess", "Voidstone": "Princess"}
SHAPE_MODELS = {"Brilliant": "Brilliant_Gem",
                "Emerald_Cut": "Emerald_Cut_Gem",
                "Princess": "Princess_Gem"}
KIT_ID = "MT_Gem_Cutters_Kit"
# was 40; Mark (Nov 10): magic jewelry should be kind of expensive
KIT_USES = 10


def shape_of(gem_id):
    return GEM_SHAPES.get(gem_id, "Brilliant")
CUT_PAGE = {"Type": "OpenCustomUI", "Page": {"Id": "MT_GemCut"}}
# The raw gem's glow and sparks (vanilla Rock_Gem_<Gem>.json: Light
# colour, Block_Gem_Sparks colour), so a cut gem shines like the ore
# (Mark, Nov 11).
GEM_GLOW = {
    "Diamond": ("#bbb", "#c2ebf4"),
    "Emerald": ("#5b6", "#60d682"),
    "Ruby": ("#b75", "#ffaa99"),
    "Sapphire": ("#58b", "#64c1ff"),
    "Topaz": ("#bb5", "#e2ae76"),
    "Voidstone": ("#514", "#c9b1ff"),
    "Zephyr": ("#5ab", "#9cebff"),
}


def glow(gem_id):
    """The item JSON's Light and Particles for a cut gem."""
    light, sparks = GEM_GLOW.get(gem_id, ("#bbb", "#ffffff"))
    return {
        "Light": {"Color": light, "Radius": 0},
        "Particles": [{"SystemId": "Block_Gem_Sparks", "Color": sparks}],
    }


def element_name(element):
    return "all-element" if element == "Elemental" else element


def describe_rolls(rolls):
    """A gem's element rolls in a few words, for example "Wind
    resistance and damage" or "Fire resistance, and Burn on hit"."""
    groups, extras, penalty = {}, [], ""
    for r in rolls:
        kind, element = r["Kind"], r.get("Element")
        if kind == "Crit":
            extras.append("crit chance")
        elif kind == "OnHit":
            extras.append(f"{r.get('Effect', element)} on hit")
        elif kind == "Resistance" and r["Max"] < 0:
            penalty = ", but less resistance to every element"
        else:
            groups.setdefault(element, []).append(kind.lower())
    parts = [f"{element_name(e)} {' and '.join(k)}"
             for e, k in groups.items()] + extras
    if len(parts) > 2:
        text = ", ".join(parts[:-1]) + ", and " + parts[-1]
    else:
        text = " and ".join(parts)
    return text + penalty


def sentence(text):
    return text[0].upper() + text[1:]


def cut_powers(gem, cut):
    """What a cut of this gem gives set in jewelry (ElementRolling in
    Java)."""
    rolls = gem.get("Elements", [])
    if cut == "Flawed":
        weak = gem.get("Weakness")
        if not weak:
            return "No element powers."
        weak = "every element" if weak == "Elemental" else weak
        return f"No element powers, and it may carry a weakness to {weak}."
    if cut == "Fine":
        kept = [r for r in rolls if r["Kind"] not in ("OnHit", "Crit")]
        if not kept:
            return ""
        return sentence(describe_rolls(kept)) + "; at half strength."
    text = sentence(describe_rolls(rolls))
    if cut == "Perfect" and gem.get("Bonus"):
        return (f"{text}; at full strength, with a chance of "
                f"{describe_rolls(gem['Bonus'])}.")
    return f"{text}."


def write_gems(gems, lang):
    """Writes the cut gems (MT_Gem_<Gem>_<Cut>) and the gem cutter's
    kit, which opens the cutting page."""
    count = 0
    for gem_id, gem in gems.items():
        for cut, steps, look, share in CUTS:
            gid = f"MT_Gem_{gem_id}_{cut}"
            shape = shape_of(gem_id)
            q = QUALITIES[max(0, min(quality_index(gem.get("Quality",
                                                            "Common"))
                                     + steps, len(QUALITIES) - 1))]
            write_json(GEMS_OUT / f"{gid}.json", {
                "TranslationProperties": {
                    "Name": f"server.items.{gid}.name",
                    "Description": f"server.items.{gid}.description",
                },
                "Icon": f"Icons/Items/MagicTreasures/{gid}.png",
                "Quality": q,
                "Categories": ["Items.Ingredients"],
                "Tags": {"Type": ["MT_Cut_Gem"]},
                "Model": "Items/MagicTreasures/Gems/"
                         + SHAPE_MODELS[shape] + ".blockymodel",
                "Texture": f"Items/MagicTreasures/Gems/{gem_id}_{shape}"
                           f"_{cut}.png",
                "PlayerAnimationsId": "Item",
                "Scale": 1.0,
                "MaxStack": 64,
                "ItemSoundSetId": "ISS_Items_Gems",
                **glow(gem_id),
                # right-click: choose the jewelry to set it in
                "Interactions": {
                    "Primary": {"Interactions": [IMBUE]},
                    "Secondary": {"Interactions": [IMBUE]},
                },
            })
            lang.append(f"items.{gid}.name={cut} {gem_id}")
            lang.append(f"items.{gid}.description=A {gem_id.lower()} "
                        f"given {look}. {cut_powers(gem, cut)} "
                        f"Set in jewelry, it adds {share} of "
                        "the gem's charge, recharges and rune boosts. "
                        "Right-click to choose the jewelry to set it in.")
            count += 1
    write_json(GEMS_OUT / f"{KIT_ID}.json", {
        "TranslationProperties": {
            "Name": f"server.items.{KIT_ID}.name",
            "Description": f"server.items.{KIT_ID}.description",
        },
        # the vanilla iron repair kit's art until the kit has its own
        "Icon": "Icons/Items/MagicTreasures/MT_Gem_Cutters_Kit.png",
        "Quality": "Uncommon",
        "Categories": ["Items.Tools"],
        "Tags": {"Type": ["Tool"]},
        "Model": "Items/Tools/Repair_Kit/Repair_Kit_Iron.blockymodel",
        "Texture": "Items/MagicTreasures/Gems/Gem_Cutters_Kit_Texture.png",
        "IconProperties": {"Scale": 0.58823, "Rotation": [341, 141, 8],
                           "Translation": [1, -13.5]},
        "PlayerAnimationsId": "Item",
        "MaxStack": 1,
        "MaxDurability": KIT_USES,
        "ItemSoundSetId": "ISS_Weapons_Wood",
        "Interactions": {
            "Primary": {"Interactions": [CUT_PAGE]},
            "Secondary": {"Interactions": [CUT_PAGE]},
        },
        "Recipe": {
            "TimeSeconds": 2,
            "Input": [{"ItemId": "Ingredient_Bar_Iron", "Quantity": 2},
                      {"ItemId": "Ingredient_Leather_Light", "Quantity": 1},
                      {"ItemId": "Rock_Gem_Topaz", "Quantity": 1}],
            "OutputQuantity": 1,
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting",
                                  "Categories": ["Workbench_Tools"]}],
        },
    })
    lang.append(f"items.{KIT_ID}.name=Gem Cutter's Kit")
    lang.append(f"items.{KIT_ID}.description=Right-click to cut the raw "
                "gems you carry. Better cuts come with your gem cutting "
                "level; harder gems need a higher level, and a cut at "
                f"the edge of your skill can shatter the gem. {KIT_USES} "
                "uses.")
    return count + 1


if __name__ == "__main__":
    main()
