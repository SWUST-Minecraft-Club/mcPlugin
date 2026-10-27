package com.example.town.hook;

import com.example.town.TownPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * CraftEngine 软依赖隔离层：全程反射，不引入 CE 编译期依赖。
 * 物品注册在 CE reload 之前是空的，因此只在运行期（发镇政厅道具时）按需解析。
 */
public final class CraftEngineHook {

    private final java.lang.reflect.Method byId;
    private final java.lang.reflect.Method buildItem;
    private final ClassLoader loader;

    private CraftEngineHook(java.lang.reflect.Method byId, java.lang.reflect.Method buildItem,ClassLoader loader) {
        this.byId = byId;
        this.buildItem = buildItem;
        this.loader=loader;
    }

    /** CE 未安装 / API 不兼容时返回 null。 */
    public static CraftEngineHook create(TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("CraftEngine") == null) {
            return null;
        }
        try {
            ClassLoader loader=plugin.getServer().getPluginManager().getPlugin("CraftEngine").getClass().getClassLoader();
            Class<?> itemsClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.api.CraftEngineItems",true,loader);
            java.lang.reflect.Method byId = itemsClass.getMethod("byId", String.class);
            Class<?> defClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.item.BukkitItemDefinition",true,loader);
            java.lang.reflect.Method buildItem = defClass.getMethod("buildBukkitItem");
            plugin.getLogger().info("已接入 CraftEngine：镇政厅优先使用自定义物品。");
            return new CraftEngineHook(byId, buildItem,loader);
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("CraftEngine API 不可用，镇政厅回落原版材质："
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 按 id（namespace:path 或 path）构建 CE 物品；物品不存在或失败返回 null。 */
    public ItemStack buildItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            Object definition = byId.invoke(null, id);
            if (definition == null) {
                return null;
            }
            return buildItem.invoke(definition) instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public boolean hasItem(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        try {
            return byId.invoke(null, id) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 从 CE 正规发放或创造物品栏取得的物品也保留其自定义 ID，不依赖 Town 的交付标记。 */
    public boolean isItem(ItemStack item, String id) {
        if (item == null || item.getType().isAir()) return false;
        try {
            Object key = api("CraftEngineItems").getMethod("getCustomItemId", ItemStack.class).invoke(null, item);
            return key != null && id.equals(key.toString());
        } catch (ReflectiveOperationException | LinkageError error) {
            return false;
        }
    }

    /** 真正放置 CE 方块；不能用原版材质伪装已成功放置的自定义核心。 */
    public boolean placeBlock(org.bukkit.block.Block block,String id) {
        try {
            Class<?> key=Class.forName("net.momirealms.craftengine.core.util.Key",true,loader);
            Object value=key.getMethod("of",String.class).invoke(null,id);
            Class<?> blocks=Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks",true,loader);
            return Boolean.TRUE.equals(blocks.getMethod("place",org.bukkit.Location.class,key,boolean.class)
                    .invoke(null,block.getLocation(),value,false));
        }catch(ReflectiveOperationException|LinkageError error){return false;}
    }

    public byte[] snapshotBlock(org.bukkit.block.Block block) {
        try {
            Class<?> blocks=api("CraftEngineBlocks");
            Object state=blocks.getMethod("getCustomBlockState",org.bukkit.block.Block.class).invoke(null,block);
            if(state==null) return new byte[0];
            if(Boolean.TRUE.equals(state.getClass().getMethod("hasBlockEntity").invoke(state)))
                throw new IllegalArgumentException("move-custom-block-entity-unsupported");
            Object definition=state.getClass().getMethod("owner").invoke(state);
            definition=definition.getClass().getMethod("value").invoke(definition);
            for(Object property:(java.util.Collection<?>)definition.getClass().getMethod("properties").invoke(definition)) {
                Class<?> valueType=(Class<?>)property.getClass().getMethod("valueClass").invoke(property);
                if(java.util.Set.of("DoubleBlockHalf","BedPart").contains(valueType.getSimpleName()))
                    throw new IllegalArgumentException("move-custom-multiblock-unsupported");
            }
            Object tag=state.getClass().getMethod("getNbtToSave").invoke(state);
            Class<?> nbt=Class.forName("net.momirealms.craftengine.libraries.nbt.NBT",true,loader);
            return (byte[])nbt.getMethod("toBytes",Class.forName("net.momirealms.craftengine.libraries.nbt.CompoundTag",true,loader)).invoke(null,tag);
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 方块快照 API 不兼容",error);}
    }

    public void removeBlock(org.bukkit.block.Block block) {
        try {
            Class<?> blocks=api("CraftEngineBlocks");
            if(Boolean.TRUE.equals(blocks.getMethod("isCustomBlock",org.bukkit.block.Block.class).invoke(null,block)))
                blocks.getMethod("remove",org.bukkit.block.Block.class,org.bukkit.entity.Player.class,boolean.class,boolean.class,boolean.class).invoke(null,block,null,false,false,false);
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 方块移除 API 不兼容",error);}
    }

    public void restoreBlock(org.bukkit.block.Block block,byte[] data,int turns) {
        try {
            Object state=rotatedState(data,turns);
            boolean placed=Boolean.TRUE.equals(api("CraftEngineBlocks").getMethod("place",org.bukkit.Location.class,
                    Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState",true,loader),boolean.class).invoke(null,block.getLocation(),state,false));
            if(!placed) throw new IllegalStateException("CraftEngine 方块放置失败");
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 方块恢复 API 不兼容",error);}
    }
    public org.bukkit.block.data.BlockData previewBlock(byte[] data,int turns) {
        try {
            return (org.bukkit.block.data.BlockData)api("CraftEngineBlocks").getMethod("getBukkitBlockData",
                    Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState",true,loader)).invoke(null,rotatedState(data,turns));
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 预览 API 不兼容",error);}
    }
    private Object rotatedState(byte[] data,int turns) throws ReflectiveOperationException {
        Class<?> nbt=Class.forName("net.momirealms.craftengine.libraries.nbt.NBT",true,loader);
        Object tag=nbt.getMethod("fromBytes",byte[].class).invoke(null,data);
        String id=(String)tag.getClass().getMethod("getString",String.class).invoke(tag,"id");
        Object properties=tag.getClass().getMethod("getCompound",String.class).invoke(tag,"properties");
        Class<?> key=Class.forName("net.momirealms.craftengine.core.util.Key",true,loader);
        Object definition=api("CraftEngineBlocks").getMethod("byId",key).invoke(null,key.getMethod("of",String.class).invoke(null,id));
        if(definition==null) throw new IllegalStateException("CraftEngine 方块未加载："+id);
        rotateProperties(nbt,properties,turns,definition);
        return definition.getClass().getMethod("getBlockState",properties.getClass()).invoke(definition,properties);
    }
    private void rotateProperties(Class<?> nbt,Object properties,int turns,Object definition) throws ReflectiveOperationException {
        if(turns==0) return;
        Class<?> compound=properties.getClass();
        Class<?> tagType=Class.forName("net.momirealms.craftengine.libraries.nbt.Tag",true,loader);
        var getString=compound.getMethod("getString",String.class);
        var put=compound.getMethod("put",String.class,tagType);
        java.util.Map<String,Boolean> fields=new java.util.LinkedHashMap<>();
        fields.put("facing",false);fields.put("axis",true);
        for(Object property:(java.util.Collection<?>)definition.getClass().getMethod("properties").invoke(definition)) {
            Class<?> valueType=(Class<?>)property.getClass().getMethod("valueClass").invoke(property);
            if(valueType.getName().equals("net.momirealms.craftengine.core.util.Direction") || valueType.getSimpleName().equals("Axis"))
                fields.put((String)property.getClass().getMethod("name").invoke(property),valueType.getSimpleName().equals("Axis"));
        }
        for(String field:fields.keySet()) {
            String value=(String)getString.invoke(properties,field);
            if(value==null) continue;
            String next=value;
            if(!fields.get(field)) {
                var directions=java.util.List.of("north","east","south","west");int index=directions.indexOf(value);
                if(index>=0) next=directions.get((index+turns)%4);
            }else if(turns%2==1 && (value.equals("x") || value.equals("z"))) next=value.equals("x")?"z":"x";
            put.invoke(properties,field,nbt.getMethod("createString",String.class).invoke(null,next));
        }
        var directions=java.util.List.of("north","east","south","west");
        java.util.Map<String,Object> links=new java.util.LinkedHashMap<>();
        for(String direction:directions) if(Boolean.TRUE.equals(compound.getMethod("containsKey",String.class).invoke(properties,direction)))
            links.put(direction,compound.getMethod("get",String.class).invoke(properties,direction));
        if(!links.isEmpty()) {
            if(links.size()!=4) throw new IllegalArgumentException("move-custom-rotation-unsupported");
            for(int i=0;i<4;i++) put.invoke(properties,directions.get((i+turns)%4),links.get(directions.get(i)));
        }
        if(Boolean.TRUE.equals(compound.getMethod("containsKey",String.class).invoke(properties,"rotation"))) {
            int rotation=((Number)compound.getMethod("getInt",String.class).invoke(properties,"rotation")).intValue();
            put.invoke(properties,"rotation",nbt.getMethod("createInt",int.class).invoke(null,Math.floorMod(rotation+turns*4,16)));
        }
    }

    public com.example.town.relocation.MoveSnapshot.Furniture snapshotFurniture(org.bukkit.entity.Entity entity) {
        try {
            Object furniture=furniture(entity);
            if(furniture==null) return null;
            entity=(org.bukkit.entity.Entity)furniture.getClass().getMethod("baseEntity").invoke(furniture);
            furniture.getClass().getMethod("saveIfDirty").invoke(furniture);
            Object persistent=furniture.getClass().getMethod("persistentData").invoke(furniture);
            byte[] data=(byte[])persistent.getClass().getMethod("toBytes").invoke(persistent);
            String id=furniture.getClass().getMethod("id").invoke(furniture).toString();
            org.bukkit.Location at=(org.bukkit.Location)furniture.getClass().getMethod("location").invoke(furniture);
            return new com.example.town.relocation.MoveSnapshot.Furniture(entity.getUniqueId(),id,data,
                    at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 家具快照 API 不兼容",error);}
    }

    private Object furniture(org.bukkit.entity.Entity entity) throws ReflectiveOperationException {
        Class<?> api=api("CraftEngineFurniture");
        Object value=api.getMethod("getLoadedFurnitureByMetaEntity",org.bukkit.entity.Entity.class).invoke(null,entity);
        if(value==null && Boolean.TRUE.equals(api.getMethod("isCollisionEntity",org.bukkit.entity.Entity.class).invoke(null,entity)))
            value=api.getMethod("getLoadedFurnitureByCollider",org.bukkit.entity.Entity.class).invoke(null,entity);
        if(value==null && Boolean.TRUE.equals(api.getMethod("isSeat",org.bukkit.entity.Entity.class).invoke(null,entity)))
            value=api.getMethod("getLoadedFurnitureBySeat",org.bukkit.entity.Entity.class).invoke(null,entity);
        return value;
    }
    public java.util.List<org.bukkit.util.BoundingBox> furnitureBounds(org.bukkit.entity.Entity entity) {
        try {
            Object furniture=furniture(entity);if(furniture==null) throw new IllegalStateException("家具尚未载入");
            java.util.List<org.bukkit.util.BoundingBox> bounds=new java.util.ArrayList<>();
            for(Object hit:(java.util.List<?>)furniture.getClass().getMethod("hitboxes").invoke(furniture)) {
                Class<?> type=Class.forName("net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox",true,loader);
                int count=((Number)type.getMethod("partCount").invoke(hit)).intValue();
                for(int i=0;i<count;i++) {
                    Object part=type.getMethod("part",int.class).invoke(hit,i),box=part.getClass().getMethod("aabb").invoke(part);
                    double[] a=new double[6];String[] names={"minX","minY","minZ","maxX","maxY","maxZ"};
                    for(int j=0;j<6;j++) a[j]=box.getClass().getField(names[j]).getDouble(box);
                    bounds.add(new org.bukkit.util.BoundingBox(a[0],a[1],a[2],a[3],a[4],a[5]));
                }
            }
            if(bounds.isEmpty()) bounds.add(entity.getBoundingBox());return bounds;
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 家具碰撞范围接口不兼容",error);}
    }

    public boolean furnitureEntity(org.bukkit.entity.Entity entity) {
        try {
            Class<?> furniture=api("CraftEngineFurniture");
            for(String method:java.util.List.of("isFurniture","isCollisionEntity","isSeat"))
                if(Boolean.TRUE.equals(furniture.getMethod(method,org.bukkit.entity.Entity.class).invoke(null,entity))) return true;
            return false;
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("CraftEngine 家具识别 API 不兼容",error);}
    }

    @SuppressWarnings("unchecked")
    public java.util.concurrent.CompletableFuture<Boolean> moveFurniture(org.bukkit.entity.Entity entity,org.bukkit.Location target) {
        try {
            Object furniture=api("CraftEngineFurniture").getMethod("getLoadedFurnitureByMetaEntity",org.bukkit.entity.Entity.class).invoke(null,entity);
            if(furniture==null) return java.util.concurrent.CompletableFuture.completedFuture(false);
            Class<?> positionType=Class.forName("net.momirealms.craftengine.core.world.WorldPosition",true,loader);
            Class<?> worldType=Class.forName("net.momirealms.craftengine.core.world.World",true,loader);
            Object adaptedWorld=api("BukkitAdaptor").getMethod("adapt",org.bukkit.World.class).invoke(null,target.getWorld());
            Object position=positionType.getConstructor(worldType,double.class,double.class,double.class,float.class,float.class)
                    .newInstance(adaptedWorld,target.getX(),target.getY(),target.getZ(),target.getPitch(),target.getYaw());
            return (java.util.concurrent.CompletableFuture<Boolean>)furniture.getClass().getMethod("moveTo",
                    Class.forName("net.momirealms.craftengine.core.world.WorldPosition",true,loader),boolean.class).invoke(furniture,position,true);
        }catch(ReflectiveOperationException error) {return java.util.concurrent.CompletableFuture.failedFuture(error);}
    }
    private Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName("net.momirealms.craftengine.bukkit.api."+name,true,loader);
    }
    public void protect(TownPlugin plugin) {
        var listener=new org.bukkit.event.Listener(){};
        for(String name:java.util.List.of("CustomBlockBreakEvent","CustomBlockPlaceEvent","CustomBlockInteractEvent",
                "FurnitureBreakEvent","FurniturePlaceEvent","FurnitureInteractEvent","FurnitureHitEvent")) {
            try {
                @SuppressWarnings("unchecked") Class<? extends org.bukkit.event.Event> type=(Class<? extends org.bukkit.event.Event>)
                        Class.forName("net.momirealms.craftengine.bukkit.api.event."+name,true,loader).asSubclass(org.bukkit.event.Event.class);
                if(name.equals("CustomBlockInteractEvent")) {
                    plugin.getServer().getPluginManager().registerEvent(type,listener,org.bukkit.event.EventPriority.LOWEST,(ignored,event)->{
                        try {
                            Player player=((org.bukkit.event.player.PlayerEvent)event).getPlayer();
                            if(!"MAIN_HAND".equals(type.getMethod("hand").invoke(event).toString())) return;
                            String action=type.getMethod("action").invoke(event).toString();
                            int index=action.contains("LEFT")?1:action.contains("RIGHT")?2:0;
                            if(index!=0 && plugin.selections().click(player,(ItemStack)type.getMethod("item").invoke(event),index,
                                    (org.bukkit.block.Block)type.getMethod("bukkitBlock").invoke(event)))
                                ((org.bukkit.event.Cancellable)event).setCancelled(true);
                        } catch(ReflectiveOperationException error) {
                            plugin.getLogger().warning("CraftEngine 测绘交互接口变化："+error.getClass().getSimpleName());
                        }
                    },plugin,false);
                }
                plugin.getServer().getPluginManager().registerEvent(type,listener,org.bukkit.event.EventPriority.HIGHEST,(ignored,event)->{
                    if(!(event instanceof org.bukkit.event.Cancellable cancel) || !(event instanceof org.bukkit.event.player.PlayerEvent pe)) return;
                    try {
                        org.bukkit.Location location;
                        try {location=(org.bukkit.Location)type.getMethod("location").invoke(event);}
                        catch(NoSuchMethodException missing) {
                            org.bukkit.block.Block block=(org.bukkit.block.Block)type.getMethod("block").invoke(event);location=block.getLocation();
                        }
                        var action=name.contains("Interact")?com.example.town.model.PlotRules.Action.CONTAINER:com.example.town.model.PlotRules.Action.BUILD;
                        if((name.equals("CustomBlockBreakEvent") && plugin.selections().isTool(pe.getPlayer().getInventory().getItemInMainHand()))
                                || plugin.service().denied(pe.getPlayer(),location,action)
                                || (name.equals("CustomBlockBreakEvent") && plugin.townHalls().hallAt(location.getBlock())!=null)) cancel.setCancelled(true);
                    }catch(ReflectiveOperationException|ClassCastException error) {
                        cancel.setCancelled(true);
                        plugin.getLogger().warning("CraftEngine 保护事件接口变化："+name);
                    }
                },plugin,true);
            }catch(ClassNotFoundException missing) {plugin.getLogger().warning("CraftEngine 未提供保护事件："+name);}
        }
    }
}
