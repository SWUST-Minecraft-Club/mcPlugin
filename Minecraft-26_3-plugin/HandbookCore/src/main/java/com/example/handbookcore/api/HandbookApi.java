package com.example.handbookcore.api;

import org.bukkit.Bukkit;

/** 跨插件入口；也可以直接通过 Bukkit ServicesManager 获取 HandbookService。 */
public final class HandbookApi {
    private HandbookApi() { }
    public static HandbookService serviceOrNull() {
        return Bukkit.getServicesManager().load(HandbookService.class);
    }
    public static HandbookService service() {
        HandbookService service = serviceOrNull();
        if (service == null) throw new IllegalStateException("HandbookCore 尚未加载");
        return service;
    }
}
