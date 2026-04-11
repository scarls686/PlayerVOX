package com.playervox.common.loader;

import com.google.gson.annotations.SerializedName;

/**
 * pack_meta.json 对应的数据类。
 * {
 *   "id": "my_voice",
 *   "name": "我的语音包",
 *   "description": "描述",
 *   "icon": "icon.png"
 * }
 */
public class VoxPackMeta {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description = "";

    @SerializedName("icon")
    private String icon = "";

    public VoxPackMeta() {}

    public VoxPackMeta(String id, String name, String description, String icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
}
