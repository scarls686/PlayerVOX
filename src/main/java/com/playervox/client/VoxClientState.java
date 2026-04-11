package com.playervox.client;

import com.playervox.common.loader.VoxPackMeta;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端本地状态：当前选择的语音包、服务端可用的语音包列表。
 */
@OnlyIn(Dist.CLIENT)
public class VoxClientState {

    /** 当前选择的语音包 ID（空字符串 = 未选择） */
    private static String selectedPackId = "";

    /** 服务端同步过来的可用语音包列表 */
    private static List<VoxPackMeta> availablePacks = Collections.emptyList();

    public static String getSelectedPackId() {
        return selectedPackId;
    }

    public static void setSelectedPack(String packId) {
        selectedPackId = packId != null ? packId : "";
    }

    public static List<VoxPackMeta> getAvailablePacks() {
        return availablePacks;
    }

    public static void setAvailablePacks(List<VoxPackMeta> packs) {
        availablePacks = Collections.unmodifiableList(new ArrayList<>(packs));
    }

    public static void reset() {
        selectedPackId = "";
        availablePacks = Collections.emptyList();
    }
}
