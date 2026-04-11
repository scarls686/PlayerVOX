package com.playervox.client;

import com.playervox.PlayerVoxMod;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * 客户端资源重载监听器：扫描 assets/namespace/vox_sounds/ 下的 OGG 文件。
 * 采用懒加载 + LRU 缓存策略：资源重载时只收集文件索引，播放时按需解码。
 */
@OnlyIn(Dist.CLIENT)
public class VoxSoundAssetsManager extends SimplePreparableReloadListener<Map<ResourceLocation, Resource>> {

    public record SoundData(ByteBuffer byteBuffer, AudioFormat audioFormat) {}

    public static final VoxSoundAssetsManager INSTANCE = new VoxSoundAssetsManager();

    /** 最大缓存条目数 */
    private static final int MAX_CACHE_SIZE = 64;

    /** 资源索引：soundId → Resource（用于按需加载） */
    private final Map<ResourceLocation, Resource> resourceIndex = new HashMap<>();

    /** LRU 解码缓存：soundId → 解码后的 PCM 数据 */
    private final LinkedHashMap<ResourceLocation, SoundData> cache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, SoundData> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };

    private final FileToIdConverter fileToIdConverter = new FileToIdConverter("vox_sounds", ".ogg");

    private VoxSoundAssetsManager() {}

    @Override
    @NotNull
    protected Map<ResourceLocation, Resource> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> index = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : fileToIdConverter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation filePath = entry.getKey();
            ResourceLocation soundId = fileToIdConverter.fileToId(filePath);
            index.put(soundId, entry.getValue());
        }
        return index;
    }

    @Override
    protected void apply(Map<ResourceLocation, Resource> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        resourceIndex.clear();
        cache.clear();
        resourceIndex.putAll(prepared);
        PlayerVoxMod.LOGGER.info("PlayerVOX: 客户端已索引 {} 个音频文件（懒加载）", resourceIndex.size());
    }

    /** 根据 ResourceLocation 获取音频数据，首次访问时解码并缓存 */
    @Nullable
    public SoundData getSoundData(ResourceLocation id) {
        // 先查缓存
        SoundData cached = cache.get(id);
        if (cached != null) return cached;

        // 缓存未命中，按需解码
        Resource resource = resourceIndex.get(id);
        if (resource == null) return null;

        try (InputStream stream = resource.open();
             OggAudioStream audioStream = new OggAudioStream(stream)) {
            ByteBuffer buffer = audioStream.readAll();
            AudioFormat format = audioStream.getFormat();

            // 立体声转单声道
            if (format.getChannels() > 1) {
                buffer = stereoToMono(buffer, format);
                format = new AudioFormat(
                        format.getEncoding(), format.getSampleRate(),
                        format.getSampleSizeInBits(), 1,
                        format.getSampleSizeInBits() / 8,
                        format.getSampleRate(), format.isBigEndian()
                );
            }

            SoundData data = new SoundData(buffer, format);
            cache.put(id, data);
            return data;
        } catch (IOException e) {
            PlayerVoxMod.LOGGER.warn("PlayerVOX: 解码音频失败: {}", id);
            return null;
        }
    }

    /**
     * 将立体声 PCM 数据合并为单声道（左右声道取平均值）。
     */
    private static ByteBuffer stereoToMono(ByteBuffer stereoBuffer, AudioFormat format) {
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        int channels = format.getChannels();
        int frameSize = bytesPerSample * channels;
        int frameCount = stereoBuffer.remaining() / frameSize;

        ByteBuffer monoBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerSample);
        monoBuffer.order(stereoBuffer.order());

        if (bytesPerSample == 2) {
            for (int i = 0; i < frameCount; i++) {
                int offset = i * frameSize;
                short left = stereoBuffer.getShort(stereoBuffer.position() + offset);
                short right = stereoBuffer.getShort(stereoBuffer.position() + offset + 2);
                short mono = (short) ((left + right) / 2);
                monoBuffer.putShort(mono);
            }
        } else {
            for (int i = 0; i < frameCount; i++) {
                int offset = i * frameSize;
                byte left = stereoBuffer.get(stereoBuffer.position() + offset);
                byte right = stereoBuffer.get(stereoBuffer.position() + offset + 1);
                byte mono = (byte) ((left + right) / 2);
                monoBuffer.put(mono);
            }
        }

        monoBuffer.flip();
        return monoBuffer;
    }
}
