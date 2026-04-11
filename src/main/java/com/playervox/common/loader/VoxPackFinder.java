package com.playervox.common.loader;

import com.playervox.PlayerVoxMod;
import com.google.gson.Gson;
import cpw.mods.jarhandling.SecureJar;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.resource.DelegatingPackResources;
import net.minecraftforge.resource.PathPackResources;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 扫描 vox_packs/ 目录，将所有语音包通过 DelegatingPackResources 合并为
 * 单个虚拟资源包 "playervox_resources"，不污染原版资源包/数据包列表。
 *
 * 每个语音包需包含 pack_meta.json 声明 id/name/description。
 * 语音包内部结构：
 *   assets/<id>/vox_sounds/xxx.ogg   （音频文件）
 *   data/<id>/vox/triggers/xxx.json  （触发器定义）
 */
public class VoxPackFinder implements RepositorySource {

    private static final Gson GSON = new Gson();

    private final PackType packType;

    /** 已加载的语音包元信息列表（id → meta），供 GUI / TriggerRegistry 查询 */
    private static final Map<String, VoxPackMeta> loadedPacks = new LinkedHashMap<>();

    public VoxPackFinder(PackType packType) {
        this.packType = packType;
    }

    /** 获取所有已加载的语音包 ID 列表 */
    public static List<String> getLoadedPackIds() {
        return new ArrayList<>(loadedPacks.keySet());
    }

    /** 获取指定语音包的元信息 */
    public static VoxPackMeta getPackMeta(String packId) {
        return loadedPacks.get(packId);
    }

    /** 获取所有已加载的语音包元信息 */
    public static Collection<VoxPackMeta> getAllPackMetas() {
        return Collections.unmodifiableCollection(loadedPacks.values());
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        Path voxPacksDir = FMLPaths.GAMEDIR.get().resolve("vox_packs");

        if (!Files.exists(voxPacksDir)) {
            try {
                Files.createDirectories(voxPacksDir);
            } catch (Exception e) {
                PlayerVoxMod.LOGGER.error("PlayerVOX: 无法创建 vox_packs 目录: {}", e.getMessage());
            }
            return;
        }

        // 扫描所有语音包
        List<VoxPack> voxPacks = scanPacks(voxPacksDir);
        if (voxPacks.isEmpty()) return;

        // 仅在 SERVER_DATA 扫描时更新 loadedPacks（避免重复）
        if (packType == PackType.SERVER_DATA) {
            loadedPacks.clear();
            for (VoxPack vp : voxPacks) {
                loadedPacks.put(vp.meta.getId(), vp.meta);
            }
        }

        // 构建 PathPackResources 列表
        List<PathPackResources> extensionPacks = new ArrayList<>();
        for (VoxPack vp : voxPacks) {
            PathPackResources packResources = new PathPackResources(
                    vp.meta.getId(), false, vp.path
            ) {
                private final SecureJar secureJar = SecureJar.from(vp.path);

                @Override
                protected Path resolve(String... paths) {
                    if (paths.length < 1) {
                        throw new IllegalArgumentException("Missing path");
                    }
                    return this.secureJar.getPath(String.join("/", paths));
                }

                @Override
                public IoSupplier<InputStream> getResource(PackType type, net.minecraft.resources.ResourceLocation location) {
                    return super.getResource(type, location);
                }

                @Override
                public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
                    super.listResources(type, namespace, path, resourceOutput);
                }
            };
            extensionPacks.add(packResources);
        }

        // 合并为单个虚拟包
        Pack pack = Pack.readMetaAndCreate(
                "playervox_resources",
                Component.literal("ClaudeVOX Resources"),
                true,
                id -> new DelegatingPackResources(
                        id, false,
                        new PackMetadataSection(
                                Component.literal("ClaudeVOX voice packs"),
                                SharedConstants.getCurrentVersion().getPackVersion(packType)
                        ),
                        extensionPacks
                ),
                packType,
                Pack.Position.BOTTOM,
                PackSource.BUILT_IN
        );

        if (pack != null) {
            consumer.accept(pack);
            PlayerVoxMod.LOGGER.info("PlayerVOX: 已加载 {} 个语音包为 {} (合并虚拟包)",
                    voxPacks.size(), packType.name());
        }
    }

    // -------------------------------------------------------------------------

    private static List<VoxPack> scanPacks(Path dir) {
        List<VoxPack> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                VoxPack vp = null;
                if (Files.isDirectory(entry)) {
                    vp = fromDir(entry);
                } else if (entry.toString().endsWith(".zip")) {
                    vp = fromZip(entry);
                }
                if (vp != null) {
                    PlayerVoxMod.LOGGER.info("PlayerVOX: 发现语音包 [{}] id={}", entry.getFileName(), vp.meta.getId());
                    result.add(vp);
                }
            }
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.error("PlayerVOX: 扫描 vox_packs 失败: {}", e.getMessage());
        }
        return result;
    }

    private static VoxPack fromDir(Path path) {
        Path metaFile = path.resolve("pack_meta.json");
        if (!Files.exists(metaFile)) return null;
        try (InputStream in = Files.newInputStream(metaFile)) {
            VoxPackMeta meta = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), VoxPackMeta.class);
            if (meta == null || meta.getId() == null || meta.getId().isEmpty()) {
                PlayerVoxMod.LOGGER.warn("PlayerVOX: 语音包 [{}] pack_meta.json 缺少 id 字段", path.getFileName());
                return null;
            }
            if (!validatePackId(meta.getId(), path.getFileName().toString())) return null;
            return new VoxPack(path, meta);
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.warn("PlayerVOX: 读取语音包 [{}] 元信息失败: {}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    private static VoxPack fromZip(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry("pack_meta.json");
            if (entry == null) {
                PlayerVoxMod.LOGGER.warn("PlayerVOX: 语音包 [{}] 缺少 pack_meta.json", path.getFileName());
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                VoxPackMeta meta = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), VoxPackMeta.class);
                if (meta == null || meta.getId() == null || meta.getId().isEmpty()) {
                    PlayerVoxMod.LOGGER.warn("PlayerVOX: 语音包 [{}] pack_meta.json 缺少 id 字段", path.getFileName());
                    return null;
                }
                if (!validatePackId(meta.getId(), path.getFileName().toString())) return null;
                return new VoxPack(path, meta);
            }
        } catch (Exception e) {
            PlayerVoxMod.LOGGER.warn("PlayerVOX: 读取语音包 [{}] 失败: {}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    /** 验证 pack id 是否符合 ResourceLocation namespace 规范：仅允许 [a-z0-9_.-] */
    private static boolean validatePackId(String id, String fileName) {
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c != '_' && c != '-' && c != '.' && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) {
                PlayerVoxMod.LOGGER.error(
                        "PlayerVOX: 语音包 [{}] 的 id \"{}\" 包含非法字符 '{}'。" +
                        "id 仅允许小写字母、数字、下划线、连字符和点号 [a-z0-9_.-]",
                        fileName, id, c);
                return false;
            }
        }
        return true;
    }

    private record VoxPack(Path path, VoxPackMeta meta) {}
}
