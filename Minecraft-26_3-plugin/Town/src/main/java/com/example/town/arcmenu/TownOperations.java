package com.example.town.arcmenu;

import com.example.town.*;
import com.example.town.economy.*;
import com.example.town.market.*;
import com.example.town.model.*;
import com.example.town.relocation.*;
import com.example.town.service.PlotService;
import com.example.town.util.Threads;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 每位玩家的经营表单和确认报价；服务层仍负责权属、版本与支付恢复。 */
final class TownOperations {
    private final TownPlugin plugin;
    private final TownUi ui;
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    private record Pending(String token,long expires,String back,String title,List<String> lines,Runnable action) { }
    private record Approval(String kind,String token,String name,List<String> lines) { }
    private static final class Session {
        String screen="civic",selected="",plot="",name="新地块",owner="",catalog="technology",ledger="entries";
        PlotKind kind=PlotKind.RESIDENTIAL;
        int page=1,quantity=1,step=1;
        long unit=100;
        boolean hall=false,order=false;
        List<?> rows=List.of();
        Pending pending;
    }
    TownOperations(TownPlugin plugin,TownUi ui) {this.plugin=plugin;this.ui=ui;}
    private Session session(Player p) {return sessions.computeIfAbsent(p.getUniqueId(),id->new Session());}
    void forget(UUID actor) {sessions.remove(actor);}
    private void meta(Player p,String key,String value) {ui.set(p,"op_"+key,value==null?"":safe(value));}
    private static String safe(String s) {return s.replace('&','＆').replace('{','（').replace('}','）');}
    private void lines(Player p,List<String> lines) {for(int i=0;i<6;i++) meta(p,"line_"+i,i<lines.size()?lines.get(i):"");}
    private void show(Player p,String screen) {
        Session s=session(p);s.screen=screen;
        if(!TownOperationMenus.contains(screen)) throw new IllegalArgumentException("未知页面。");
        if(write(p)) ui.markAndOpen(p,TownOperationMenus.PREFIX+screen);
    }
    private void confirm(Player p,String title,List<String> lines,Runnable action) {
        Session s=session(p);s.pending=new Pending(UUID.randomUUID().toString(),System.currentTimeMillis()+120_000,s.screen,title,List.copyOf(lines),action);show(p,"confirm");
    }
    private Consumer<com.example.town.service.TownService.Result> feedback(Player p) {
        return result->{plugin.service().deliver(p,result);Threads.forPlayer(plugin,p,()->ui.refresh(p));};
    }
    void command(Player p,String input) {
        Session s=session(p);String[] a=input.trim().split("\\s+");
        try {
            switch(a[0]) {
                case "civic","plots","market","mail","planning","jobs","approvals"->{s.page=1;show(p,a[0]);}
                case "page"->{s.page=Math.max(1,s.page+Integer.parseInt(a[1]));show(p,s.screen);}
                case "catalog"->{s.catalog=a.length>1?a[1]:"technology";s.page=1;show(p,"catalog");}
                case "catalog-pick"->catalogPick(p,s,row(s,a[1]));
                case "ledger"->{s.ledger=a[1];show(p,"civic");}
                case "input"->input(p,s,a[1]);
                case "back"->{String back=s.pending==null?"civic":s.pending.back();s.pending=null;show(p,back);}
                case "commit"->{
                    Pending pending=s.pending;if(pending==null || a.length!=2 || !pending.token().equals(a[1]) || pending.expires()<System.currentTimeMillis()) throw new IllegalArgumentException("确认已过期，请重新预览。");
                    s.pending=null;s.screen=pending.back();pending.action().run();show(p,s.screen.equals("plot") && s.plot.isBlank()?"plots":s.screen);
                }
                case "tool"->{plugin.selections().giveTool(p);ui.closeOperations(p);}
                case "plot-pick"->{s.plot=((Plot)row(s,a[1])).id();show(p,"plot");}
                case "plot-new"->{s.plot="";s.name="新地块";s.owner=p.getName();s.kind=PlotKind.RESIDENTIAL;show(p,"plot-form");}
                case "plot-edit"->{var plot=plot(s.plot);s.name=plot.name();s.owner=name(plot.owner());s.kind=plot.kind();show(p,"plot-form");}
                case "form-kind"->{s.kind=PlotKind.values()[(s.kind.ordinal()+1)%PlotKind.values().length];show(p,"plot-form");}
                case "plot-preview"->previewPlot(p,s);
                case "plot-submit"->submitPlot(p,s);
                case "plot-kind"->{var plot=plot(s.plot);PlotKind next=PlotKind.values()[(plot.kind().ordinal()+1)%PlotKind.values().length];plotAction(p,s,"修改用途为 "+next.display(),List.of(plot.name(),"用途修改可能影响经营权限。"),()->plugin.plots().update(p.getUniqueId(),townForPlot(plot.id()).id(),plot.id(),null,next,null));}
                case "plot-public"->{var plot=plot(s.plot);boolean next=!plot.publicInteractionAt(System.currentTimeMillis());plotAction(p,s,next?"开放访客交互":"关闭访客交互",List.of(plot.name(),"容器和建造仍按业主／租户授权。"),()->plugin.plots().update(p.getUniqueId(),townForPlot(plot.id()).id(),plot.id(),null,null,next));}
                case "plot-unoffer"->{String id=s.plot;plotAction(p,s,"取消出租挂牌",List.of(plot(id).name()),()->plugin.plots().cancelOffer(p.getUniqueId(),townForPlot(id).id(),id));}
                case "plot-rent"->{Plot plot=plot(s.plot);plotAction(p,s,"承租 "+plot.name(),List.of("租金 "+Money.format(plot.offeredRentCents())+" "+currencyLabel(plot.offeredCurrency()),"租期 "+duration(plot.offeredDurationMillis()),"租约期间容器与建造按租户授权。"),()->plugin.civic().rent(p,plot.id(),feedback(p)));}
                case "plot-remove"->{Plot plot=plot(s.plot);plotAction(p,s,"撤销地块",List.of(plot.name(),"原范围归回小镇管理，请先下架经营资产。"),()->{plugin.plots().remove(p.getUniqueId(),townForPlot(plot.id()).id(),plot.id(),plot.revision(),System.currentTimeMillis());s.plot="";});}
                case "plot-move"->{s.hall=false;show(p,"planning");}
                case "move-hall"->{if(plugin.relocation().draft(p.getUniqueId())!=null) throw new IllegalArgumentException("请先取消当前草案再切换迁移类型。");s.hall=!s.hall;show(p,"planning");}
                case "move-begin"->{BlockBox box=plugin.selections().box(p.getUniqueId());plugin.relocation().begin(p,Math.addExact(box.maxX(),2),box.minY(),box.minZ(),s.hall?"":s.plot,s.hall);show(p,"planning");}
                case "shift"->{plugin.relocation().adjust(p,Math.multiplyExact(s.step,Integer.parseInt(a[1])),Math.multiplyExact(s.step,Integer.parseInt(a[2])),Math.multiplyExact(s.step,Integer.parseInt(a[3])),false);ui.refresh(p);}
                case "step"->{s.step=s.step==1?5:s.step==5?16:1;ui.refresh(p);}
                case "rotate"->{plugin.relocation().adjust(p,0,0,0,true);ui.refresh(p);}
                case "ground"->plugin.relocation().ground(p);
                case "world-view"->ui.closeOperations(p);
                case "move-cancel"->{plugin.relocation().cancel(p);ui.refresh(p);}
                case "move-confirm"->{var d=Objects.requireNonNull(plugin.relocation().draft(p.getUniqueId()),"请先建立草案。");long price=plugin.relocation().price(d);
                    confirm(p,"确认建筑迁移",List.of("原址 "+RelocationService.box(d.move().source()),"目标 "+RelocationService.box(d.move().target()),"费用 "+Money.format(price)+" "+currencyLabel(plugin.civic().currency()),"源区和目标将锁定至验收或撤回。"),()->plugin.relocation().confirm(p,d.token(),price));}
                case "job-pick"->{s.selected=((MoveJob)row(s,a[1])).id();show(p,"job");}
                case "job-accept","job-undo","job-retry"->{String id=s.selected;String op=a[0];
                    confirm(p,op.equals("job-accept")?"验收并开放建筑":op.equals("job-undo")?"撤回工程并退款":"重试工程",List.of("工程 "+shortId(id),"撤回会用保存的原始快照恢复整个施工选区。"),()->{
                        switch(op) {case "job-accept"->plugin.relocation().accept(p,id);case "job-undo"->plugin.relocation().undo(p,id);default->plugin.relocation().retry(p,id);}
                    });}
                case "product-pick"->{s.selected=((MarketBook.Listing)row(s,a[1])).id();s.quantity=1;show(p,"product");}
                case "market-new"->{s.order=a.length>1 && a[1].equals("order");s.quantity=1;s.unit=100;show(p,"market-form");}
                case "quantity"->{s.quantity=Math.clamp(s.quantity+Integer.parseInt(a[1]),1,1000000);ui.refresh(p);}
                case "market-publish"->{int quantity=s.quantity;long unit=s.unit;boolean order=s.order;
                    String sample=InventoryImage.encode(p.getInventory().getItemInMainHand().asOne());
                    confirm(p,order?"发布预付收购单":"托管商品并开店",List.of(itemName(sample)+" × "+quantity,"单价 "+Money.format(unit)+" "+currencyLabel(plugin.civic().currency()),"合计 "+Money.format(Math.multiplyExact(unit,quantity)),order?"先扣除全部预付款，取消可结算未用余额。":"托管物品后开放商铺，剩余库存下架送回收件箱。"),()->{
                        if(!sample.equals(InventoryImage.encode(p.getInventory().getItemInMainHand().asOne()))) throw new IllegalArgumentException("主手样品已改变，请重新预览。");
                        if(order) plugin.market().order(p,quantity,unit);else plugin.market().stock(p,quantity,unit);
                    });}
                case "product-trade"->{var l=product(s);int quantity=s.quantity;long net=plugin.market().netQuote(l,p.getUniqueId(),quantity);
                    confirm(p,l.kind()==MarketBook.Kind.STOCK?"购买商品":"交付收购订单",List.of(itemName(l.item())+" × "+quantity,"单价 "+Money.format(l.unitCents())+" "+currencyLabel(l.currency()),"总额 "+Money.format(Math.multiplyExact(quantity,l.unitCents()))+" · 收款人实际到账 "+Money.format(net),"商品进入收件箱，按预览版本结算。"),()->{
                        if(l.kind()==MarketBook.Kind.STOCK) plugin.market().buy(p,l.id(),quantity,l.revision(),l.unitCents(),net);else plugin.market().supply(p,l.id(),quantity,l.revision(),l.unitCents(),net);
                    });}
                case "product-close"->{var l=product(s);confirm(p,"下架 "+itemName(l.item()),List.of("剩余数量 "+l.remaining(),l.kind()==MarketBook.Kind.STOCK?"剩余商品进入收件箱。":"未使用的预付款自动结算回钱包。"),()->plugin.market().close(p,l.id(),l.revision()));}
                case "mail-pick"->{var m=(MarketBook.Mail)row(s,a[1]);int q=Math.min(m.quantity(),Objects.requireNonNull(InventoryImage.decode(m.item())).getMaxStackSize());
                    confirm(p,"领取物品",List.of(itemName(m.item())+" × "+q,"领取后保存背包，再减少收件箱数量。"),()->plugin.market().collect(p,m.id(),q));}
                case "approval-pick"->{Approval approval=(Approval)row(s,a[1]);confirm(p,"审批："+approval.name(),approval.lines(),()->{
                    if(approval.kind().equals("move")) plugin.relocation().approve(p,approval.token());else plugin.plots().approve(p.getUniqueId(),approval.token(),System.currentTimeMillis());
                });}
                default->throw new IllegalArgumentException("未知经营操作。");
            }
        }catch(IllegalArgumentException|ArithmeticException|NullPointerException error) {p.sendPlainMessage("[小镇] "+friendly(RelocationService.reason(error)));}
    }
    private static Object row(Session s,String index) {int i=(s.page-1)*6+Integer.parseInt(index);if(i<0 || i>=s.rows.size()) throw new IllegalArgumentException("列表已改变，请刷新。");return s.rows.get(i);}
    private void catalogPick(Player p,Session s,Object value) {
        TownSnapshot town=town(p);String id;long cost;boolean tech=value instanceof CivicCatalog.Technology;
        if(tech) {var d=(CivicCatalog.Technology)value;id=d.id();cost=d.cost(town.civic().level(id));}
        else {var d=(CivicCatalog.PolicyDef)value;id=d.id();cost=d.cents();}
        int level=town.civic().level(id);String currency=plugin.civic().currency();String name=tech?((CivicCatalog.Technology)value).name():((CivicCatalog.PolicyDef)value).name();
        List<String> text=new ArrayList<>(List.of(name,"镇库支出 "+Money.format(cost)+" "+currencyLabel(plugin.civic().currency())));
        if(tech) text.add("永久科技 · 当前等级 "+level);else {var policy=(CivicCatalog.PolicyDef)value;text.add("持续 "+duration(policy.durationMillis())+" · 受益 "+scope(policy.scope()));text.add(policyEffects(policy));}
        confirm(p,"购买发展项目",text,()->{
            try(var ignored=plugin.registry().guard()) {
                var current=town(p);Object definition=tech?plugin.civic().catalog().technologies().get(id):plugin.civic().catalog().policies().get(id);
                if(!town.id().equals(current.id()) || current.civic().level(id)!=level || !value.equals(definition) || !currency.equals(plugin.civic().currency())) throw new IllegalArgumentException("采购项目或报价已改变，请重新预览。");
                plugin.civic().purchase(p,id,tech,feedback(p));
            }
        });
    }
    private void input(Player p,Session s,String kind) {
        String title=switch(kind) {case "donate"->"贡献金额";case "withdraw"->"提取金额";case "quantity"->"数量";case "unit"->"商品单价";case "move-target"->"目标 X Y Z";case "plot-offer"->"租金 天数，例如 100 7";case "plot-trust","plot-untrust","plot-transfer","form-owner"->"玩家名或 UUID";default->"地块名称";};
        ui.requestOperationInput(p,title,value->{
            try {
                switch(kind) {
                    case "donate","withdraw"->{long amount=Money.cents(value);confirm(p,kind.equals("donate")?"贡献镇库":"提取镇库",List.of("金额 "+Money.format(amount)+" "+currencyLabel(plugin.civic().currency())),()->{if(kind.equals("donate")) plugin.civic().donate(p,amount,feedback(p));else plugin.civic().withdraw(p,amount,feedback(p));});return;}
                    case "quantity"->s.quantity=Math.clamp(Integer.parseInt(value),1,1000000);
                    case "unit"->{s.unit=Money.cents(value);if(s.unit<=0) throw new IllegalArgumentException("单价须大于零。");}
                    case "form-name"->s.name=value;
                    case "form-owner"->{playerId(value);s.owner=value;}
                    case "move-target"->{String[] xyz=value.trim().split("\\s+");if(xyz.length!=3) throw new IllegalArgumentException("请输入 X Y Z 三个整数。");
                        var d=plugin.relocation().draft(p.getUniqueId());int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);
                        if(d==null) plugin.relocation().begin(p,x,y,z,s.hall?"":s.plot,s.hall);else plugin.relocation().adjust(p,x-d.move().targetX(),y-d.move().targetY(),z-d.move().targetZ(),false);
                    }
                    case "plot-name"->{String id=s.plot;plotAction(p,s,"修改地块名称",List.of(value),()->plugin.plots().update(p.getUniqueId(),townForPlot(id).id(),id,value,null,null));return;}
                    case "plot-trust","plot-untrust","plot-transfer"->{UUID target=playerId(value);String id=s.plot;plotAction(p,s,title,List.of(plot(id).name(),value),()->{
                        if(kind.equals("plot-transfer")) plugin.plots().transfer(p.getUniqueId(),townForPlot(id).id(),id,target);
                        else plugin.plots().trust(p.getUniqueId(),townForPlot(id).id(),id,target,kind.equals("plot-trust"),System.currentTimeMillis());
                    });return;}
                    case "plot-offer"->{String[] values=value.trim().split("\\s+");if(values.length!=2) throw new IllegalArgumentException("请输入租金和天数。");long amount=Money.cents(values[0]),days=Long.parseLong(values[1]);String id=s.plot,currency=plugin.civic().currency();
                        plotAction(p,s,"出租挂牌",List.of(plot(id).name(),Money.format(amount)+" "+currencyLabel(currency)+" ／ "+days+" 天"),()->plugin.plots().offerRent(p.getUniqueId(),townForPlot(id).id(),id,amount,Math.multiplyExact(days,86400_000),System.currentTimeMillis(),currency));return;}
                    default->throw new IllegalArgumentException("未知输入。");
                }
                show(p,s.screen);
            }catch(IllegalArgumentException|ArithmeticException error) {p.sendPlainMessage("[小镇] "+friendly(RelocationService.reason(error)));show(p,s.screen);}
        });
    }
    private void plotAction(Player p,Session s,String title,List<String> lines,Runnable action) {
        Plot plot=plot(s.plot);confirm(p,title,lines,()->{try(var ignored=plugin.registry().guard()) {if(plot(plot.id()).revision()!=plot.revision()) throw new IllegalArgumentException("地块资料已改变，请重新确认。");action.run();}});
    }
    private void previewPlot(Player p,Session s) {
        if(s.plot.isBlank()) {show(p,"plot-form");return;}
        PlotService.EditProposal proposal=plugin.plots().propose(p.getUniqueId(),townForPlot(s.plot).id(),s.plot,plugin.selections().box(p.getUniqueId()).footprint(),System.currentTimeMillis());
        plugin.selections().previewEdit(p,proposal);
        for(UUID id:proposal.awaiting()) {Player approver=Bukkit.getPlayer(id);if(approver!=null) Threads.forPlayer(plugin,approver,()->approver.sendPlainMessage("[小镇] 有地块边界调整等待你审批，请打开小镇的待办审批。"));}
        ui.refresh(p);
    }
    private void submitPlot(Player p,Session s) {
        PlotBounds bounds=plugin.selections().box(p.getUniqueId()).footprint();
        if(s.plot.isBlank()) {
            String name=s.name;UUID owner=s.owner.isBlank()?p.getUniqueId():playerId(s.owner);PlotKind kind=s.kind;
            confirm(p,"划分 "+kind.display(),List.of(name,bounds(bounds),"覆盖全部 Y 高度 · 业主 "+name(owner)),()->{var plot=plugin.plots().create(p.getUniqueId(),name,kind,bounds,owner);s.plot=plot.id();s.screen="plot";});
        }else {
            var proposal=plugin.plots().pending(p.getUniqueId());if(proposal==null || !proposal.plotId().equals(s.plot) || !proposal.next().equals(bounds)) throw new IllegalArgumentException("请先预览当前选区的边界修改。");
            confirm(p,"提交地块边界修改",List.of("原："+bounds(proposal.previous()),"新："+bounds(proposal.next()),"仍待 "+proposal.awaiting().size()+" 人同意"),()->plugin.plots().confirm(p.getUniqueId(),proposal.token(),System.currentTimeMillis()));
        }
    }
    boolean write(Player p) {
        Session s=session(p);
        try {
            switch(s.screen) {
                case "civic"->{
                    var town=town(p);var civic=town.civic();String code=plugin.civic().currency();
                    var balance=Money.decimal(civic.balance(code));
                    var hook=plugin.service().wallet();
                    String symbol=hook==null?code:hook.symbolOf(plugin.config().currencyCode(),code);
                    if(symbol.isBlank()) symbol=code;
                    symbol=BalanceDisplay.plain(symbol);
                    meta(p,"treasury",BalanceDisplay.amount(balance));
                    meta(p,"treasury_currency",BalanceDisplay.currency(symbol));
                    meta(p,"treasury_full",BalanceDisplay.full(balance)+" "+symbol);
                    meta(p,"slots","政策槽位 "+plugin.civic().catalog().active(civic,System.currentTimeMillis()).size()+" / "+plugin.civic().catalog().slots(civic));
                    meta(p,"ledger_title",s.ledger.equals("contributions")?"玩家贡献排行":"最近镇库流水");
                    if(s.ledger.equals("contributions")) lines(p,civic.contributions().entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(6).map(e->contributor(e.getKey())+" · "+Money.format(e.getValue())).toList());
                    else lines(p,civic.ledger().reversed().stream().limit(6).map(e->e.reason()+" · "+Money.format(e.delta())+" "+currencyLabel(e.currency())+" · "+name(e.actor())).toList());
                }
                case "catalog"->{
                    TownSnapshot town=town(p);
                    if(s.catalog.equals("policy")) renderRows(p,s,plugin.civic().catalog().policies().values().stream().sorted(Comparator.comparing(CivicCatalog.PolicyDef::id)).toList(),v->{var d=(CivicCatalog.PolicyDef)v;long expires=town.civic().policies().getOrDefault(d.id(),new CivicState.Policy(d.id(),0)).expiresAt();return new String[]{d.name(),Money.format(d.cents())+" · "+duration(d.durationMillis())+" · "+scope(d.scope())+(expires>System.currentTimeMillis()?" · 剩余 "+duration(expires-System.currentTimeMillis()):""),expires>System.currentTimeMillis()?"续购":"查看并购买"};});
                    else renderRows(p,s,plugin.civic().catalog().technologies().values().stream().sorted(Comparator.comparing(CivicCatalog.Technology::id)).toList(),v->{var d=(CivicCatalog.Technology)v;int level=town.civic().level(d.id());return new String[]{d.name()+" · "+level+" / "+d.maxLevel(),techEffect(d)+" · 升级 "+Money.format(d.cost(level)),level>=d.maxLevel()?"已满级":"查看并升级"};});
                }
                case "plots"->{
                    var local=plugin.service().townAt(ChunkKey.of(p.getLocation()));var member=plugin.service().townOf(p.getUniqueId());
                    List<Plot> plots=plugin.service().listTowns().stream().flatMap(t->t.raw().plots().values().stream().filter(plot->(local!=null && local.id().equals(t.id())) || (member!=null && member.id().equals(t.id())) || plot.owner().equals(p.getUniqueId()) || (plot.lease()!=null && plot.lease().tenant().equals(p.getUniqueId())))).sorted(Comparator.comparing(Plot::id)).toList();
                    renderRows(p,s,plots,v->{var plot=(Plot)v;return new String[]{plot.name()+" · "+plot.kind().display(),plot.bounds().area()+" 平方格 · 业主 "+name(plot.owner())+(plot.lease()!=null && plot.lease().active(System.currentTimeMillis())?" · 租期中":plot.offeredDurationMillis()>0?" · 出租中":""),"经营详情"};});
                }
                case "plot"->{
                    Plot plot=plot(s.plot);meta(p,"plot_name",plot.name()+" · "+plot.kind().display());
                    lines(p,List.of(bounds(plot.bounds()),"业主 "+name(plot.owner())+" · 授权 "+plot.trusted().size()+" 人 · 访客交互 "+(plot.publicInteractionAt(System.currentTimeMillis())?"开放":"关闭"),
                            plot.lease()!=null && plot.lease().active(System.currentTimeMillis())?"租户 "+name(plot.lease().tenant())+" · 剩余 "+duration(plot.lease().expiresAt()-System.currentTimeMillis()):"当前没有生效租约",plot.offeredDurationMillis()>0?"挂牌 "+Money.format(plot.offeredRentCents())+" "+currencyLabel(plot.offeredCurrency())+" ／ "+duration(plot.offeredDurationMillis()):"尚未挂牌出租"));
                }
                case "plot-form"->{
                    selection(p);meta(p,"form_name",clip(s.name,14));meta(p,"form_kind",s.kind.display());meta(p,"form_owner",s.owner.isBlank()?p.getName():s.owner);meta(p,"plot_submit",s.plot.isBlank()?"确认划分新地块":"确认提交边界修改");
                    var proposal=plugin.plots().pending(p.getUniqueId());
                    meta(p,"old_bounds",s.plot.isBlank()?"新划分地块": "原边界："+bounds(plot(s.plot).bounds()));
                    meta(p,"new_bounds",proposal!=null && proposal.plotId().equals(s.plot)?"拟调整："+bounds(proposal.next()):"用测绘工具选定新矩形，再预览边界");
                    meta(p,"proposal",proposal!=null && proposal.plotId().equals(s.plot)?"仍需 "+proposal.awaiting().size()+" 人同意，确认前原边界继续生效":"边界变更保留地块标识、租约与授权");
                }
                case "planning"->{
                    var d=plugin.relocation().draft(p.getUniqueId());meta(p,"step",String.valueOf(s.step));meta(p,"hall_mode",(d==null?s.hall:d.hall())?"模式：政厅迁址":"模式：建筑迁移");
                    String linked=d==null?s.plot:d.plot();meta(p,"move_linked",linked.isBlank()?"未关联地块": "关联："+clip(plot(linked).name(),15));
                    if(d==null) {
                        try {meta(p,"move_source","源区 "+RelocationService.box(plugin.selections().box(p.getUniqueId())));}catch(IllegalArgumentException e){meta(p,"move_source","先用测绘工具选择建筑的三维两角");}
                        meta(p,"move_target","输入目标坐标，或建立草案后用方向按钮微调");meta(p,"move_problem","请把地板、屋顶和附属方块完整包含在选区内");meta(p,"move_quote","建立草案后检查碰撞与报价");
                    }else {
                        meta(p,"move_source","原址 "+RelocationService.box(d.move().source()));meta(p,"move_target","目标 "+RelocationService.box(d.move().target())+" · "+d.move().quarterTurns()*90+"°");
                        meta(p,"move_problem",d.scanning()?"正在扫描方块、容器与家具…":!d.problem().isBlank()?"暂不可施工："+friendly(d.problem()):"选区可用 · 仍需 "+d.required().stream().filter(id->!d.approved().contains(id)).count()+" 人同意");
                        meta(p,"move_quote",d.scanning()?"报价计算中…":"迁移费 "+Money.format(plugin.relocation().price(d))+" "+currencyLabel(plugin.civic().currency()));
                    }
                }
                case "jobs"->renderRows(p,s,plugin.relocation().jobs(p.getUniqueId()).stream().sorted(Comparator.comparingLong(MoveJob::createdAt).reversed()).toList(),v->{var j=(MoveJob)v;return new String[]{"工程 "+shortId(j.id())+" · "+stage(j.stage()),RelocationService.box(j.transform().target())+" · "+Money.format(j.cents())+" "+currencyLabel(j.currency()),"查看工程"};});
                case "job"->{
                    var j=plugin.relocation().jobs(p.getUniqueId()).stream().filter(job->job.id().equals(s.selected)).findFirst().orElseThrow(()->new IllegalArgumentException("找不到工程。"));
                    meta(p,"job_stage",stage(j.stage()));lines(p,List.of("工程 "+shortId(j.id()),"原址 "+RelocationService.box(j.transform().source()),"目标 "+RelocationService.box(j.transform().target()),"费用 "+Money.format(j.cents())+" "+currencyLabel(j.currency()),j.error().isBlank()?"工程状态已持久保存":clip(friendly(j.error()),54),j.stage()==MoveJob.Stage.REVIEW?"可走入检查；容器和建造在验收前保持锁定。":"可随时关闭菜单，施工与结算会继续。"));
                }
                case "market"->renderRows(p,s,plugin.market().listings(),v->{var l=(MarketBook.Listing)v;return new String[]{(l.kind()==MarketBook.Kind.STOCK?"现货 · ":"收购 · ")+itemName(l.item()),"单价 "+Money.format(l.unitCents())+" "+currencyLabel(l.currency())+" · 剩余 "+l.remaining()+" · "+l.ownerName(),"查看商品"};});
                case "product"->{
                    var l=product(s);meta(p,"product_name",itemName(l.item()));meta(p,"quantity",String.valueOf(s.quantity));
                    lines(p,List.of(l.kind()==MarketBook.Kind.STOCK?"现货商铺 · 托管库存":"玩家收购单 · 预付款已托管","经营者 "+l.ownerName()+" · 剩余 "+l.remaining(),"单价 "+Money.format(l.unitCents())+" "+currencyLabel(l.currency()),"地址 "+l.world()+" · "+l.x()+" / "+l.y()+" / "+l.z(),"结算手续费已在确认页列出，卖家离线可收款。"));
                    meta(p,"total","合计 "+Money.format(Math.multiplyExact(s.quantity,l.unitCents()))+" "+currencyLabel(l.currency()));meta(p,"trade_action",l.kind()==MarketBook.Kind.STOCK?"预览购买":"预览供应订单");
                }
                case "market-form"->{
                    meta(p,"market_mode",s.order?"发布预付收购单":"开设托管现货商铺");meta(p,"sample",p.getInventory().getItemInMainHand().isEmpty()?"请在主手拿着商品样品":"当前样品："+itemName(InventoryImage.encode(p.getInventory().getItemInMainHand().asOne())));
                    meta(p,"quantity",String.valueOf(s.quantity));meta(p,"unit",Money.format(s.unit)+" "+currencyLabel(plugin.civic().currency()));meta(p,"total","总额 "+Money.format(Math.multiplyExact(s.quantity,s.unit))+" "+currencyLabel(plugin.civic().currency()));
                    meta(p,"escrow_hint",s.order?"发布时扣除全部预付款；取消订单可返还未用余额。":"发布时托管商品；成交后自动结算到钱包。");
                }
                case "mail"->renderRows(p,s,plugin.market().mail(p.getUniqueId()),v->{var m=(MarketBook.Mail)v;return new String[]{itemName(m.item())+" × "+m.quantity(),m.source()+" · 每次最多领取一组","领取一组"};});
                case "approvals"->{
                    List<Approval> all=new ArrayList<>();
                    for(var proposal:plugin.plots().awaiting(p.getUniqueId())) all.add(new Approval("plot",proposal.token(),"地块边界调整",List.of("申请人 "+name(proposal.proposer()),"原："+bounds(proposal.previous()),"新："+bounds(proposal.next()),"同意后仍需申请人提交。")));
                    for(var d:plugin.relocation().approvals(p.getUniqueId())) all.add(new Approval("move",d.token(),"建筑迁移",List.of("申请人 "+name(d.actor()),"原："+RelocationService.box(d.move().source()),"新："+RelocationService.box(d.move().target())+" · "+d.move().quarterTurns()*90+"°","坐标或朝向改变后需要重新同意。")));
                    renderRows(p,s,all,v->{var a=(Approval)v;return new String[]{a.name(),a.lines().getFirst(),"核对并审批"};});
                }
                case "confirm"->{Pending pending=s.pending;if(pending==null) throw new IllegalArgumentException("请重新预览操作。");meta(p,"confirm_title",pending.title());meta(p,"confirm_token",pending.token());lines(p,pending.lines());}
                default->throw new IllegalArgumentException("页面不存在。");
            }
            return true;
        }catch(RuntimeException error) {
            s.pending=null;s.rows=List.of();ui.closeOperations(p);
            p.sendPlainMessage("[小镇] "+friendly(RelocationService.reason(error))+"，请重新打开相应页面。");
            plugin.getLogger().fine("经营界面等待刷新："+RelocationService.reason(error));
            return false;
        }
    }
    private void renderRows(Player p,Session s,List<?> values,java.util.function.Function<Object,String[]> render) {
        s.rows=List.copyOf(values);int pages=Math.max(1,(values.size()+5)/6);s.page=Math.clamp(s.page,1,pages);meta(p,"page",s.page+" / "+pages);
        for(int i=0;i<6;i++) {
            int index=(s.page-1)*6+i;boolean present=index<values.size();String[] fields=present?render.apply(values.get(index)):new String[]{i==0?"暂无记录":"","",""};
            meta(p,"row_"+i+"_present",present?"1":"0");meta(p,"row_"+i+"_name",clip(fields[0],30));meta(p,"row_"+i+"_info",clip(fields[1],54));meta(p,"row_"+i+"_action",fields[2]);
        }
    }
    private void selection(Player p) {
        try {BlockBox box=plugin.selections().box(p.getUniqueId());meta(p,"selection",bounds(box.footprint()));meta(p,"selection_detail","面积 "+box.footprint().area()+" 平方格 · 地块覆盖全部 Y 高度");}
        catch(IllegalArgumentException error) {meta(p,"selection","尚未选定矩形");meta(p,"selection_detail","持测绘工具左键选第一点，右键选第二点");}
    }
    private TownSnapshot town(Player p) {var town=plugin.service().townOf(p.getUniqueId());if(town==null) throw new IllegalArgumentException("not-in-town");return town.raw();}
    private TownSnapshot townForPlot(String plot) {return plugin.service().listTowns().stream().map(v->v.raw()).filter(t->t.plots().containsKey(plot)).findFirst().orElseThrow(()->new IllegalArgumentException("plot-not-found"));}
    private Plot plot(String id) {return townForPlot(id).plots().get(id);}
    private MarketBook.Listing product(Session s) {return Objects.requireNonNull(plugin.market().book().state().listings().get(s.selected),"商品已不存在。");}
    private static UUID playerId(String value) {Player p=Bukkit.getPlayerExact(value);return p==null?UUID.fromString(value):p.getUniqueId();}
    private static String bounds(PlotBounds b) {return b.world()+" · ("+b.minX()+", "+b.minZ()+") → ("+b.maxX()+", "+b.maxZ()+")";}
    private static String name(UUID id) {var p=Bukkit.getOfflinePlayer(id);return p.getName()==null?shortId(id.toString()):p.getName();}
    private static String shortId(String id) {return id.substring(0,Math.min(8,id.length()));}
    private String contributor(String key) {int sep=key.indexOf(':');if(sep<0) return key;try {return name(UUID.fromString(key.substring(0,sep)))+" · "+currencyLabel(key.substring(sep+1));}catch(IllegalArgumentException e){return key;}}
    /** 展示名与账本币种分开；历史交易仍保留原始来源标识。 */
    private String currencyLabel(String code) {
        var hook=plugin.service().wallet();
        if(hook!=null && hook.acceptsCurrency(code)) {
            String symbol=hook.symbolOf(plugin.config().currencyCode(),code);
            if(symbol!=null && !symbol.isBlank()) return BalanceDisplay.currency(symbol);
        }
        return BalanceDisplay.currency(code.startsWith("vault:")?code.substring(code.lastIndexOf(':')+1):code);
    }
    private static String itemName(String encoded) {
        var item=InventoryImage.decode(encoded);if(item==null || item.isEmpty()) return "空物品";
        var meta=item.getItemMeta();if(meta.hasDisplayName()) return clip(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName()),24);
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
    }
    private static String duration(long millis) {long mins=Math.max(1,millis/60000);return mins>=1440?mins/1440+" 天":mins>=60?mins/60+" 小时":mins+" 分钟";}
    private static String scope(CivicCatalog.Scope scope) {return switch(scope) {case ALL->"镇域内所有玩家";case MEMBERS->"本镇成员";case RESIDENTS->"成员、业主与有效租户";};}
    private static String techEffect(CivicCatalog.Technology t) {return switch(t.effect()) {case LAND_CAPACITY->"每级增加 "+(int)t.value()+" 个区块上限";case POLICY_SLOTS->"每级增加 "+(int)t.value()+" 个政策槽位";case MOVE_DISCOUNT->"每级迁移费减免 "+(int)t.value()+"%";};}
    private static String policyEffects(CivicCatalog.PolicyDef d) {List<String> effects=new ArrayList<>();if(!d.potion().isBlank()) effects.add("药水 "+d.potion()+" "+(d.amplifier()+1));if(d.harvest()>0) effects.add("收获 +"+(int)(d.harvest()*100)+"%");if(d.tradeDiscount()>0) effects.add("交易手续费 −"+(int)(d.tradeDiscount()*100)+"%");if(d.moveDiscount()>0) effects.add("迁移费 −"+(int)(d.moveDiscount()*100)+"%");return String.join(" · ",effects);}
    private String friendly(String message) {String key=message.split(":",2)[0];String raw=plugin.config().messages().get(key);return raw==null?message:net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw))+(message.contains(":")?" · "+message.substring(message.indexOf(':')+1):"");}
    private static String stage(MoveJob.Stage stage) {return switch(stage) {case PREPARED->"等待付款";case APPLYING->"施工中";case REVIEW->"等待验收";case ACCEPTING->"验收提交中";case ACCEPTED->"已验收";case ROLLING_BACK->"正在恢复原址";case ROLLED_BACK->"已撤回";case CANCELLED->"已取消";};}
    private static String clip(String value,int max) {return value.length()>max?value.substring(0,max-1)+"…":value;}
}
