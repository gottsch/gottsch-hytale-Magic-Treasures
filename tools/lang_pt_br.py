#!/usr/bin/env python3
"""Brazilian Portuguese (pt-BR) text for the generated items (v1.2).

generate_jewelry.py writes the English names and descriptions from
word parts; Portuguese puts the words in another order and makes
adjectives agree with the noun's gender ("Safira Perfeita", "Rubi
Perfeito"), so this module composes them on its own, from the same
ids. Its entries() returns the lines of the generated block of
Server/Languages/pt-BR/server.lang.

Words follow vanilla pt-BR where the game has one: "Lingote" (bar),
"Bancada de Trabalho", "relâmpago", "Tempo de recarga" (cooldown),
and the gem names (Rubi, Safira, Pedra do Vazio, ...).

A missing translation falls back to the English text and is listed
by missing(), which generate_jewelry.py prints.
"""

# noun, gender ("m"/"f"), noun inside a sentence
TYPES = {
    "Ring": ("Anel", "m", "anel"),
    "Necklace": ("Colar", "m", "colar"),
    "Bracer": ("Braçadeira", "f", "braçadeira"),
}
MATERIALS = {"Copper": "cobre", "Silver": "prata", "Gold": "ouro",
             "Bone": "osso", "Iron": "ferro"}
# a style's word before the noun ("Grande Anel") or after it
STYLES_BEFORE = {"Great": "Grande"}
STYLES_AFTER = {"Lords": {"m": "Senhorial", "f": "Senhorial"}}
# name, gender
GEMS = {
    "Topaz": ("Topázio", "m"), "Ruby": ("Rubi", "m"),
    "Sapphire": ("Safira", "f"), "Diamond": ("Diamante", "m"),
    "Emerald": ("Esmeralda", "f"), "Zephyr": ("Zéfiro", "m"),
    "Voidstone": ("Pedra do Vazio", "f"),
}
# a cut gem's adjective (m, f), and the cut ("lapidação", feminine)
CUTS = {
    "Flawed": ("Imperfeito", "Imperfeita", "imperfeita"),
    "Fine": ("Fino", "Fina", "fina"),
    "Flawless": ("Impecável", "Impecável", "impecável"),
    "Perfect": ("Perfeito", "Perfeita", "perfeita"),
}
# an element: its word, "resistance to" it, "damage of" it
ELEMENTS = {
    "Fire": ("fogo", "ao fogo", "de fogo"),
    "Ice": ("gelo", "ao gelo", "de gelo"),
    "Lightning": ("relâmpago", "ao relâmpago", "de relâmpago"),
    "Earth": ("terra", "à terra", "de terra"),
    "Poison": ("veneno", "ao veneno", "de veneno"),
    "Water": ("água", "à água", "de água"),
    "Wind": ("vento", "ao vento", "de vento"),
    "Elemental": ("todos os elementos", "a todos os elementos",
                  "de todos os elementos"),
}
STATUSES = {"Burn": "Queimadura", "Poison": "Envenenamento",
            "Slow": "Lentidão", "Stun": "Atordoamento",
            "Root": "Enraizamento"}

SPELLS = {
    "Barrier": "Barreira",
    "Cat Sight": "Visão Felina",
    "Drain": "Dreno",
    "Lesser Drain": "Dreno Menor",
    "Greater Drain": "Dreno Maior",
    "Fire Strike": "Golpe de Fogo",
    "Greater Fire Strike": "Golpe de Fogo Maior",
    "Frost Strike": "Golpe de Gelo",
    "Greater Frost Strike": "Golpe de Gelo Maior",
    "Stone Strike": "Golpe de Pedra",
    "Greater Stone Strike": "Golpe de Pedra Maior",
    "Storm Strike": "Golpe de Tempestade",
    "Greater Storm Strike": "Golpe de Tempestade Maior",
    "Venom Strike": "Golpe Venenoso",
    "Greater Venom Strike": "Golpe Venenoso Maior",
    "Fire Ward": "Proteção contra Fogo",
    "Frost Ward": "Proteção contra Gelo",
    "Stone Ward": "Proteção contra Terra",
    "Storm Ward": "Proteção contra Tempestade",
    "Venom Ward": "Proteção contra Veneno",
    "Greater Ward": "Proteção Maior",
    "Flame Weapon": "Arma Flamejante",
    "Frost Weapon": "Arma Congelante",
    "Stone Weapon": "Arma de Pedra",
    "Storm Weapon": "Arma da Tempestade",
    "Venom Weapon": "Arma Venenosa",
    "Healing": "Cura",
    "Lesser Healing": "Cura Menor",
    "Greater Healing": "Cura Maior",
    "Speed": "Velocidade",
    "Greater Speed": "Velocidade Maior",
    "Strength": "Força",
    "Quick Strength": "Força Rápida",
    "Greater Strength": "Força Maior",
    "Stoneskin": "Pele de Pedra",
    "Thorns": "Espinhos",
    "Vampiric Kiss": "Beijo Vampírico",
    "Vampiric Bite": "Mordida Vampírica",
    "Vampiric Embrace": "Abraço Vampírico",
}

NAMED = {
    "MT_Ring_Vampire_Lich": (
        "Anel do Lich Vampiro",
        "Arrancado da mão de um lich que se recusava a continuar morto. "
        "Ele se alimenta do que você golpeia e não pede nada em troca, "
        "até que você caia."),
    "MT_Bracer_Thornguard": (
        "Guarda-Espinhos",
        "Suas farpas de ferro foram cultivadas, não forjadas. Elas mordem "
        "quem golpeia você, e mordem você também, só um pouco."),
    "MT_Necklace_Last_Stand": (
        "Amuleto da Última Resistência",
        "Forjado para um capitão que jurou nunca abandonar a muralha. "
        "Quando a muralha cai, ele cumpre esse juramento por você."),
    "MT_Ring_Starfall": (
        "Estrela Cadente",
        "Esmaltado com um pedaço do céu noturno. Quem o usa enxerga como "
        "as estrelas, e golpeia como uma estrela cadente."),
}

KIT_NAME = "Kit de Lapidação"

_missing = []


def missing():
    """The English texts that had no translation, for a warning."""
    return sorted(set(_missing))


def _get(table, key, fallback):
    if key in table:
        return table[key]
    _missing.append(str(key))
    return fallback


def article(gender):
    return "um" if gender == "m" else "uma"


def join(parts):
    """"a e b", "a, b e c" (no comma before "e" in Portuguese)."""
    if len(parts) <= 1:
        return "".join(parts)
    return ", ".join(parts[:-1]) + " e " + parts[-1]


def sentence(text):
    return text[0].upper() + text[1:] if text else text


def gem_name(gem_id):
    return _get(GEMS, gem_id, (gem_id, "m"))


def piece_name(jewelry_type, material_id, gem_id, style_id):
    """"Grande Anel de Ouro com Rubi"."""
    noun, gender, _ = _get(TYPES, jewelry_type,
                           (jewelry_type, "m", jewelry_type.lower()))
    words = []
    if style_id in STYLES_BEFORE:
        words.append(STYLES_BEFORE[style_id])
    words.append(noun)
    if style_id in STYLES_AFTER:
        words.append(STYLES_AFTER[style_id][gender])
    metal = _get(MATERIALS, material_id, material_id.lower())
    words.append("de " + metal[0].upper() + metal[1:])
    if gem_id:
        words.append("com " + gem_name(gem_id)[0])
    return " ".join(words)


def piece_description(jewelry_type, material_id, gem_id):
    """"Um anel de ouro cravejado com um rubi."."""
    _, gender, lower = _get(TYPES, jewelry_type,
                            (jewelry_type, "m", jewelry_type.lower()))
    text = (f"{sentence(article(gender))} {lower} de "
            f"{_get(MATERIALS, material_id, material_id.lower())}")
    if gem_id:
        name, gem_gender = gem_name(gem_id)
        set_word = "cravejado" if gender == "m" else "cravejada"
        text += f" {set_word} com {article(gem_gender)} {name.lower()}"
    return text + "."


def element(e, form=0):
    return _get(ELEMENTS, e, (e.lower(), "a " + e.lower(),
                              "de " + e.lower()))[form]


def describe_rolls(rolls):
    """A gem's element rolls in a few words, as describe_rolls in
    generate_jewelry.py: "resistência ao fogo e Queimadura ao
    acertar"."""
    groups, extras, penalty = {}, [], ""
    for r in rolls:
        kind, e = r["Kind"], r.get("Element")
        if kind == "Crit":
            extras.append("chance de acerto crítico")
        elif kind == "OnHit":
            status = r.get("Effect", e)
            extras.append(f"{_get(STATUSES, status, status)} ao acertar")
        elif kind == "Resistance" and r["Max"] < 0:
            penalty = ", mas menos resistência a todos os elementos"
        elif kind == "Damage":
            extras.insert(0, f"dano {element(e, 2)} adicionado aos golpes")
        else:
            groups.setdefault(e, []).append(kind)
    parts = []
    for e, kinds in groups.items():
        words = []
        for k in kinds:
            if k == "Resistance":
                words.append(f"resistência {element(e, 1)}")
            else:
                words.append(f"{k.lower()} {element(e, 2)}")
        parts.append(join(words))
    return join(parts + extras) + penalty


def cut_powers(gem, cut):
    """What a cut of this gem gives set in jewelry (cut_powers in
    generate_jewelry.py, in Portuguese)."""
    rolls = gem.get("Elements", [])
    if cut == "Flawed":
        weak = gem.get("Weakness")
        if not weak:
            return "Sem poderes elementais."
        return ("Sem poderes elementais, e pode trazer uma fraqueza "
                f"{element(weak, 1)}.")
    if cut == "Fine":
        kept = [r for r in rolls if r["Kind"] not in ("OnHit", "Crit")]
        if not kept:
            return ""
        return sentence(describe_rolls(kept)) + "; com metade da força."
    text = sentence(describe_rolls(rolls))
    if cut == "Perfect" and gem.get("Bonus"):
        return (f"{text}; com força total, e chance de "
                f"{describe_rolls(gem['Bonus'])}.")
    return f"{text}."


def entries(pieces, spells, types, gems, named, recipe_quantity,
            kit_uses, cuts):
    """The lines of the pt-BR generated block.

    pieces: (item id, type, material, gem or None, style) per piece;
    spells: {id: spell JSON}; types: the jewelry types with recipes;
    gems: {id: gem JSON}; named: named_pieces.json's list;
    cuts: generate_jewelry.CUTS, (cut, steps, look, share)."""
    out = []
    for iid, jewelry_type, material_id, gem_id, style_id in pieces:
        out.append(f"items.{iid}.name="
                   + piece_name(jewelry_type, material_id, gem_id,
                                style_id))
        out.append(f"items.{iid}.description="
                   + piece_description(jewelry_type, material_id, gem_id))
    for piece in named:
        nid = piece["Id"]
        name, lore = _get(NAMED, nid, (piece["Name"], piece.get("Lore", "")))
        out.append(f"items.{nid}.name={name}")
        out.append(f"items.{nid}.description={lore}")
        out.append(f"items.{nid}.lore={lore}")
    for spell_id, spell in spells.items():
        sid = f"MT_Scroll_{spell_id}"
        level = spell.get("Level", 1)
        name = spell_name(spell_id, spell)
        out.append(f"items.{sid}.name=Pergaminho de Feitiço: {name}")
        out.append(f"items.{sid}.description=Feitiço de nível {level}. "
                   "Clique com o botão direito e escolha uma joia para "
                   "imbuir. Precisa de uma joia de nível de feitiço "
                   f"{level} ou mais, ainda sem feitiço.")
    for jewelry_type in types:
        noun, gender, lower = _get(TYPES, jewelry_type,
                                   (jewelry_type, "m", jewelry_type.lower()))
        rid = f"MT_Recipe_{jewelry_type}"
        out.append(f"items.{rid}.name=Receita de {noun}")
        out.append(f"items.{rid}.description=Necessária para fabricar "
                   f"{article(gender)} {lower} numa Bancada de Trabalho, "
                   f"com {recipe_quantity} lingotes de cobre, prata ou "
                   "ouro. Cada fabricação consome uma.")
    for gem_id, gem in gems.items():
        name, gender = gem_name(gem_id)
        for cut, _, _, share in cuts:
            gid = f"MT_Gem_{gem_id}_{cut}"
            adjective = CUTS[cut][0 if gender == "m" else 1]
            powers = cut_powers(gem, cut)
            out.append(f"items.{gid}.name={name} {adjective}")
            out.append(f"items.{gid}.description={sentence(article(gender))} "
                       f"{name.lower()} "
                       f"com lapidação {CUTS[cut][2]}. "
                       + (powers + " " if powers else "")
                       + f"Cravejada numa joia, acrescenta {share} da "
                       "carga, das recargas e dos bônus de runa da gema. "
                       "Clique com o botão direito para escolher a joia "
                       "onde cravejá-la.")
    out.append(f"items.MT_Gem_Cutters_Kit.name={KIT_NAME}")
    out.append("items.MT_Gem_Cutters_Kit.description=Clique com o botão "
               "direito para lapidar as gemas brutas que você carrega. "
               "Lapidações melhores vêm com o seu nível de lapidação; "
               "gemas mais difíceis exigem um nível mais alto, e uma "
               "lapidação no limite da sua habilidade pode estilhaçar a "
               f"gema. {kit_uses} usos.")
    for spell_id, spell in spells.items():
        out.append(f"magictreasures.spells.{spell_id}.name="
                   + spell_name(spell_id, spell))
    return out


def spell_name(spell_id, spell):
    english = spell.get("Name", spell_id.replace("_", " "))
    return _get(SPELLS, english, english)
