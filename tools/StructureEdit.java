import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class StructureEdit {
    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;

    private static final Map<String, int[]> DIRS = Map.of(
            "north", new int[]{0, 0, -1},
            "south", new int[]{0, 0, 1},
            "west", new int[]{-1, 0, 0},
            "east", new int[]{1, 0, 0}
    );
    private static final Map<String, String> LEFT = Map.of(
            "north", "west",
            "south", "east",
            "west", "south",
            "east", "north"
    );
    private static final Map<String, String> RIGHT = Map.of(
            "north", "east",
            "south", "west",
            "west", "north",
            "east", "south"
    );
    private static final Map<String, String> OPP = Map.of(
            "north", "south",
            "south", "north",
            "west", "east",
            "east", "west"
    );

    private static final Set<String> NON_CONNECTABLE = new HashSet<>(Arrays.asList(
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
            "minecraft:sweet_berry_bush"
    ));
    private static final String LOOT_TABLE = "eduhzzepunks_productive_hoe:chests/abandoned_farming_cache";

    private StructureEdit() {
    }

    private static final class Tag {
        final byte type;
        Object value;

        Tag(byte type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class ListTag {
        final byte elemType;
        List<Object> items;

        ListTag(byte elemType, List<Object> items) {
            this.elemType = elemType;
            this.items = items;
        }
    }

    private static final class State {
        final String name;
        final Map<String, String> props;

        State(String name, Map<String, String> props) {
            this.name = name;
            this.props = props;
        }
    }

    private static final class DoorInfo {
        final int x;
        final int y;
        final int z;
        final String facing;

        DoorInfo(int x, int y, int z, String facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
        }
    }

    private static final class NbtRoot {
        final Tag rootTag;
        final String rootName;
        final boolean compressed;

        NbtRoot(Tag rootTag, String rootName, boolean compressed) {
            this.rootTag = rootTag;
            this.rootName = rootName;
            this.compressed = compressed;
        }
    }

    private static NbtRoot readNbt(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        boolean compressed = raw.length >= 2 && (raw[0] == (byte) 0x1f && raw[1] == (byte) 0x8b);
        byte[] data;
        if (compressed) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                data = gzip.readAllBytes();
            }
        } else {
            data = raw;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte rootType = in.readByte();
            if (rootType == TAG_END) {
                return null;
            }
            String rootName = readString(in);
            Tag rootTag = new Tag(rootType, readPayload(rootType, in));
            return new NbtRoot(rootTag, rootName, compressed);
        }
    }

    private static void writeNbt(Path path, NbtRoot root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(root.rootTag.type);
            writeString(out, root.rootName);
            writePayload(root.rootTag.type, root.rootTag.value, out);
        }
        byte[] data = baos.toByteArray();
        if (root.compressed) {
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(gz)) {
                gzip.write(data);
            }
            Files.write(path, gz.toByteArray());
        } else {
            Files.write(path, data);
        }
    }

    private static Object readPayload(byte type, DataInputStream in) throws IOException {
        switch (type) {
            case TAG_BYTE:
                return in.readByte();
            case TAG_SHORT:
                return in.readShort();
            case TAG_INT:
                return in.readInt();
            case TAG_LONG:
                return in.readLong();
            case TAG_FLOAT:
                return in.readFloat();
            case TAG_DOUBLE:
                return in.readDouble();
            case TAG_BYTE_ARRAY: {
                int length = in.readInt();
                byte[] arr = new byte[length];
                in.readFully(arr);
                return arr;
            }
            case TAG_STRING:
                return readString(in);
            case TAG_LIST: {
                byte elemType = in.readByte();
                int length = in.readInt();
                List<Object> items = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    items.add(readPayload(elemType, in));
                }
                return new ListTag(elemType, items);
            }
            case TAG_COMPOUND: {
                LinkedHashMap<String, Tag> map = new LinkedHashMap<>();
                while (true) {
                    byte tagId = in.readByte();
                    if (tagId == TAG_END) {
                        break;
                    }
                    String name = readString(in);
                    map.put(name, new Tag(tagId, readPayload(tagId, in)));
                }
                return map;
            }
            case TAG_INT_ARRAY: {
                int length = in.readInt();
                int[] arr = new int[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = in.readInt();
                }
                return arr;
            }
            case TAG_LONG_ARRAY: {
                int length = in.readInt();
                long[] arr = new long[length];
                for (int i = 0; i < length; i++) {
                    arr[i] = in.readLong();
                }
                return arr;
            }
            default:
                throw new IOException("Unknown tag type " + type);
        }
    }

    private static void writePayload(byte type, Object value, DataOutputStream out) throws IOException {
        switch (type) {
            case TAG_BYTE:
                out.writeByte((byte) value);
                break;
            case TAG_SHORT:
                out.writeShort((short) value);
                break;
            case TAG_INT:
                out.writeInt((int) value);
                break;
            case TAG_LONG:
                out.writeLong((long) value);
                break;
            case TAG_FLOAT:
                out.writeFloat((float) value);
                break;
            case TAG_DOUBLE:
                out.writeDouble((double) value);
                break;
            case TAG_BYTE_ARRAY: {
                byte[] arr = (byte[]) value;
                out.writeInt(arr.length);
                out.write(arr);
                break;
            }
            case TAG_STRING:
                writeString(out, (String) value);
                break;
            case TAG_LIST: {
                ListTag list = (ListTag) value;
                out.writeByte(list.elemType);
                out.writeInt(list.items.size());
                for (Object item : list.items) {
                    writePayload(list.elemType, item, out);
                }
                break;
            }
            case TAG_COMPOUND: {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> map = (LinkedHashMap<String, Tag>) value;
                for (Map.Entry<String, Tag> entry : map.entrySet()) {
                    out.writeByte(entry.getValue().type);
                    writeString(out, entry.getKey());
                    writePayload(entry.getValue().type, entry.getValue().value, out);
                }
                out.writeByte(TAG_END);
                break;
            }
            case TAG_INT_ARRAY: {
                int[] arr = (int[]) value;
                out.writeInt(arr.length);
                for (int v : arr) {
                    out.writeInt(v);
                }
                break;
            }
            case TAG_LONG_ARRAY: {
                long[] arr = (long[]) value;
                out.writeInt(arr.length);
                for (long v : arr) {
                    out.writeLong(v);
                }
                break;
            }
            default:
                throw new IOException("Unknown tag type " + type);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] data = new byte[length];
        in.readFully(data);
        return new String(data, "UTF-8");
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes("UTF-8");
        out.writeShort(data.length);
        out.write(data);
    }

    private static boolean isFloorBlock(String name) {
        if (name.endsWith("_planks") || name.endsWith("_log") || name.endsWith("_wood")) {
            return true;
        }
        return Set.of(
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
                "minecraft:granite"
        ).contains(name);
    }

    private static boolean isConnectable(String name) {
        return !NON_CONNECTABLE.contains(name);
    }

    private static String axis(String dir) {
        return ("north".equals(dir) || "south".equals(dir)) ? "z" : "x";
    }

    private static Map<String, String> getProps(LinkedHashMap<String, Tag> entry) {
        Tag propsTag = entry.get("Properties");
        if (propsTag == null) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Tag> propsMap = (LinkedHashMap<String, Tag>) propsTag.value;
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Tag> prop : propsMap.entrySet()) {
            out.put(prop.getKey(), (String) prop.getValue().value);
        }
        return out;
    }

    private static String stateKey(String name, Map<String, String> props) {
        if (props.isEmpty()) {
            return name;
        }
        List<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder(name);
        for (String key : keys) {
            sb.append("|").append(key).append("=").append(props.get(key));
        }
        return sb.toString();
    }

    private static LinkedHashMap<String, Tag> paletteEntry(String name, Map<String, String> props) {
        LinkedHashMap<String, Tag> entry = new LinkedHashMap<>();
        entry.put("Name", new Tag(TAG_STRING, name));
        if (!props.isEmpty()) {
            LinkedHashMap<String, Tag> propTag = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(props.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                propTag.put(key, new Tag(TAG_STRING, props.get(key)));
            }
            entry.put("Properties", new Tag(TAG_COMPOUND, propTag));
        }
        return entry;
    }

    private static int getOrCreateState(List<Object> paletteList, Map<String, Integer> paletteIndex, String name, Map<String, String> props) {
        String key = stateKey(name, props);
        Integer existing = paletteIndex.get(key);
        if (existing != null) {
            return existing;
        }
        paletteList.add(paletteEntry(name, props));
        int idx = paletteList.size() - 1;
        paletteIndex.put(key, idx);
        return idx;
    }

    private static String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static int getInt(Tag tag) {
        return ((Number) tag.value).intValue();
    }

    private static int[] parsePos(String key) {
        String[] parts = key.split(",", -1);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static State stateAt(Map<String, LinkedHashMap<String, Tag>> posToBlock, List<Object> paletteList, int x, int y, int z) {
        LinkedHashMap<String, Tag> block = posToBlock.get(posKey(x, y, z));
        if (block == null) {
            return new State("minecraft:air", new LinkedHashMap<>());
        }
        Tag stateTag = block.get("state");
        if (stateTag == null) {
            return new State("minecraft:air", new LinkedHashMap<>());
        }
        int idx = getInt(stateTag);
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
        String name = (String) entry.get("Name").value;
        Map<String, String> props = getProps(entry);
        return new State(name, props);
    }

    private static double dist2(int x, int z, double centerX, double centerZ) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz;
    }

    private static boolean isInteriorCandidate(Map<String, LinkedHashMap<String, Tag>> posToBlock, List<Object> paletteList, int x, int y, int z) {
        int nonAir = 0;
        for (int[] delta : DIRS.values()) {
            State neighbor = stateAt(posToBlock, paletteList, x + delta[0], y + delta[1], z + delta[2]);
            if (!"minecraft:air".equals(neighbor.name)) {
                nonAir += 1;
            }
        }
        return nonAir >= 2;
    }

    private static int findFloorY(Map<String, LinkedHashMap<String, Tag>> posToBlock, List<Object> paletteList) {
        Map<Integer, Integer> floorCounts = new HashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, Tag>> entry : posToBlock.entrySet()) {
            int[] pos = parsePos(entry.getKey());
            State state = stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]);
            if (isGroundBlock(state.name)) {
                floorCounts.put(pos[1], floorCounts.getOrDefault(pos[1], 0) + 1);
            }
        }
        if (floorCounts.isEmpty()) {
            return -1;
        }
        int bestY = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : floorCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestY = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestY;
    }

    private static void placeGardenBarrel(List<Object> blocksList,
                                          Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                          List<Object> paletteList,
                                          Map<String, Integer> paletteIndex,
                                          int maxY,
                                          int maxX,
                                          int maxZ) {
        boolean hasBarrel = false;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if ("minecraft:barrel".equals(name)) {
                hasBarrel = true;
                break;
            }
        }
        if (hasBarrel) {
            return;
        }

        int floorY = findFloorY(posToBlock, paletteList);
        if (floorY < 0 || floorY + 1 > maxY) {
            return;
        }

        int innerMinX = Math.min(1, maxX);
        int innerMinZ = Math.min(1, maxZ);
        int innerMaxX = Math.max(0, maxX - 1);
        int innerMaxZ = Math.max(0, maxZ - 1);
        if (innerMaxX <= innerMinX || innerMaxZ <= innerMinZ) {
            return;
        }

        double centerX = maxX / 2.0;
        double centerZ = maxZ / 2.0;
        List<int[]> candidates = new ArrayList<>();
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                State below = stateAt(posToBlock, paletteList, x, floorY, z);
                if (!isGardenSupportBlock(below.name)) {
                    continue;
                }
                State above = stateAt(posToBlock, paletteList, x, floorY + 1, z);
                if (!"minecraft:air".equals(above.name)) {
                    continue;
                }
                if (x == innerMinX || z == innerMinZ || x == innerMaxX || z == innerMaxZ) {
                    candidates.add(new int[]{x, floorY + 1, z});
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(p -> dist2(p[0], p[2], centerX, centerZ)));
        int[] chosen = candidates.get(0);

        String facing = "north";
        if (chosen[0] == innerMinX) {
            facing = "west";
        } else if (chosen[0] == innerMaxX) {
            facing = "east";
        } else if (chosen[2] == innerMinZ) {
            facing = "north";
        } else if (chosen[2] == innerMaxZ) {
            facing = "south";
        }

        setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                chosen[0], chosen[1], chosen[2],
                "minecraft:barrel", Map.of("facing", facing, "open", "false"), lootBarrelNbt());

        int compX = Math.min(maxX, Math.max(0, chosen[0] + (chosen[0] == 0 ? 1 : -1)));
        int compZ = chosen[2];
        State compAbove = stateAt(posToBlock, paletteList, compX, chosen[1], compZ);
        if ("minecraft:air".equals(compAbove.name)) {
            setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    compX, chosen[1], compZ, "minecraft:composter", Map.of("level", "0"), null);
        }
    }

    private static void addGardenFences(List<Object> blocksList,
                                        Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                        List<Object> paletteList,
                                        Map<String, Integer> paletteIndex,
                                        int maxY,
                                        int maxX,
                                        int maxZ) {
        int floorY = findFloorY(posToBlock, paletteList);
        if (floorY < 0 || floorY + 1 > maxY) {
            return;
        }
        int minFarmX = Integer.MAX_VALUE;
        int maxFarmX = Integer.MIN_VALUE;
        int minFarmZ = Integer.MAX_VALUE;
        int maxFarmZ = Integer.MIN_VALUE;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!"minecraft:farmland".equals(name)) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y != floorY) {
                continue;
            }
            minFarmX = Math.min(minFarmX, x);
            maxFarmX = Math.max(maxFarmX, x);
            minFarmZ = Math.min(minFarmZ, z);
            maxFarmZ = Math.max(maxFarmZ, z);
        }

        int ringMinX = 0;
        int ringMaxX = maxX;
        int ringMinZ = 0;
        int ringMaxZ = maxZ;
        if (minFarmX != Integer.MAX_VALUE) {
            ringMinX = Math.max(0, minFarmX - 1);
            ringMaxX = Math.min(maxX, maxFarmX + 1);
            ringMinZ = Math.max(0, minFarmZ - 1);
            ringMaxZ = Math.min(maxZ, maxFarmZ + 1);
        }

        for (int x = ringMinX; x <= ringMaxX; x++) {
            for (int z = ringMinZ; z <= ringMaxZ; z++) {
                if (!(x == ringMinX || z == ringMinZ || x == ringMaxX || z == ringMaxZ)) {
                    continue;
                }
                State base = stateAt(posToBlock, paletteList, x, floorY, z);
                if (isGardenBaseReplaceable(base.name)) {
                    setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            x, floorY, z, "minecraft:oak_planks", Collections.emptyMap(), null);
                } else if (!isGardenSupportBlock(base.name)) {
                    continue;
                }

                int fenceY = floorY + 1;
                State here = stateAt(posToBlock, paletteList, x, fenceY, z);
                if (!isFenceReplaceable(here.name)) {
                    continue;
                }
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        x, fenceY, z, "minecraft:oak_fence", Map.of("waterlogged", "false"), null);
            }
        }
    }

    private static boolean isGardenBaseReplaceable(String name) {
        if ("minecraft:water".equals(name) || "minecraft:lava".equals(name)) {
            return true;
        }
        return isFenceReplaceable(name);
    }

    private static boolean isFenceReplaceable(String name) {
        if ("minecraft:air".equals(name)) {
            return true;
        }
        if ("minecraft:water".equals(name) || "minecraft:lava".equals(name)) {
            return false;
        }
        return NON_CONNECTABLE.contains(name);
    }

    private static boolean placeInteriorAgainstWall(List<Object> blocksList,
                                                    Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                                    List<Object> paletteList,
                                                    Map<String, Integer> paletteIndex,
                                                    int maxY,
                                                    List<int[]> interiorAsc,
                                                    Set<String> occupied,
                                                    String blockName,
                                                    Map<String, String> props) {
        for (int[] pos : interiorAsc) {
            String key = posKey(pos[0], pos[1], pos[2]);
            if (occupied.contains(key)) {
                continue;
            }
            if (!isInteriorCandidate(posToBlock, paletteList, pos[0], pos[1], pos[2])) {
                continue;
            }
            if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]).name)) {
                continue;
            }
            boolean hasWall = false;
            for (int[] delta : DIRS.values()) {
                State neighbor = stateAt(posToBlock, paletteList, pos[0] + delta[0], pos[1], pos[2] + delta[2]);
                if (!"minecraft:air".equals(neighbor.name)) {
                    hasWall = true;
                    break;
                }
            }
            if (!hasWall) {
                continue;
            }
            if (addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    pos[0], pos[1], pos[2], blockName, props)) {
                occupied.add(key);
                return true;
            }
        }
        return false;
    }

    private static boolean placeFacingBlockAgainstWall(List<Object> blocksList,
                                                       Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                                       List<Object> paletteList,
                                                       Map<String, Integer> paletteIndex,
                                                       int maxY,
                                                       List<int[]> interiorAsc,
                                                       Set<String> occupied,
                                                       String blockName) {
        for (int[] pos : interiorAsc) {
            String key = posKey(pos[0], pos[1], pos[2]);
            if (occupied.contains(key)) {
                continue;
            }
            if (!isInteriorCandidate(posToBlock, paletteList, pos[0], pos[1], pos[2])) {
                continue;
            }
            if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]).name)) {
                continue;
            }
            String facing = "north";
            boolean hasWall = false;
            for (Map.Entry<String, int[]> dir : DIRS.entrySet()) {
                int[] delta = dir.getValue();
                State neighbor = stateAt(posToBlock, paletteList, pos[0] + delta[0], pos[1], pos[2] + delta[2]);
                if (!"minecraft:air".equals(neighbor.name)) {
                    facing = OPP.get(dir.getKey());
                    hasWall = true;
                    break;
                }
            }
            if (!hasWall) {
                continue;
            }
            if (addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    pos[0], pos[1], pos[2], blockName, Map.of("facing", facing, "lit", "false"))) {
                occupied.add(key);
                return true;
            }
        }
        return false;
    }

    private static void placePottedPlantAbove(List<Object> blocksList,
                                              Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                              List<Object> paletteList,
                                              Map<String, Integer> paletteIndex,
                                              int maxY,
                                              String targetBlock,
                                              String pottedBlock) {
        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!targetBlock.equals(name)) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y + 1 > maxY) {
                continue;
            }
            if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, x, y + 1, z).name)) {
                continue;
            }
            setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    x, y + 1, z, pottedBlock, Collections.emptyMap(), null);
            return;
        }
    }

    private static void fixRoofGaps(List<Object> blocksList,
                                    Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                    List<Object> paletteList,
                                    Map<String, Integer> paletteIndex,
                                    int maxY,
                                    int maxX,
                                    int maxZ) {
        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!name.endsWith("_stairs")) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y <= 0) {
                continue;
            }

            Map<String, String> props = getProps(entry);
            String facing = props.getOrDefault("facing", "north");
            String inward = OPP.getOrDefault(facing, "south");
            int[] delta = DIRS.get(inward);
            if (delta == null) {
                continue;
            }

            int gx = x + delta[0];
            int gz = z + delta[2];
            if (gx < 0 || gx > maxX || gz < 0 || gz > maxZ) {
                continue;
            }
            State gap = stateAt(posToBlock, paletteList, gx, y, gz);
            if (!"minecraft:air".equals(gap.name)) {
                continue;
            }
            State support = stateAt(posToBlock, paletteList, gx, y - 1, gz);
            if ("minecraft:air".equals(support.name)) {
                continue;
            }
            State ahead = stateAt(posToBlock, paletteList, gx + delta[0], y, gz + delta[2]);
            if (!ahead.name.endsWith("_stairs")) {
                continue;
            }
            String aheadFacing = ahead.props.getOrDefault("facing", "north");
            if (!aheadFacing.equals(facing)) {
                continue;
            }

            setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    gx, y, gz, name,
                    Map.of(
                            "facing", facing,
                            "half", props.getOrDefault("half", "bottom"),
                            "shape", "straight",
                            "waterlogged", props.getOrDefault("waterlogged", "false")
                    ),
                    null);
        }
    }

    private static String roofFillFromStairs(String stairName) {
        if (stairName == null || !stairName.endsWith("_stairs")) {
            return "minecraft:oak_planks";
        }
        String base = stairName.substring(0, stairName.length() - "_stairs".length());
        String[] woodTypes = new String[]{
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "bamboo", "crimson", "warped"
        };
        for (String wood : woodTypes) {
            if (base.endsWith(wood)) {
                return base + "_planks";
            }
        }
        if (base.endsWith("stone_brick")) {
            return base + "s";
        }
        if (base.endsWith("stone")) {
            return base;
        }
        return "minecraft:oak_planks";
    }

    private static void removeBlock(List<Object> blocksList,
                                    Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                    int x,
                                    int y,
                                    int z) {
        String key = posKey(x, y, z);
        LinkedHashMap<String, Tag> block = posToBlock.remove(key);
        if (block != null) {
            blocksList.remove(block);
        }
    }

    private static void fixRoofPyramid(List<Object> blocksList,
                                       Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                       List<Object> paletteList,
                                       Map<String, Integer> paletteIndex,
                                       int maxY,
                                       int maxX,
                                       int maxZ,
                                       int floorY) {
        Map<String, Integer> stairCounts = new HashMap<>();
        Map<String, Map<String, String>> stairProps = new HashMap<>();
        int minRoofY = Integer.MAX_VALUE;
        int maxRoofY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxXLocal = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZLocal = Integer.MIN_VALUE;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!name.endsWith("_stairs")) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int y = ((Number) posList.items.get(1)).intValue();
            if (y <= floorY + 1) {
                continue;
            }
            int x = ((Number) posList.items.get(0)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            stairCounts.put(name, stairCounts.getOrDefault(name, 0) + 1);
            stairProps.putIfAbsent(name, getProps(entry));
            minRoofY = Math.min(minRoofY, y);
            maxRoofY = Math.max(maxRoofY, y);
            minX = Math.min(minX, x);
            maxXLocal = Math.max(maxXLocal, x);
            minZ = Math.min(minZ, z);
            maxZLocal = Math.max(maxZLocal, z);
        }
        if (stairCounts.isEmpty()) {
            return;
        }
        String roofStairs = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : stairCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                roofStairs = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        if (roofStairs == null) {
            return;
        }
        Map<String, String> baseProps = stairProps.getOrDefault(roofStairs, new HashMap<>());
        String roofFill = roofFillFromStairs(roofStairs);
        String roofSlab = roofStairs.substring(0, roofStairs.length() - "_stairs".length()) + "_slab";

        int[] wallBounds = findWallBounds(blocksList, paletteList, floorY);
        int wallMinX;
        int wallMaxX;
        int wallMinZ;
        int wallMaxZ;
        int roofBaseY;
        if (wallBounds != null) {
            wallMinX = wallBounds[0];
            wallMaxX = wallBounds[1];
            wallMinZ = wallBounds[2];
            wallMaxZ = wallBounds[3];
            roofBaseY = wallBounds[4];
        } else {
            wallMinX = minX;
            wallMaxX = maxXLocal;
            wallMinZ = minZ;
            wallMaxZ = maxZLocal;
            roofBaseY = minRoofY;
        }

        int xMin = Math.max(0, wallMinX - 1);
        int xMax = Math.min(maxX, wallMaxX + 1);
        int zMin = Math.max(0, wallMinZ - 1);
        int zMax = Math.min(maxZ, wallMaxZ + 1);
        if (xMin > xMax || zMin > zMax) {
            return;
        }

        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!(roofStairs.equals(name) || roofFill.equals(name) || roofSlab.equals(name) || name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_planks"))) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (x < xMin - 1 || x > xMax + 1 || z < zMin - 1 || z > zMax + 1 || y < roofBaseY || y > maxY) {
                continue;
            }
            if (name.endsWith("_planks") && y == roofBaseY) {
                continue;
            }
            removeBlock(blocksList, posToBlock, x, y, z);
        }

        Map<String, String> northProps = new HashMap<>(baseProps);
        northProps.put("facing", "north");
        northProps.put("shape", "straight");
        northProps.putIfAbsent("half", "bottom");
        northProps.putIfAbsent("waterlogged", "false");
        Map<String, String> southProps = new HashMap<>(baseProps);
        southProps.put("facing", "south");
        southProps.put("shape", "straight");
        southProps.putIfAbsent("half", "bottom");
        southProps.putIfAbsent("waterlogged", "false");
        Map<String, String> westProps = new HashMap<>(baseProps);
        westProps.put("facing", "west");
        westProps.put("shape", "straight");
        westProps.putIfAbsent("half", "bottom");
        westProps.putIfAbsent("waterlogged", "false");
        Map<String, String> eastProps = new HashMap<>(baseProps);
        eastProps.put("facing", "east");
        eastProps.put("shape", "straight");
        eastProps.putIfAbsent("half", "bottom");
        eastProps.putIfAbsent("waterlogged", "false");

        for (int level = 0; ; level++) {
            int y = roofBaseY + level;
            int xMinLevel = xMin + level;
            int xMaxLevel = xMax - level;
            int zMinLevel = zMin + level;
            int zMaxLevel = zMax - level;
            if (xMinLevel > xMaxLevel || zMinLevel > zMaxLevel || y > maxY) {
                break;
            }
            if (xMinLevel == xMaxLevel && zMinLevel == zMaxLevel) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        xMinLevel, y, zMinLevel, roofFill, Collections.emptyMap(), null);
                break;
            }
            for (int x = xMinLevel; x <= xMaxLevel; x++) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        x, y, zMinLevel, roofStairs, northProps, null);
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        x, y, zMaxLevel, roofStairs, southProps, null);
            }
            for (int z = zMinLevel; z <= zMaxLevel; z++) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        xMinLevel, y, z, roofStairs, westProps, null);
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        xMaxLevel, y, z, roofStairs, eastProps, null);
            }
            for (int x = xMinLevel + 1; x <= xMaxLevel - 1; x++) {
                for (int z = zMinLevel + 1; z <= zMaxLevel - 1; z++) {
                    setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            x, y, z, roofFill, Collections.emptyMap(), null);
                }
            }
        }
        fixRoofStairFacing(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                xMin, xMax, zMin, zMax, roofBaseY);
    }

    private static void redesignCanopyGarden(List<Object> blocksList,
                                             Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                             List<Object> paletteList,
                                             Map<String, Integer> paletteIndex,
                                             int maxY,
                                             int maxX,
                                             int maxZ) {
        int floorY = findFloorY(posToBlock, paletteList);
        if (floorY < 0 || floorY + 1 > maxY) {
            return;
        }

        int minFarmX = Integer.MAX_VALUE;
        int maxFarmX = Integer.MIN_VALUE;
        int minFarmZ = Integer.MAX_VALUE;
        int maxFarmZ = Integer.MIN_VALUE;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!"minecraft:farmland".equals(name)) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y != floorY) {
                continue;
            }
            minFarmX = Math.min(minFarmX, x);
            maxFarmX = Math.max(maxFarmX, x);
            minFarmZ = Math.min(minFarmZ, z);
            maxFarmZ = Math.max(maxFarmZ, z);
        }
        if (minFarmX == Integer.MAX_VALUE) {
            return;
        }

        Map<String, Integer> logCounts = new HashMap<>();
        Map<String, Integer> slabCounts = new HashMap<>();
        int maxSlabY = -1;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!name.endsWith("_log") && !name.endsWith("_slab")) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (x < minFarmX || x > maxFarmX || z < minFarmZ || z > maxFarmZ || y <= floorY) {
                continue;
            }
            if (name.endsWith("_log")) {
                logCounts.put(name, logCounts.getOrDefault(name, 0) + 1);
            } else {
                slabCounts.put(name, slabCounts.getOrDefault(name, 0) + 1);
                maxSlabY = Math.max(maxSlabY, y);
            }
        }
        if (logCounts.isEmpty() || slabCounts.isEmpty()) {
            return;
        }

        String logType = null;
        int bestLog = 0;
        for (Map.Entry<String, Integer> entry : logCounts.entrySet()) {
            if (entry.getValue() > bestLog) {
                logType = entry.getKey();
                bestLog = entry.getValue();
            }
        }
        String slabType = null;
        int bestSlab = 0;
        for (Map.Entry<String, Integer> entry : slabCounts.entrySet()) {
            if (entry.getValue() > bestSlab) {
                slabType = entry.getKey();
                bestSlab = entry.getValue();
            }
        }
        if (logType == null || slabType == null) {
            return;
        }

        int roofY = maxSlabY > 0 ? maxSlabY : floorY + 4;
        int postTop = Math.min(maxY, roofY - 1);
        int postBase = Math.min(maxY, floorY + 1);
        if (postBase > postTop) {
            return;
        }

        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!(name.endsWith("_log") || name.endsWith("_slab") || "minecraft:lantern".equals(name))) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (x < minFarmX || x > maxFarmX || z < minFarmZ || z > maxFarmZ || y <= floorY) {
                continue;
            }
            if (y <= roofY + 1) {
                removeBlock(blocksList, posToBlock, x, y, z);
            }
        }

        String fenceType = "minecraft:oak_fence";
        if (logType != null && logType.endsWith("_log")) {
            fenceType = logType.substring(0, logType.length() - "_log".length()) + "_fence";
        }
        int[][] corners = new int[][]{
                {minFarmX, minFarmZ},
                {minFarmX, maxFarmZ},
                {maxFarmX, minFarmZ},
                {maxFarmX, maxFarmZ}
        };
        for (int[] corner : corners) {
            for (int y = postBase; y <= postTop; y++) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        corner[0], y, corner[1], fenceType, Map.of("waterlogged", "false"), null);
            }
        }

        Map<String, String> slabProps = new HashMap<>();
        slabProps.put("type", "bottom");
        slabProps.put("waterlogged", "false");
        for (int x = minFarmX; x <= maxFarmX; x++) {
            for (int z = minFarmZ; z <= maxFarmZ; z++) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        x, roofY, z, slabType, slabProps, null);
            }
        }

        int centerX = (minFarmX + maxFarmX) / 2;
        int centerZ = (minFarmZ + maxFarmZ) / 2;
        int lanternY = Math.max(postBase + 1, roofY - 1);
        if (lanternY <= maxY && "minecraft:air".equals(stateAt(posToBlock, paletteList, centerX, lanternY, centerZ).name)) {
            setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    centerX, lanternY, centerZ, "minecraft:lantern", Map.of("hanging", "true", "waterlogged", "false"), null);
        }
    }

    private static void dedupeBlockType(List<Object> blocksList,
                                        Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                        List<Object> paletteList,
                                        String blockName,
                                        int keepCount,
                                        Set<String> preferred,
                                        double centerX,
                                        double centerZ) {
        List<int[]> positions = new ArrayList<>();
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!blockName.equals(name)) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            positions.add(new int[]{x, y, z});
        }
        if (positions.size() <= keepCount) {
            return;
        }
        positions.sort((a, b) -> {
            boolean aPref = preferred != null && preferred.contains(posKey(a[0], a[1], a[2]));
            boolean bPref = preferred != null && preferred.contains(posKey(b[0], b[1], b[2]));
            if (aPref != bPref) {
                return aPref ? -1 : 1;
            }
            double da = dist2(a[0], a[2], centerX, centerZ);
            double db = dist2(b[0], b[2], centerX, centerZ);
            return Double.compare(da, db);
        });
        for (int i = keepCount; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            removeBlock(blocksList, posToBlock, pos[0], pos[1], pos[2]);
        }
    }

    private static int[] findWallBounds(List<Object> blocksList, List<Object> paletteList, int floorY) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!isWallBlock(name)) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y < floorY + 1 || y > floorY + 6) {
                continue;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            maxY = Math.max(maxY, y);
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new int[]{minX, maxX, minZ, maxZ, maxY};
    }

    private static boolean isWallBlock(String name) {
        if (name.endsWith("_planks") || name.endsWith("_log")) {
            return true;
        }
        return Set.of(
                "minecraft:stone_bricks",
                "minecraft:mossy_stone_bricks",
                "minecraft:cobblestone",
                "minecraft:mossy_cobblestone",
                "minecraft:stone",
                "minecraft:bricks"
        ).contains(name);
    }

    private static void cleanupExteriorDecor(List<Object> blocksList,
                                             Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                             List<Object> paletteList,
                                             Set<String> interiorSet) {
        Set<String> targets = Set.of(
                "minecraft:barrel",
                "minecraft:chest",
                "minecraft:crafting_table",
                "minecraft:bookshelf",
                "minecraft:smoker",
                "minecraft:furnace",
                "minecraft:blast_furnace",
                "minecraft:lantern"
        );
        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!targets.contains(name) && !name.endsWith("_bed")) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (!interiorSet.contains(posKey(x, y, z))) {
                removeBlock(blocksList, posToBlock, x, y, z);
            }
        }
    }

    private static void fixRoofStairFacing(List<Object> blocksList,
                                           Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                           List<Object> paletteList,
                                           Map<String, Integer> paletteIndex,
                                           int maxY,
                                           int minX,
                                           int maxX,
                                           int minZ,
                                           int maxZ,
                                           int minY) {
        List<Object> snapshot = new ArrayList<>(blocksList);
        for (Object blockObj : snapshot) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!name.endsWith("_stairs")) {
                continue;
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (y < minY || x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }

            String desired = null;
            for (Map.Entry<String, int[]> dir : DIRS.entrySet()) {
                int[] delta = dir.getValue();
                State out = stateAt(posToBlock, paletteList, x + delta[0], y, z + delta[2]);
                State in = stateAt(posToBlock, paletteList, x - delta[0], y, z - delta[2]);
                if ("minecraft:air".equals(out.name) && !"minecraft:air".equals(in.name)) {
                    desired = dir.getKey();
                    break;
                }
            }
            if (desired == null) {
                if (z == minZ) {
                    desired = "north";
                } else if (z == maxZ) {
                    desired = "south";
                } else if (x == minX) {
                    desired = "west";
                } else if (x == maxX) {
                    desired = "east";
                }
            }
            if (desired == null) {
                continue;
            }

            Map<String, String> props = getProps(entry);
            String current = props.getOrDefault("facing", "north");
            if (!current.equals(desired)) {
                Map<String, String> newProps = new HashMap<>(props);
                newProps.put("facing", desired);
                newProps.put("shape", "straight");
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        x, y, z, name, newProps, null);
            }
        }
    }

    private static void updateConnections(List<Object> blocksList,
                                          Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                          List<Object> paletteList,
                                          Map<String, Integer> paletteIndex) {
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            int stateIdx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(stateIdx);
            String name = (String) entry.get("Name").value;
            Map<String, String> props = getProps(entry);

            boolean updated = false;
            if (name.endsWith("_pane") || "minecraft:iron_bars".equals(name)) {
                for (Map.Entry<String, int[]> dir : DIRS.entrySet()) {
                    int[] delta = dir.getValue();
                    State neighbor = stateAt(posToBlock, paletteList, x + delta[0], y + delta[1], z + delta[2]);
                    props.put(dir.getKey(), isConnectable(neighbor.name) ? "true" : "false");
                }
                updated = true;
            } else if (name.endsWith("_fence")) {
                for (Map.Entry<String, int[]> dir : DIRS.entrySet()) {
                    int[] delta = dir.getValue();
                    State neighbor = stateAt(posToBlock, paletteList, x + delta[0], y + delta[1], z + delta[2]);
                    props.put(dir.getKey(), isConnectable(neighbor.name) ? "true" : "false");
                }
                updated = true;
            } else if (name.endsWith("_stairs")) {
                String facing = props.getOrDefault("facing", "north");
                String half = props.getOrDefault("half", "bottom");
                String shape = "straight";

                int[] frontDelta = DIRS.get(facing);
                String frontFacing = stairFacingAt(posToBlock, paletteList, x + frontDelta[0], y, z + frontDelta[2], half);
                if (frontFacing != null && !axis(frontFacing).equals(axis(facing))
                        && canTakeShape(posToBlock, paletteList, x, y, z, facing, half, OPP.get(frontFacing))) {
                    shape = frontFacing.equals(LEFT.get(facing)) ? "outer_left" : "outer_right";
                } else {
                    int[] backDelta = DIRS.get(OPP.get(facing));
                    String backFacing = stairFacingAt(posToBlock, paletteList, x + backDelta[0], y, z + backDelta[2], half);
                    if (backFacing != null && !axis(backFacing).equals(axis(facing))
                            && canTakeShape(posToBlock, paletteList, x, y, z, facing, half, backFacing)) {
                        shape = backFacing.equals(LEFT.get(facing)) ? "inner_left" : "inner_right";
                    }
                }

                if (!shape.equals(props.getOrDefault("shape", "straight"))) {
                    props.put("shape", shape);
                    updated = true;
                }
            }

            if (updated) {
                block.get("state").value = getOrCreateState(paletteList, paletteIndex, name, props);
            }
        }
    }

    private static void applyHutVariantTweaks(List<Object> blocksList,
                                              Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                              List<Object> paletteList,
                                              Map<String, Integer> paletteIndex,
                                              int maxY,
                                              int maxX,
                                              int maxZ,
                                              int floorY,
                                              int variant) {
        DoorInfo door = findDoor(posToBlock, paletteList);
        if (door == null) {
            return;
        }

        int[] forward = DIRS.getOrDefault(door.facing, DIRS.get("north"));
        int[] right = DIRS.getOrDefault(RIGHT.getOrDefault(door.facing, "east"), DIRS.get("east"));
        int frontX = door.x + forward[0];
        int frontZ = door.z + forward[2];

        if (variant == 0) {
            for (int offset = -1; offset <= 1; offset++) {
                int px = frontX + right[0] * offset;
                int pz = frontZ + right[2] * offset;
                if (px < 0 || px > maxX || pz < 0 || pz > maxZ) {
                    continue;
                }
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        px, floorY, pz, "minecraft:oak_slab", Map.of("type", "bottom", "waterlogged", "false"), null);
                if (offset != 0) {
                    setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            px, floorY + 1, pz, "minecraft:oak_fence", Map.of("north", "false", "south", "false", "east", "false", "west", "false", "waterlogged", "false"), null);
                }
            }
            setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                    frontX, floorY + 2, frontZ, "minecraft:oak_slab", Map.of("type", "bottom", "waterlogged", "false"), null);
        } else if (variant == 1) {
            int cornerX = door.x < maxX / 2 ? maxX : 0;
            int cornerZ = door.z < maxZ / 2 ? maxZ : 0;
            for (int y = floorY + 1; y <= Math.min(maxY, floorY + 4); y++) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        cornerX, y, cornerZ, "minecraft:cobblestone", Collections.emptyMap(), null);
            }
        } else if (variant == 2) {
            int sideX = door.x + right[0] * 2;
            int sideZ = door.z + right[2] * 2;
            if (sideX >= 0 && sideX <= maxX && sideZ >= 0 && sideZ <= maxZ) {
                setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        sideX, floorY + 1, sideZ, "minecraft:glass_pane", Map.of("north", "false", "south", "false", "east", "false", "west", "false", "waterlogged", "false"), null);
            }
        } else if (variant == 3) {
            int[][] corners = new int[][]{
                    {0, 0},
                    {0, maxZ},
                    {maxX, 0},
                    {maxX, maxZ}
            };
            for (int[] corner : corners) {
                for (int y = floorY + 1; y <= Math.min(maxY, floorY + 3); y++) {
                    setBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            corner[0], y, corner[1], "minecraft:spruce_log", Map.of("axis", "y"), null);
                }
            }
        }
    }

    private static boolean addBlock(List<Object> blocksList,
                                    Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                    List<Object> paletteList,
                                    Map<String, Integer> paletteIndex,
                                    int maxY,
                                    int x,
                                    int y,
                                    int z,
                                    String name,
                                    Map<String, String> props) {
        String key = posKey(x, y, z);
        if (posToBlock.containsKey(key)) {
            return false;
        }
        if (y < 0 || y > maxY) {
            return false;
        }
        LinkedHashMap<String, Tag> block = new LinkedHashMap<>();
        block.put("pos", new Tag(TAG_LIST, new ListTag(TAG_INT, new ArrayList<>(Arrays.asList(x, y, z)))));
        block.put("state", new Tag(TAG_INT, getOrCreateState(paletteList, paletteIndex, name, props)));
        blocksList.add(block);
        posToBlock.put(key, block);
        return true;
    }

    private static void setBlock(List<Object> blocksList,
                                 Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                 List<Object> paletteList,
                                 Map<String, Integer> paletteIndex,
                                 int maxY,
                                 int x,
                                 int y,
                                 int z,
                                 String name,
                                 Map<String, String> props,
                                 LinkedHashMap<String, Tag> nbt) {
        if (y < 0 || y > maxY) {
            return;
        }
        String key = posKey(x, y, z);
        int state = getOrCreateState(paletteList, paletteIndex, name, props);
        LinkedHashMap<String, Tag> block = posToBlock.get(key);
        if (block == null) {
            block = new LinkedHashMap<>();
            block.put("pos", new Tag(TAG_LIST, new ListTag(TAG_INT, new ArrayList<>(Arrays.asList(x, y, z)))));
            block.put("state", new Tag(TAG_INT, state));
            if (nbt != null) {
                block.put("nbt", new Tag(TAG_COMPOUND, nbt));
            }
            blocksList.add(block);
            posToBlock.put(key, block);
            return;
        }
        block.put("state", new Tag(TAG_INT, state));
        if (nbt != null) {
            block.put("nbt", new Tag(TAG_COMPOUND, nbt));
        }
    }

    private static LinkedHashMap<String, Tag> lootBarrelNbt() {
        LinkedHashMap<String, Tag> nbt = new LinkedHashMap<>();
        nbt.put("id", new Tag(TAG_STRING, "minecraft:barrel"));
        nbt.put("LootTable", new Tag(TAG_STRING, LOOT_TABLE));
        nbt.put("LootTableSeed", new Tag(TAG_LONG, 0L));
        return nbt;
    }

    private static void ensureLootTableForBarrels(List<Object> blocksList, List<Object> paletteList) {
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            Tag stateTag = block.get("state");
            if (stateTag == null) {
                continue;
            }
            int idx = getInt(stateTag);
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!"minecraft:barrel".equals(name)) {
                continue;
            }
            LinkedHashMap<String, Tag> nbt;
            Tag nbtTag = block.get("nbt");
            if (nbtTag != null) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> existing = (LinkedHashMap<String, Tag>) nbtTag.value;
                nbt = existing;
            } else {
                nbt = new LinkedHashMap<>();
            }
            nbt.put("id", new Tag(TAG_STRING, "minecraft:barrel"));
            nbt.put("LootTable", new Tag(TAG_STRING, LOOT_TABLE));
            if (!nbt.containsKey("LootTableSeed")) {
                nbt.put("LootTableSeed", new Tag(TAG_LONG, 0L));
            }
            block.put("nbt", new Tag(TAG_COMPOUND, nbt));
        }
    }

    private static boolean isGroundBlock(String name) {
        if (isFloorBlock(name)) {
            return true;
        }
        return Set.of(
                "minecraft:grass_block",
                "minecraft:dirt",
                "minecraft:coarse_dirt",
                "minecraft:rooted_dirt",
                "minecraft:podzol",
                "minecraft:farmland",
                "minecraft:grass_path",
                "minecraft:dirt_path",
                "minecraft:gravel",
                "minecraft:sand"
        ).contains(name);
    }

    private static boolean isGardenSupportBlock(String name) {
        if (name.endsWith("_planks")) {
            return true;
        }
        return Set.of(
                "minecraft:grass_block",
                "minecraft:dirt",
                "minecraft:coarse_dirt",
                "minecraft:rooted_dirt",
                "minecraft:podzol",
                "minecraft:farmland",
                "minecraft:cobblestone",
                "minecraft:mossy_cobblestone",
                "minecraft:stone",
                "minecraft:stone_bricks",
                "minecraft:gravel",
                "minecraft:grass_path",
                "minecraft:dirt_path"
        ).contains(name);
    }

    private static DoorInfo findDoor(Map<String, LinkedHashMap<String, Tag>> posToBlock, List<Object> paletteList) {
        for (Map.Entry<String, LinkedHashMap<String, Tag>> entry : posToBlock.entrySet()) {
            int[] pos = parsePos(entry.getKey());
            State state = stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]);
            if (!state.name.endsWith("_door")) {
                continue;
            }
            String half = state.props.getOrDefault("half", "");
            if (!"lower".equals(half)) {
                continue;
            }
            String facing = state.props.getOrDefault("facing", "north");
            return new DoorInfo(pos[0], pos[1], pos[2], facing);
        }
        return null;
    }

    private static String stairFacingAt(Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                        List<Object> paletteList,
                                        int x,
                                        int y,
                                        int z,
                                        String half) {
        State neighbor = stateAt(posToBlock, paletteList, x, y, z);
        if (!neighbor.name.endsWith("_stairs")) {
            return null;
        }
        if (!half.equals(neighbor.props.getOrDefault("half", "bottom"))) {
            return null;
        }
        return neighbor.props.getOrDefault("facing", "north");
    }

    private static boolean canTakeShape(Map<String, LinkedHashMap<String, Tag>> posToBlock,
                                        List<Object> paletteList,
                                        int x,
                                        int y,
                                        int z,
                                        String facing,
                                        String half,
                                        String checkDir) {
        int[] delta = DIRS.get(checkDir);
        State neighbor = stateAt(posToBlock, paletteList, x + delta[0], y, z + delta[2]);
        if (!neighbor.name.endsWith("_stairs")) {
            return true;
        }
        if (!facing.equals(neighbor.props.getOrDefault("facing", "north"))) {
            return true;
        }
        return !half.equals(neighbor.props.getOrDefault("half", "bottom"));
    }

    private static void shrinkGarden(LinkedHashMap<String, Tag> root, int targetX, int targetZ) {
        Tag sizeTag = root.get("size");
        if (sizeTag == null) {
            return;
        }
        ListTag sizeList = (ListTag) sizeTag.value;
        int sizeX = ((Number) sizeList.items.get(0)).intValue();
        int sizeY = ((Number) sizeList.items.get(1)).intValue();
        int sizeZ = ((Number) sizeList.items.get(2)).intValue();

        int newX = Math.min(sizeX, targetX);
        int newZ = Math.min(sizeZ, targetZ);
        if (newX < 1 || newZ < 1 || (newX == sizeX && newZ == sizeZ)) {
            return;
        }

        int startX = Math.max(0, (sizeX - newX) / 2);
        int startZ = Math.max(0, (sizeZ - newZ) / 2);
        int endX = startX + newX;
        int endZ = startZ + newZ;

        Tag blocksTag = root.get("blocks");
        if (blocksTag == null) {
            return;
        }
        ListTag blocksList = (ListTag) blocksTag.value;
        List<Object> newBlocks = new ArrayList<>();
        for (Object blockObj : blocksList.items) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            if (x < startX || x >= endX || z < startZ || z >= endZ) {
                continue;
            }
            posList.items.set(0, x - startX);
            posList.items.set(1, y);
            posList.items.set(2, z - startZ);
            newBlocks.add(block);
        }
        blocksList.items = newBlocks;
        sizeList.items = Arrays.asList(newX, sizeY, newZ);

        Tag entitiesTag = root.get("entities");
        if (entitiesTag != null) {
            ListTag entities = (ListTag) entitiesTag.value;
            List<Object> newEntities = new ArrayList<>();
            for (Object entObj : entities.items) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> ent = (LinkedHashMap<String, Tag>) entObj;
                Tag blockPosTag = ent.get("blockPos");
                if (blockPosTag != null) {
                    ListTag bpos = (ListTag) blockPosTag.value;
                    int bx = ((Number) bpos.items.get(0)).intValue();
                    int by = ((Number) bpos.items.get(1)).intValue();
                    int bz = ((Number) bpos.items.get(2)).intValue();
                    if (bx < startX || bx >= endX || bz < startZ || bz >= endZ) {
                        continue;
                    }
                    bpos.items.set(0, bx - startX);
                    bpos.items.set(1, by);
                    bpos.items.set(2, bz - startZ);
                }
                Tag posTag = ent.get("pos");
                if (posTag != null) {
                    ListTag pos = (ListTag) posTag.value;
                    double px = ((Number) pos.items.get(0)).doubleValue();
                    double py = ((Number) pos.items.get(1)).doubleValue();
                    double pz = ((Number) pos.items.get(2)).doubleValue();
                    pos.items.set(0, px - startX);
                    pos.items.set(1, py);
                    pos.items.set(2, pz - startZ);
                }
                newEntities.add(ent);
            }
            entities.items = newEntities;
        }
    }

    private static void fixStructure(LinkedHashMap<String, Tag> root, boolean isHut, int hutVariant, boolean isGarden) {
        Tag paletteTag = root.get("palette");
        Tag blocksTag = root.get("blocks");
        Tag sizeTag = root.get("size");
        if (paletteTag == null || blocksTag == null || sizeTag == null) {
            return;
        }

        ListTag paletteListTag = (ListTag) paletteTag.value;
        List<Object> paletteList = paletteListTag.items;
        Map<String, Integer> paletteIndex = new HashMap<>();
        for (int i = 0; i < paletteList.size(); i++) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(i);
            String name = (String) entry.get("Name").value;
            Map<String, String> props = getProps(entry);
            paletteIndex.put(stateKey(name, props), i);
        }

        ListTag blocksListTag = (ListTag) blocksTag.value;
        List<Object> blocksList = blocksListTag.items;
        Map<String, LinkedHashMap<String, Tag>> posToBlock = new HashMap<>();
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            posToBlock.put(posKey(x, y, z), block);
        }

        if (!isHut && !isGarden) {
            return;
        }

        ListTag sizeList = (ListTag) sizeTag.value;
        int maxX = ((Number) sizeList.items.get(0)).intValue() - 1;
        int maxY = ((Number) sizeList.items.get(1)).intValue() - 1;
        int maxZ = ((Number) sizeList.items.get(2)).intValue() - 1;

        int floorY = -1;
        if (isHut) {
            Map<Integer, Integer> floorCounts = new HashMap<>();
            for (Object blockObj : blocksList) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
                ListTag posList = (ListTag) block.get("pos").value;
                int y = ((Number) posList.items.get(1)).intValue();
                int idx = getInt(block.get("state"));
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
                String name = (String) entry.get("Name").value;
                if (isFloorBlock(name)) {
                    floorCounts.put(y, floorCounts.getOrDefault(y, 0) + 1);
                }
            }

            if (floorCounts.isEmpty()) {
                return;
            }

            int bestCount = 0;
            for (Map.Entry<Integer, Integer> entry : floorCounts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    floorY = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            if (floorY < 0 || floorY + 1 > maxY) {
                return;
            }
        } else if (isGarden) {
            floorY = findFloorY(posToBlock, paletteList);
            if (floorY < 0 || floorY + 1 > maxY) {
                floorY = -1;
            }
        }

        if (isHut && floorY >= 0) {
            List<int[]> interior = new ArrayList<>();
            Set<String> interiorSet = new HashSet<>();
            for (int x = 0; x <= maxX; x++) {
                for (int z = 0; z <= maxZ; z++) {
                    State below = stateAt(posToBlock, paletteList, x, floorY, z);
                    State here = stateAt(posToBlock, paletteList, x, floorY + 1, z);
                    if (isFloorBlock(below.name) && "minecraft:air".equals(here.name)) {
                        interior.add(new int[]{x, floorY + 1, z});
                        interiorSet.add(posKey(x, floorY + 1, z));
                    }
                }
            }

            int[] wallBounds = findWallBounds(blocksList, paletteList, floorY);
            if (wallBounds != null) {
                int wallMinX = wallBounds[0];
                int wallMaxX = wallBounds[1];
                int wallMinZ = wallBounds[2];
                int wallMaxZ = wallBounds[3];
                int innerMinX = wallMinX + 1;
                int innerMaxX = wallMaxX - 1;
                int innerMinZ = wallMinZ + 1;
                int innerMaxZ = wallMaxZ - 1;
                List<int[]> filtered = new ArrayList<>();
                Set<String> filteredSet = new HashSet<>();
                for (int[] pos : interior) {
                    if (pos[0] < innerMinX || pos[0] > innerMaxX || pos[2] < innerMinZ || pos[2] > innerMaxZ) {
                        continue;
                    }
                    filtered.add(pos);
                    filteredSet.add(posKey(pos[0], pos[1], pos[2]));
                }
                interior = filtered;
                interiorSet = filteredSet;
            }

            if (interior.isEmpty()) {
                return;
            }

            int minX = Integer.MAX_VALUE;
            int maxXInterior = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZInterior = Integer.MIN_VALUE;
            for (int[] pos : interior) {
                minX = Math.min(minX, pos[0]);
                maxXInterior = Math.max(maxXInterior, pos[0]);
                minZ = Math.min(minZ, pos[2]);
                maxZInterior = Math.max(maxZInterior, pos[2]);
            }
            double centerX = (minX + maxXInterior) / 2.0;
            double centerZ = (minZ + maxZInterior) / 2.0;

            List<int[]> interiorAsc = new ArrayList<>(interior);
            interiorAsc.sort(Comparator.comparingDouble(p -> dist2(p[0], p[2], centerX, centerZ)));
            List<int[]> interiorDesc = new ArrayList<>(interiorAsc);
            Collections.reverse(interiorDesc);

            Set<String> occupied = new HashSet<>();
            Set<String> existingBlocks = new HashSet<>();
            for (Object blockObj : blocksList) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
                int idx = getInt(block.get("state"));
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
                existingBlocks.add((String) entry.get("Name").value);
            }
            boolean hasContainer = existingBlocks.contains("minecraft:barrel") || existingBlocks.contains("minecraft:chest");
            boolean hasCraftingTable = existingBlocks.contains("minecraft:crafting_table");
            boolean hasLantern = existingBlocks.contains("minecraft:lantern");
            boolean hasBookshelf = existingBlocks.contains("minecraft:bookshelf");
            boolean hasSmoker = existingBlocks.contains("minecraft:smoker");
            boolean hasPottedFern = existingBlocks.contains("minecraft:potted_fern");

            int[] bedFoot = null;
            int[] bedHead = null;
            String bedFacing = null;
            for (int[] pos : interiorDesc) {
                if (!isInteriorCandidate(posToBlock, paletteList, pos[0], pos[1], pos[2])) {
                    continue;
                }
                String footKey = posKey(pos[0], pos[1], pos[2]);
                if (occupied.contains(footKey)) {
                    continue;
                }
                for (String facing : DIRS.keySet()) {
                    int[] delta = DIRS.get(facing);
                    int hx = pos[0] + delta[0];
                    int hy = pos[1];
                    int hz = pos[2] + delta[2];
                    String headKey = posKey(hx, hy, hz);
                    if (!interiorSet.contains(headKey)) {
                        continue;
                    }
                    if (occupied.contains(headKey)) {
                        continue;
                    }
                    if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]).name)) {
                        continue;
                    }
                    if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, hx, hy, hz).name)) {
                        continue;
                    }
                    int bx = hx + delta[0];
                    int bz = hz + delta[2];
                    if ("minecraft:air".equals(stateAt(posToBlock, paletteList, bx, hy, bz).name)) {
                        continue;
                    }
                    bedFoot = pos;
                    bedHead = new int[]{hx, hy, hz};
                    bedFacing = facing;
                    break;
                }
                if (bedFoot != null) {
                    break;
                }
            }

            if (bedFoot != null && bedHead != null && bedFacing != null) {
                addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        bedFoot[0], bedFoot[1], bedFoot[2],
                        "minecraft:blue_bed", Map.of("facing", bedFacing, "part", "foot", "occupied", "false"));
                addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        bedHead[0], bedHead[1], bedHead[2],
                        "minecraft:blue_bed", Map.of("facing", bedFacing, "part", "head", "occupied", "false"));
                occupied.add(posKey(bedFoot[0], bedFoot[1], bedFoot[2]));
                occupied.add(posKey(bedHead[0], bedHead[1], bedHead[2]));
            }

            if (!hasCraftingTable) {
                for (int[] pos : interiorDesc) {
                    String key = posKey(pos[0], pos[1], pos[2]);
                    if (occupied.contains(key)) {
                        continue;
                    }
                    if (!isInteriorCandidate(posToBlock, paletteList, pos[0], pos[1], pos[2])) {
                        continue;
                    }
                    if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]).name)) {
                        continue;
                    }
                    boolean hasNeighbor = false;
                    for (int[] delta : DIRS.values()) {
                        State neighbor = stateAt(posToBlock, paletteList, pos[0] + delta[0], pos[1], pos[2] + delta[2]);
                        if (!"minecraft:air".equals(neighbor.name)) {
                            hasNeighbor = true;
                            break;
                        }
                    }
                    if (hasNeighbor && addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            pos[0], pos[1], pos[2], "minecraft:crafting_table", Collections.emptyMap())) {
                        occupied.add(key);
                        break;
                    }
                }
            }

            if (!hasContainer) {
                for (int[] pos : interiorAsc) {
                    String key = posKey(pos[0], pos[1], pos[2]);
                    if (occupied.contains(key)) {
                        continue;
                    }
                    if (!isInteriorCandidate(posToBlock, paletteList, pos[0], pos[1], pos[2])) {
                        continue;
                    }
                    if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, pos[0], pos[1], pos[2]).name)) {
                        continue;
                    }
                    String facing = "north";
                    for (Map.Entry<String, int[]> dir : DIRS.entrySet()) {
                        int[] delta = dir.getValue();
                        State neighbor = stateAt(posToBlock, paletteList, pos[0] + delta[0], pos[1], pos[2] + delta[2]);
                        if (!"minecraft:air".equals(neighbor.name)) {
                            facing = OPP.get(dir.getKey());
                            break;
                        }
                    }
                    if (addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            pos[0], pos[1], pos[2], "minecraft:barrel", Map.of("facing", facing, "open", "false"))) {
                        occupied.add(key);
                        break;
                    }
                }
            }

            if (!hasLantern) {
                boolean lanternPlaced = false;
                for (int[] pos : interiorAsc) {
                    int x = pos[0];
                    int z = pos[2];
                    for (int y = floorY + 2; y <= maxY; y++) {
                        if (y + 1 > maxY) {
                            continue;
                        }
                        String key = posKey(x, y, z);
                        if (occupied.contains(key)) {
                            continue;
                        }
                        if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, x, y, z).name)) {
                            continue;
                        }
                        if ("minecraft:air".equals(stateAt(posToBlock, paletteList, x, y + 1, z).name)) {
                            continue;
                        }
                        if (addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                                x, y, z, "minecraft:lantern", Map.of("hanging", "true", "waterlogged", "false"))) {
                            occupied.add(key);
                            lanternPlaced = true;
                            break;
                        }
                    }
                    if (lanternPlaced) {
                        break;
                    }
                }
            }

            for (int[] pos : interiorAsc) {
                int x = pos[0];
                int y = pos[1];
                int z = pos[2];
                int[][] carpetPositions = new int[][]{
                        {x, y, z},
                        {x + 1, y, z},
                        {x, y, z + 1},
                        {x + 1, y, z + 1}
                };
                boolean valid = true;
                for (int[] cp : carpetPositions) {
                    String key = posKey(cp[0], cp[1], cp[2]);
                    if (!interiorSet.contains(key) || occupied.contains(key)) {
                        valid = false;
                        break;
                    }
                    if (!"minecraft:air".equals(stateAt(posToBlock, paletteList, cp[0], cp[1], cp[2]).name)) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) {
                    continue;
                }
                for (int[] cp : carpetPositions) {
                    if (addBlock(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                            cp[0], cp[1], cp[2], "minecraft:gray_carpet", Collections.emptyMap())) {
                        occupied.add(posKey(cp[0], cp[1], cp[2]));
                    }
                }
                break;
            }

            if (!hasBookshelf) {
                placeInteriorAgainstWall(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        interiorAsc, occupied, "minecraft:bookshelf", Collections.emptyMap());
            }
            if (!hasSmoker) {
                placeFacingBlockAgainstWall(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        interiorAsc, occupied, "minecraft:smoker");
            }
            if (!hasPottedFern) {
                placePottedPlantAbove(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        "minecraft:barrel", "minecraft:potted_fern");
                placePottedPlantAbove(blocksList, posToBlock, paletteList, paletteIndex, maxY,
                        "minecraft:crafting_table", "minecraft:potted_fern");
            }

            applyHutVariantTweaks(blocksList, posToBlock, paletteList, paletteIndex, maxY, maxX, maxZ, floorY, hutVariant);
            fixRoofPyramid(blocksList, posToBlock, paletteList, paletteIndex, maxY, maxX, maxZ, floorY);
            dedupeBlockType(blocksList, posToBlock, paletteList, "minecraft:lantern", 1, interiorSet, centerX, centerZ);
            dedupeBlockType(blocksList, posToBlock, paletteList, "minecraft:crafting_table", 1, interiorSet, centerX, centerZ);
            cleanupExteriorDecor(blocksList, posToBlock, paletteList, interiorSet);
        }

        if (isGarden) {
            placeGardenBarrel(blocksList, posToBlock, paletteList, paletteIndex, maxY, maxX, maxZ);
            addGardenFences(blocksList, posToBlock, paletteList, paletteIndex, maxY, maxX, maxZ);
            redesignCanopyGarden(blocksList, posToBlock, paletteList, paletteIndex, maxY, maxX, maxZ);
        }

        updateConnections(blocksList, posToBlock, paletteList, paletteIndex);
        ensureLootTableForBarrels(blocksList, paletteList);
    }

    private static void dumpRoof(Path path, LinkedHashMap<String, Tag> root) {
        Tag paletteTag = root.get("palette");
        Tag blocksTag = root.get("blocks");
        if (paletteTag == null || blocksTag == null) {
            return;
        }
        ListTag paletteListTag = (ListTag) paletteTag.value;
        List<Object> paletteList = paletteListTag.items;
        ListTag blocksListTag = (ListTag) blocksTag.value;
        List<Object> blocksList = blocksListTag.items;

        Map<Integer, List<int[]>> stairsByY = new HashMap<>();
        String sampleStair = null;
        Map<String, String> sampleProps = null;
        for (Object blockObj : blocksList) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> block = (LinkedHashMap<String, Tag>) blockObj;
            int idx = getInt(block.get("state"));
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> entry = (LinkedHashMap<String, Tag>) paletteList.get(idx);
            String name = (String) entry.get("Name").value;
            if (!name.endsWith("_stairs")) {
                continue;
            }
            if (sampleStair == null) {
                sampleStair = name;
                sampleProps = getProps(entry);
            }
            ListTag posList = (ListTag) block.get("pos").value;
            int x = ((Number) posList.items.get(0)).intValue();
            int y = ((Number) posList.items.get(1)).intValue();
            int z = ((Number) posList.items.get(2)).intValue();
            stairsByY.computeIfAbsent(y, k -> new ArrayList<>()).add(new int[]{x, y, z});
        }
        if (stairsByY.isEmpty()) {
            return;
        }
        List<Integer> ys = new ArrayList<>(stairsByY.keySet());
        ys.sort(Integer::compareTo);
        int topY = ys.get(ys.size() - 1);
        int penY = ys.size() >= 2 ? ys.get(ys.size() - 2) : topY - 1;
        System.out.println("Roof dump: " + path.getFileName());
        System.out.println("  topY=" + topY + " penultimateY=" + penY);
        if (sampleStair != null) {
            System.out.println("  sampleStair=" + sampleStair + " props=" + sampleProps);
        }
        for (int y : ys) {
            List<int[]> positions = stairsByY.get(y);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int[] pos : positions) {
                minX = Math.min(minX, pos[0]);
                maxX = Math.max(maxX, pos[0]);
                minZ = Math.min(minZ, pos[2]);
                maxZ = Math.max(maxZ, pos[2]);
            }
            System.out.println("  y=" + y + " count=" + positions.size()
                    + " bounds=(" + minX + "," + minZ + ")-(" + maxX + "," + maxZ + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: StructureEdit <structure files>");
            return;
        }
        if (args.length > 1 && "--dump".equals(args[0])) {
            for (int i = 1; i < args.length; i++) {
                Path path = Path.of(args[i]);
                NbtRoot root = readNbt(path);
                if (root == null || root.rootTag.type != TAG_COMPOUND) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, Tag> rootMap = (LinkedHashMap<String, Tag>) root.rootTag.value;
                dumpRoof(path, rootMap);
            }
            return;
        }
        for (String file : args) {
            Path path = Path.of(file);
            NbtRoot root = readNbt(path);
            if (root == null || root.rootTag.type != TAG_COMPOUND) {
                continue;
            }
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Tag> rootMap = (LinkedHashMap<String, Tag>) root.rootTag.value;
            String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean isHut = lower.contains("abandoned_hut");
            boolean isGarden = lower.contains("abandoned_garden");
            int hutVariant = 0;
            if (lower.contains("abandoned_hut_1")) {
                hutVariant = 1;
            } else if (lower.contains("abandoned_hut_2")) {
                hutVariant = 2;
            } else if (lower.contains("abandoned_hut_3")) {
                hutVariant = 3;
            }
            if (isGarden) {
                shrinkGarden(rootMap, 5, 5);
            }
            fixStructure(rootMap, isHut, hutVariant, isGarden);
            writeNbt(path, root);
        }
    }
}
