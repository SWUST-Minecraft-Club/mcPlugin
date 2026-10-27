package com.example.yudreamui.plugin;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

/** Imports only the verified fixed one-item buy path of the repository's Arc shop samples. */
final class ArcShopImport {
    record Result(String yaml,List<String> sources,List<String> skipped,int products) {}
    static Result read(Path arc,String currency) throws Exception {
        if(!currency.matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid Wallet currency code");
        Path directory=arc.resolve("menus/shop");var language=YamlConfiguration.loadConfiguration(arc.resolve("languages/zh_CN.yml").toFile());
        var output=new YamlConfiguration();output.set("title","系统商店");var sources=new ArrayList<String>();var skipped=new ArrayList<String>();var seen=new HashSet<String>();int total=0;
        var give=Pattern.compile("give-item: material:([a-z0-9_]+),amount:1");
        var take=Pattern.compile("take-money: 1\\*([0-9]+(?:\\.[0-9]+)?)");
        try(var files=Files.list(directory)) {
            for(Path file:files.filter(p->p.toString().endsWith(".yml")).sorted(Comparator.comparingInt(ArcShopImport::priority).thenComparing(p->p.getFileName().toString())).toList()) {
                sources.add(file.getFileName().toString());var yaml=YamlConfiguration.loadConfiguration(file.toFile());var backend=yaml.getConfigurationSection("backend");if(backend==null)continue;
                for(String key:backend.getKeys(false)) {
                    if(!key.endsWith("-buy"))continue;
                    var entries=backend.getMapList(key+".actions.shift-left");String material=null,price=null;boolean unsupported=false;
                    if(entries.size()!=1){skipped.add(file.getFileName()+":"+key+" missing fixed buy path");continue;}
                    Object actions=entries.getFirst().get("actions");if(!(actions instanceof List<?> steps))continue;
                    for(Object item:steps) {
                        if(!(item instanceof String text)){unsupported=true;break;}
                        var g=give.matcher(text);var t=take.matcher(text);
                        if(g.matches()){if(material!=null)unsupported=true;material=g.group(1);}
                        else if(t.matches()){if(price!=null)unsupported=true;price=t.group(1);}
                        else if(!text.startsWith("tell:")&&!text.startsWith("sound:")&&!text.startsWith("refresh:"))unsupported=true;
                    }
                    Material type=material==null?null:Material.matchMaterial(material);
                    if(unsupported||type==null||price==null||new BigDecimal(price).signum()<=0){skipped.add(file.getFileName()+":"+key+" unsupported financial action");continue;}
                    if(!seen.add(material+"|"+price))continue;
                    String prefix=key.substring(0,key.length()-4),raw=yaml.getString("frontend."+prefix+"-name.content",material);
                    var token=Pattern.compile("%arcmenu_lang_([^%]+)%").matcher(raw);if(token.find())raw=language.getString(token.group(1),material);
                    String id="arc-"+total++,path="products."+id;
                    output.set(path+".name",Views.plain(raw));output.set(path+".category",category(file.getFileName().toString()));output.set(path+".material",type.name());
                    output.set(path+".amount",1);output.set(path+".price",price);output.set(path+".currency",currency);output.set(path+".description","原商店目录 · "+Views.plain(raw));
                }
            }
        }
        if(total==0||total>64)throw new IllegalArgumentException("Imported catalog must have 1-64 products");
        return new Result(output.saveToString(),List.copyOf(sources),List.copyOf(skipped),total);
    }
    private static int priority(Path file){return file.getFileName().toString().matches("shop-(?:tools|weapons|armor|materials|rare)\\.yml")?0:1;}
    private static String category(String filename){return switch(filename){case "shop-tools.yml"->"工具";case "shop-weapons.yml"->"武器";case "shop-armor.yml"->"装备";case "shop-materials.yml"->"材料";case "shop-rare.yml"->"珍品";default->"精选物资";};}
}
