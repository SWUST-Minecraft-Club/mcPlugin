package com.example.town.market;

import com.example.town.economy.Payment;
import com.example.town.store.AtomicFiles;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** 库存、订单预付款、收件箱与背包交付意图一起落盘。写失败时不发布新状态。 */
public final class MarketBook {
    public enum Kind { STOCK, ORDER }
    public enum Status { DRAFT, OPEN, CLOSED }
    public enum TradeStage { RESERVED, DELIVERED, RELEASED }
    public enum IntentKind { LIST, SUPPLY, COLLECT }
    public record Listing(String id,String town,String plot,UUID owner,String ownerName,Kind kind,
                          String item,int remaining,long unitCents,String currency,long revision,
                          String world,int x,int y,int z,Status status) {
        public Listing {
            UUID.fromString(id);
            if(town==null || plot==null || owner==null || ownerName==null || kind==null || item==null || item.isBlank()
                    || remaining<0 || remaining>1_000_000 || unitCents<=0 || currency==null || currency.isBlank()
                    || revision<0 || world==null || status==null) throw new IllegalArgumentException("商铺数据无效");
        }
        public Listing quantity(int value) {return new Listing(id,town,plot,owner,ownerName,kind,item,value,unitCents,currency,Math.incrementExact(revision),world,x,y,z,status);}
        public Listing at(Status next) {return new Listing(id,town,plot,owner,ownerName,kind,item,remaining,unitCents,currency,revision,world,x,y,z,next);}
        public Listing moved(int nx,int ny,int nz) {return new Listing(id,town,plot,owner,ownerName,kind,item,remaining,unitCents,currency,Math.incrementExact(revision),world,nx,ny,nz,status);}
    }
    public record Mail(String id,UUID owner,String item,int quantity,String source) {
        public Mail {UUID.fromString(id);if(owner==null || item==null || item.isBlank() || quantity<1 || quantity>1_000_000 || source==null) throw new IllegalArgumentException("收件数据无效");}
        public Mail quantity(int n) {return new Mail(id,owner,item,n,source);}
    }
    public record Trade(String id,String action,String listing,int quantity,UUID receiver,String item,Payment payment,TradeStage stage) {
        public Trade {UUID.fromString(id);if(action==null || listing==null || quantity<0 || receiver==null || item==null || payment==null || !id.equals(payment.id()) || stage==null) throw new IllegalArgumentException("交易记录无效");}
        public Trade at(TradeStage next) {return new Trade(id,action,listing,quantity,receiver,item,payment,next);}
    }
    public record Intent(String id,UUID actor,IntentKind kind,String target,int quantity,List<String> before,List<String> after,boolean done) {
        public Intent {
            UUID.fromString(id);before=List.copyOf(before);after=List.copyOf(after);
            if(actor==null || kind==null || target==null || quantity<1 || before.size()!=36 || after.size()!=36) throw new IllegalArgumentException("背包交付意图无效");
        }
        public Intent complete() {return new Intent(id,actor,kind,target,quantity,before,after,true);}
    }
    public record State(Map<String,Listing> listings,Map<String,Mail> mail,Map<String,Trade> trades,Map<String,Intent> intents,Set<String> moves) {
        public State {listings=Map.copyOf(listings);mail=Map.copyOf(mail);trades=Map.copyOf(trades);intents=Map.copyOf(intents);moves=Set.copyOf(moves);}
        static State empty() {return new State(Map.of(),Map.of(),Map.of(),Map.of(),Set.of());}
    }
    /** 事务工作副本仅在 update 内可见。 */
    public static final class Edit {
        public final Map<String,Listing> listings;
        public final Map<String,Mail> mail;
        public final Map<String,Trade> trades;
        public final Map<String,Intent> intents;
        public final Set<String> moves;
        private Edit(State s) {listings=new HashMap<>(s.listings());mail=new HashMap<>(s.mail());trades=new HashMap<>(s.trades());intents=new HashMap<>(s.intents());moves=new HashSet<>(s.moves());}
        private State state() {return new State(listings,mail,trades,intents,moves);}
        public void reserve(String id,int quantity,long revision,Kind kind) {
            Listing l=listings.get(id);
            if(l==null || l.kind()!=kind || l.status()!=Status.OPEN || l.revision()!=revision || quantity<1 || l.remaining()<quantity)
                throw new IllegalArgumentException("商品数量或报价已改变，请刷新后重试。");
            listings.put(id,l.quantity(l.remaining()-quantity));
        }
        public void intent(Intent next) {
            if(intents.values().stream().anyMatch(i->i.actor().equals(next.actor()) && !i.done())) throw new IllegalArgumentException("上一笔背包交付仍在处理中。");
            intents.put(next.id(),next);
        }
        public void release(Trade t) {
            if(t.stage()!=TradeStage.RESERVED) return;
            Listing l=listings.get(t.listing());
            if(t.action().equals("buy") && l!=null) listings.put(l.id(),l.quantity(Math.addExact(l.remaining(),t.quantity())));
            if(t.action().equals("order") && l!=null) listings.put(l.id(),l.at(Status.CLOSED));
            trades.put(t.id(),t.at(TradeStage.RELEASED));
        }
        public void deliver(Trade t) {
            if(t.stage()==TradeStage.DELIVERED) return;
            if(t.stage()!=TradeStage.RESERVED) throw new IllegalArgumentException("交易已撤回。");
            if(t.action().equals("buy") || t.action().equals("supply"))
                mail.putIfAbsent(t.id(),new Mail(t.id(),t.receiver(),t.item(),t.quantity(),t.action()));
            if(t.action().equals("order")) {Listing l=Objects.requireNonNull(listings.get(t.listing()));listings.put(l.id(),l.at(Status.OPEN));}
            trades.put(t.id(),t.at(TradeStage.DELIVERED));
        }
    }
    private static final Gson GSON=new Gson();
    private final Path file;
    private volatile State state=State.empty();
    public MarketBook(Path file) throws IOException {
        this.file=file;
        if(!Files.exists(file)) return;
        if(Files.size(file)>128L*1024*1024) throw new IOException("商业台账过大");
        try {state=Objects.requireNonNull(GSON.fromJson(Files.readString(file),State.class));validate(state);}
        catch(RuntimeException error) {throw new IOException("商业台账损坏，保留原文件",error);}
    }
    public State state() {return state;}
    public Intent pending(UUID actor) {return state.intents().values().stream().filter(i->i.actor().equals(actor) && !i.done()).findFirst().orElse(null);}
    public synchronized <T> T update(Function<Edit,T> action) throws IOException {
        Edit edit=new Edit(state);T result=action.apply(edit);State next=edit.state();validate(next);
        byte[] bytes=GSON.toJson(next).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>128L*1024*1024) throw new IOException("商业台账达到容量限制");
        AtomicFiles.write(file,bytes);state=next;return result;
    }
    private static void validate(State s) {
        Set<UUID> pending=new HashSet<>();
        s.listings().forEach((id,l)->{if(!id.equals(l.id())) throw new IllegalArgumentException("商铺索引无效");});
        s.mail().forEach((id,m)->{if(!id.equals(m.id())) throw new IllegalArgumentException("收件索引无效");});
        s.trades().forEach((id,t)->{if(!id.equals(t.id()) || !s.listings().containsKey(t.listing())) throw new IllegalArgumentException("交易索引无效");});
        s.intents().forEach((id,i)->{if(!id.equals(i.id()) || (!i.done() && !pending.add(i.actor()))) throw new IllegalArgumentException("重复背包交付");});
    }
}
