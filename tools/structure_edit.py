#!/usr/bin/env python3
import argparse
import gzip
import io
import os
import struct
from collections import OrderedDict

TAG_End = 0
TAG_Byte = 1
TAG_Short = 2
TAG_Int = 3
TAG_Long = 4
TAG_Float = 5
TAG_Double = 6
TAG_Byte_Array = 7
TAG_String = 8
TAG_List = 9
TAG_Compound = 10
TAG_Int_Array = 11
TAG_Long_Array = 12


class NBTList:
    def __init__(self, elem_type, items):
        self.elem_type = elem_type
        self.items = items


class NBTTag:
    def __init__(self, tag_type, value):
        self.tag_type = tag_type
        self.value = value


def _read(fmt, buf):
    size = struct.calcsize(fmt)
    data = buf.read(size)
    if len(data) != size:
        raise EOFError("Unexpected EOF while reading NBT")
    return struct.unpack(fmt, data)


def _read_string(buf):
    (length,) = _read(">H", buf)
    data = buf.read(length)
    if len(data) != length:
        raise EOFError("Unexpected EOF while reading NBT string")
    return data.decode("utf-8")


def _read_payload(tag_type, buf):
    if tag_type == TAG_Byte:
        return _read(">b", buf)[0]
    if tag_type == TAG_Short:
        return _read(">h", buf)[0]
    if tag_type == TAG_Int:
        return _read(">i", buf)[0]
    if tag_type == TAG_Long:
        return _read(">q", buf)[0]
    if tag_type == TAG_Float:
        return _read(">f", buf)[0]
    if tag_type == TAG_Double:
        return _read(">d", buf)[0]
    if tag_type == TAG_Byte_Array:
        (length,) = _read(">i", buf)
        data = buf.read(length)
        if len(data) != length:
            raise EOFError("Unexpected EOF while reading byte array")
        return list(struct.unpack(">%db" % length, data))
    if tag_type == TAG_String:
        return _read_string(buf)
    if tag_type == TAG_List:
        elem_type = _read(">b", buf)[0]
        (length,) = _read(">i", buf)
        items = []
        for _ in range(length):
            items.append(_read_payload(elem_type, buf))
        return NBTList(elem_type, items)
    if tag_type == TAG_Compound:
        out = OrderedDict()
        while True:
            tag_id = _read(">b", buf)[0]
            if tag_id == TAG_End:
                break
            name = _read_string(buf)
            value = _read_payload(tag_id, buf)
            out[name] = NBTTag(tag_id, value)
        return out
    if tag_type == TAG_Int_Array:
        (length,) = _read(">i", buf)
        data = buf.read(length * 4)
        if len(data) != length * 4:
            raise EOFError("Unexpected EOF while reading int array")
        return list(struct.unpack(">%di" % length, data))
    if tag_type == TAG_Long_Array:
        (length,) = _read(">i", buf)
        data = buf.read(length * 8)
        if len(data) != length * 8:
            raise EOFError("Unexpected EOF while reading long array")
        return list(struct.unpack(">%dq" % length, data))
    raise ValueError("Unknown tag type: %s" % tag_type)


def read_nbt(path):
    with open(path, "rb") as f:
        raw = f.read()
    compressed = raw[:2] == b"\x1f\x8b"
    if compressed:
        raw = gzip.decompress(raw)
    buf = io.BytesIO(raw)
    (root_type,) = _read(">b", buf)
    if root_type == TAG_End:
        return None, compressed
    root_name = _read_string(buf)
    root_value = _read_payload(root_type, buf)
    return NBTTag(root_type, root_value), root_name, compressed


def _write_string(buf, s):
    data = s.encode("utf-8")
    buf.write(struct.pack(">H", len(data)))
    buf.write(data)


def _write_payload(tag_type, value, buf):
    if tag_type == TAG_Byte:
        buf.write(struct.pack(">b", value))
    elif tag_type == TAG_Short:
        buf.write(struct.pack(">h", value))
    elif tag_type == TAG_Int:
        buf.write(struct.pack(">i", value))
    elif tag_type == TAG_Long:
        buf.write(struct.pack(">q", value))
    elif tag_type == TAG_Float:
        buf.write(struct.pack(">f", value))
    elif tag_type == TAG_Double:
        buf.write(struct.pack(">d", value))
    elif tag_type == TAG_Byte_Array:
        buf.write(struct.pack(">i", len(value)))
        buf.write(struct.pack(">%db" % len(value), *value))
    elif tag_type == TAG_String:
        _write_string(buf, value)
    elif tag_type == TAG_List:
        buf.write(struct.pack(">b", value.elem_type))
        buf.write(struct.pack(">i", len(value.items)))
        for item in value.items:
            _write_payload(value.elem_type, item, buf)
    elif tag_type == TAG_Compound:
        for name, tag in value.items():
            buf.write(struct.pack(">b", tag.tag_type))
            _write_string(buf, name)
            _write_payload(tag.tag_type, tag.value, buf)
        buf.write(struct.pack(">b", TAG_End))
    elif tag_type == TAG_Int_Array:
        buf.write(struct.pack(">i", len(value)))
        buf.write(struct.pack(">%di" % len(value), *value))
    elif tag_type == TAG_Long_Array:
        buf.write(struct.pack(">i", len(value)))
        buf.write(struct.pack(">%dq" % len(value), *value))
    else:
        raise ValueError("Unknown tag type: %s" % tag_type)


def write_nbt(path, root_tag, root_name, compress):
    buf = io.BytesIO()
    buf.write(struct.pack(">b", root_tag.tag_type))
    _write_string(buf, root_name)
    _write_payload(root_tag.tag_type, root_tag.value, buf)
    data = buf.getvalue()
    if compress:
        data = gzip.compress(data)
    with open(path, "wb") as f:
        f.write(data)


DIRS = {
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
    "east": (1, 0, 0),
}
LEFT = {"north": "west", "south": "east", "west": "south", "east": "north"}
RIGHT = {"north": "east", "south": "west", "west": "north", "east": "south"}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}


NON_CONNECTABLE = {
    "minecraft:air",
    "minecraft:water",
    "minecraft:lava",
    "minecraft:tall_grass",
    "minecraft:grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:seagrass",
    "minecraft:tall_seagrass",
    "minecraft:dead_bush",
    "minecraft:poppy",
    "minecraft:dandelion",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:wither_rose",
    "minecraft:sunflower",
    "minecraft:lilac",
    "minecraft:rose_bush",
    "minecraft:peony",
    "minecraft:torch",
    "minecraft:wall_torch",
    "minecraft:lantern",
    "minecraft:soul_lantern",
    "minecraft:glow_lichen",
    "minecraft:vine",
    "minecraft:cave_vines",
    "minecraft:cave_vines_plant",
    "minecraft:weeping_vines",
    "minecraft:twisting_vines",
    "minecraft:wheat",
    "minecraft:carrots",
    "minecraft:potatoes",
    "minecraft:beetroots",
    "minecraft:melon_stem",
    "minecraft:pumpkin_stem",
    "minecraft:sweet_berry_bush",
}


def _state_key(name, props):
    return (name, tuple(sorted(props.items())))


def _palette_entry(name, props):
    od = OrderedDict()
    od["Name"] = NBTTag(TAG_String, name)
    if props:
        pod = OrderedDict()
        for k, v in sorted(props.items()):
            pod[k] = NBTTag(TAG_String, v)
        od["Properties"] = NBTTag(TAG_Compound, pod)
    return od


def _is_floor_block(name):
    if name.endswith("_planks") or name.endswith("_log") or name.endswith("_wood"):
        return True
    if name in {
        "minecraft:cobblestone",
        "minecraft:mossy_cobblestone",
        "minecraft:stone",
        "minecraft:stone_bricks",
        "minecraft:mossy_stone_bricks",
        "minecraft:bricks",
        "minecraft:smooth_stone",
        "minecraft:polished_andesite",
        "minecraft:polished_diorite",
        "minecraft:polished_granite",
        "minecraft:andesite",
        "minecraft:diorite",
        "minecraft:granite",
    }:
        return True
    return False


def _is_connectable(name):
    return name not in NON_CONNECTABLE


def _axis(dir_name):
    return "z" if dir_name in ("north", "south") else "x"


def fix_structure(root_tag, is_hut):
    root = root_tag.value
    palette_tag = root["palette"]
    blocks_tag = root["blocks"]
    size_tag = root["size"]

    palette_list = palette_tag.value.items
    palette_index = {}
    for i, entry in enumerate(palette_list):
        name = entry["Name"].value
        props = {}
        if "Properties" in entry:
            props = {k: v.value for k, v in entry["Properties"].value.items()}
        palette_index[_state_key(name, props)] = i

    blocks_list = blocks_tag.value.items
    pos_to_block = {}
    for block in blocks_list:
        pos = tuple(block["pos"].value.items)
        pos_to_block[pos] = block

    def state_at(pos):
        block = pos_to_block.get(pos)
        if not block:
            return "minecraft:air", {}
        idx = block["state"].value
        entry = palette_list[idx]
        name = entry["Name"].value
        props = {}
        if "Properties" in entry:
            props = {k: v.value for k, v in entry["Properties"].value.items()}
        return name, props

    def get_or_create_state(name, props):
        key = _state_key(name, props)
        if key in palette_index:
            return palette_index[key]
        palette_list.append(_palette_entry(name, props))
        idx = len(palette_list) - 1
        palette_index[key] = idx
        return idx

    # Fix connectivity for panes, fences, and stairs
    for block in blocks_list:
        pos = tuple(block["pos"].value.items)
        idx = block["state"].value
        entry = palette_list[idx]
        name = entry["Name"].value
        props = {}
        if "Properties" in entry:
            props = {k: v.value for k, v in entry["Properties"].value.items()}

        updated = False
        if name.endswith("_pane") or name == "minecraft:iron_bars":
            for dir_name, delta in DIRS.items():
                npos = (pos[0] + delta[0], pos[1] + delta[1], pos[2] + delta[2])
                nname, _ = state_at(npos)
                props[dir_name] = "true" if _is_connectable(nname) else "false"
            updated = True
        elif name.endswith("_fence"):
            for dir_name, delta in DIRS.items():
                npos = (pos[0] + delta[0], pos[1] + delta[1], pos[2] + delta[2])
                nname, _ = state_at(npos)
                props[dir_name] = "true" if _is_connectable(nname) else "false"
            updated = True
        elif name.endswith("_stairs"):
            facing = props.get("facing", "north")
            half = props.get("half", "bottom")
            shape = "straight"

            def stair_facing_at(check_pos):
                nname, nprops = state_at(check_pos)
                if not nname.endswith("_stairs"):
                    return None
                if nprops.get("half", "bottom") != half:
                    return None
                return nprops.get("facing", "north")

            def can_take_shape(check_dir):
                npos = (pos[0] + DIRS[check_dir][0], pos[1], pos[2] + DIRS[check_dir][2])
                nname, nprops = state_at(npos)
                if not nname.endswith("_stairs"):
                    return True
                if nprops.get("facing", "north") != facing:
                    return True
                if nprops.get("half", "bottom") != half:
                    return True
                return False

            front_pos = (pos[0] + DIRS[facing][0], pos[1], pos[2] + DIRS[facing][2])
            front_facing = stair_facing_at(front_pos)
            if front_facing and _axis(front_facing) != _axis(facing) and can_take_shape(OPP[front_facing]):
                if front_facing == LEFT[facing]:
                    shape = "outer_left"
                else:
                    shape = "outer_right"
            else:
                back_pos = (pos[0] + DIRS[OPP[facing]][0], pos[1], pos[2] + DIRS[OPP[facing]][2])
                back_facing = stair_facing_at(back_pos)
                if back_facing and _axis(back_facing) != _axis(facing) and can_take_shape(back_facing):
                    if back_facing == LEFT[facing]:
                        shape = "inner_left"
                    else:
                        shape = "inner_right"

            if props.get("shape") != shape:
                props["shape"] = shape
                updated = True

        if updated:
            block["state"].value = get_or_create_state(name, props)

    if not is_hut:
        return

    # Interior decoration pass for huts
    size = size_tag.value.items
    max_x, max_y, max_z = size[0] - 1, size[1] - 1, size[2] - 1

    # Identify floor level
    floor_counts = {}
    for block in blocks_list:
        pos = tuple(block["pos"].value.items)
        idx = block["state"].value
        entry = palette_list[idx]
        name = entry["Name"].value
        if _is_floor_block(name):
            floor_counts[pos[1]] = floor_counts.get(pos[1], 0) + 1

    if not floor_counts:
        return
    floor_y = max(floor_counts.items(), key=lambda kv: kv[1])[0]
    if floor_y + 1 > max_y:
        return

    interior = []
    interior_set = set()
    for x in range(0, max_x + 1):
        for z in range(0, max_z + 1):
            name_below, _ = state_at((x, floor_y, z))
            name_here, _ = state_at((x, floor_y + 1, z))
            if _is_floor_block(name_below) and name_here == "minecraft:air":
                interior.append((x, floor_y + 1, z))
                interior_set.add((x, floor_y + 1, z))

    if not interior:
        return

    min_x = min(p[0] for p in interior)
    max_x_i = max(p[0] for p in interior)
    min_z = min(p[2] for p in interior)
    max_z_i = max(p[2] for p in interior)
    center_x = (min_x + max_x_i) / 2.0
    center_z = (min_z + max_z_i) / 2.0

    def dist2(p):
        return (p[0] - center_x) ** 2 + (p[2] - center_z) ** 2

    occupied = set()
    existing_blocks = {state_at((x, y, z))[0] for (x, y, z) in pos_to_block.keys()}
    has_container = any(b in {"minecraft:barrel", "minecraft:chest"} for b in existing_blocks)

    def add_block(pos, name, props=None):
        if props is None:
            props = {}
        if pos in pos_to_block:
            return False
        if pos[1] < 0 or pos[1] > max_y:
            return False
        block = OrderedDict()
        block["pos"] = NBTTag(TAG_List, NBTList(TAG_Int, [pos[0], pos[1], pos[2]]))
        block["state"] = NBTTag(TAG_Int, get_or_create_state(name, props))
        blocks_list.append(block)
        pos_to_block[pos] = block
        occupied.add(pos)
        return True

    def is_interior_candidate(pos):
        non_air = 0
        for dir_name, delta in DIRS.items():
            npos = (pos[0] + delta[0], pos[1], pos[2] + delta[2])
            nname, _ = state_at(npos)
            if nname != "minecraft:air":
                non_air += 1
        return non_air >= 2

    # Place bed
    bed_positions = []
    for pos in sorted(interior, key=dist2, reverse=True):
        if not is_interior_candidate(pos):
            continue
        for facing in ("north", "south", "west", "east"):
            head = (pos[0] + DIRS[facing][0], pos[1], pos[2] + DIRS[facing][2])
            beyond = (head[0] + DIRS[facing][0], head[1], head[2] + DIRS[facing][2])
            if head not in interior_set:
                continue
            if head in occupied or pos in occupied:
                continue
            if state_at(head)[0] != "minecraft:air":
                continue
            if state_at(pos)[0] != "minecraft:air":
                continue
            if state_at(beyond)[0] == "minecraft:air":
                continue
            bed_positions = [pos, head, facing]
            break
        if bed_positions:
            break

    if bed_positions:
        foot, head, facing = bed_positions
        add_block(foot, "minecraft:blue_bed", {"facing": facing, "part": "foot", "occupied": "false"})
        add_block(head, "minecraft:blue_bed", {"facing": facing, "part": "head", "occupied": "false"})

    # Place crafting table
    for pos in sorted(interior, key=dist2, reverse=True):
        if pos in occupied or not is_interior_candidate(pos):
            continue
        if state_at(pos)[0] != "minecraft:air":
            continue
        if any(state_at((pos[0] + d[0], pos[1], pos[2] + d[2]))[0] != "minecraft:air" for d in DIRS.values()):
            if add_block(pos, "minecraft:crafting_table"):
                break

    # Place barrel if no container exists
    if not has_container:
        for pos in sorted(interior, key=dist2):
            if pos in occupied or not is_interior_candidate(pos):
                continue
            if state_at(pos)[0] != "minecraft:air":
                continue
            # Find a wall to face away from
            facing = "north"
            for dir_name, delta in DIRS.items():
                npos = (pos[0] + delta[0], pos[1], pos[2] + delta[2])
                if state_at(npos)[0] != "minecraft:air":
                    facing = OPP[dir_name]
                    break
            if add_block(pos, "minecraft:barrel", {"facing": facing, "open": "false"}):
                break

    # Place lantern hanging
    lantern_placed = False
    for pos in sorted(interior, key=dist2):
        x, _, z = pos
        for y in range(floor_y + 2, max_y + 1):
            here = (x, y, z)
            above = (x, y + 1, z)
            if y + 1 > max_y:
                continue
            if state_at(here)[0] != "minecraft:air":
                continue
            if state_at(above)[0] == "minecraft:air":
                continue
            if add_block(here, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"}):
                lantern_placed = True
                break
        if lantern_placed:
            break

    # Place a small carpet patch near center
    for pos in sorted(interior, key=dist2):
        base = pos
        carpet_positions = [
            base,
            (base[0] + 1, base[1], base[2]),
            (base[0], base[1], base[2] + 1),
            (base[0] + 1, base[1], base[2] + 1),
        ]
        valid = True
        for cp in carpet_positions:
            if cp not in interior_set or cp in occupied:
                valid = False
                break
            if state_at(cp)[0] != "minecraft:air":
                valid = False
                break
        if not valid:
            continue
        for cp in carpet_positions:
            add_block(cp, "minecraft:gray_carpet")
        break


def main():
    parser = argparse.ArgumentParser(description="Fix structure NBT connectivity and decorate huts.")
    parser.add_argument("files", nargs="+", help="Structure NBT files to edit")
    args = parser.parse_args()

    for path in args.files:
        root_tag, root_name, compressed = read_nbt(path)
        if root_tag is None:
            continue
        is_hut = "abandoned_hut" in os.path.basename(path)
        fix_structure(root_tag, is_hut)
        write_nbt(path, root_tag, root_name, compressed)


if __name__ == "__main__":
    main()
