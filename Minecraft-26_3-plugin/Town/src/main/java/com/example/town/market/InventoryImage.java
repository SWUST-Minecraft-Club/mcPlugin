package com.example.town.market;

import java.util.*;
import org.bukkit.inventory.ItemStack;

/** 只操作玩家 36 格存储空间；不触碰盔甲、光标或其他容器。 */
public final class InventoryImage {
    private InventoryImage() { }
    public static String encode(ItemStack item) {return item==null || item.isEmpty()?"":Base64.getEncoder().encodeToString(item.serializeAsBytes());}
    public static ItemStack decode(String item) {return item.isBlank()?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(item));}
    public static List<String> capture(ItemStack[] storage) {return Arrays.stream(storage).map(InventoryImage::encode).toList();}
    public static ItemStack[] restore(List<String> image) {return image.stream().map(InventoryImage::decode).toArray(ItemStack[]::new);}
    public static List<String> change(List<String> before,String sample,int amount,boolean give) {
        ItemStack match=Objects.requireNonNull(decode(sample));ItemStack[] slots=restore(before);int left=amount;
        if(amount<1 || amount>1_000_000) throw new IllegalArgumentException("数量需要在 1～1000000 之间。");
        if(give) {
            for(int pass=0;pass<2;pass++) for(int i=0;i<slots.length && left>0;i++) {
                ItemStack at=slots[i];
                if(pass==0 && at!=null && at.isSimilar(match)) {int n=Math.min(left,Math.max(0,at.getMaxStackSize()-at.getAmount()));at.setAmount(at.getAmount()+n);left-=n;}
                if(pass==1 && (at==null || at.isEmpty())) {int n=Math.min(left,match.getMaxStackSize());slots[i]=match.asQuantity(n);left-=n;}
            }
        }else for(int i=0;i<slots.length && left>0;i++) {
            ItemStack at=slots[i];if(at==null || !at.isSimilar(match)) continue;
            int n=Math.min(left,at.getAmount());left-=n;slots[i]=at.getAmount()==n?null:at.asQuantity(at.getAmount()-n);
        }
        if(left>0) throw new IllegalArgumentException(give?"背包空间不足，请减少领取数量。":"背包中同品质、同数据的物品不足。");
        return capture(slots);
    }
}
