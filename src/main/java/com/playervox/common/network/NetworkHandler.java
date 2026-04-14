package com.playervox.common.network;

import com.playervox.PlayerVoxMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "3";
    public static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int nextId() { return packetId++; }

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(PlayerVoxMod.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        INSTANCE.registerMessage(nextId(),
                PacketPlaySound.class, PacketPlaySound::encode, PacketPlaySound::decode, PacketPlaySound::handle);

        INSTANCE.registerMessage(nextId(),
                PacketSelectVoxPack.class, PacketSelectVoxPack::encode, PacketSelectVoxPack::decode, PacketSelectVoxPack::handle);

        INSTANCE.registerMessage(nextId(),
                PacketSyncVoxSelection.class, PacketSyncVoxSelection::encode, PacketSyncVoxSelection::decode, PacketSyncVoxSelection::handle);

        INSTANCE.registerMessage(nextId(),
                PacketSyncVoxPackList.class, PacketSyncVoxPackList::encode, PacketSyncVoxPackList::decode, PacketSyncVoxPackList::handle);
    }
}
