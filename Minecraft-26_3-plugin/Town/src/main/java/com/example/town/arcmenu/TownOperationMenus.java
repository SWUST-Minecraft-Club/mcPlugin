package com.example.town.arcmenu;

import java.util.*;
import static com.example.town.arcmenu.TownMenus.*;
import static com.example.town.arcmenu.MenuBuilder.Align.*;

/** 经营与规划页面共享同一组卡片、间距和动作入口。 */
public final class TownOperationMenus {
    public static final String PREFIX="town-op-";
    private static final Set<String> SCREENS=Set.of("civic","catalog","plots","plot","plot-form","planning","jobs","job","market","product","market-form","mail","approvals","confirm");
    static boolean contains(String screen) {return SCREENS.contains(screen);}
    private TownOperationMenus() { }
    private static void text(MenuBuilder m,String id,String value,double x,double y,int size) {m.text(id,t(TEXT,value),x,y,size,5,CENTER,dyn());}
    private static void go(MenuBuilder m,String id,int x,int y,int w,String label,String action,String color) {button(m,id,x,y,w,32,color,label,"command: town manage "+action);}
    private static MenuBuilder page(String title,String hint) {return chrome(title,hint,true,true);}
    private static void pages(MenuBuilder m) {
        go(m,"prev",20,416,84,"上一页","page -1",RAISE);go(m,"next",114,416,84,"下一页","page 1",RAISE);
        text(m,"pages","{meta:op_page}",280,431,10);
    }
    private static MenuBuilder rows(String title,String hint,String action) {
        MenuBuilder m=page(title,hint);
        for(int i=0;i<6;i++) {
            int y=72+i*48;card(m,"row-"+i,20,y,760,42);
            text(m,"row-name-"+i,"{meta:op_row_"+i+"_name}",330,y+13,13);
            text(m,"row-info-"+i,"{meta:op_row_"+i+"_info}",330,y+31,10);
            button(m,"row-go-"+i,650,y+5,116,32,RAISE,"{meta:op_row_"+i+"_action}",
                    MenuBuilder.conditionSpec("{meta:op_row_"+i+"_present} == 1",null,MenuBuilder.click("all","command: town manage "+action+" "+i)));
        }
        pages(m);return m;
    }
    private static MenuBuilder civic() {
        MenuBuilder m=page("镇库与城镇发展","镇库支付科技和政策 · 玩家可自由贡献 · 受益范围在购买前显示");
        card(m,"summary",20,70,760,76);
        m.item("treasury-icon","emerald",56,108,28,4);
        text(m,"town","{meta:town_name}",232,96,18);
        text(m,"slots","{meta:op_slots}",232,125,10);
        m.rect("treasury-well",460,80,304,56,JADE,2);
        m.text("treasury-label",t(MUTE,"公共镇库"),476,92,10,5,LEFT,null);
        m.text("treasury-currency",t(MUTE,"{meta:op_treasury_currency}"),716,92,10,5,CENTER,dyn());
        m.text("balance",t(GREEN,"{meta:op_treasury}"),612,118,21,5,CENTER,dyn());
        m.region("treasury-info",460,80,304,56,MenuBuilder.spec(MenuBuilder.tooltip("&f{meta:op_treasury_full}","&7镇库用于小镇公共发展"),null));
        go(m,"donate",20,158,174,"贡献镇库","input donate",GREEN);go(m,"withdraw",210,158,174,"提取镇库","input withdraw",RAISE);
        go(m,"tech",400,158,182,"永久科技","catalog technology",RAISE);go(m,"policy",598,158,182,"限时政策","catalog policy",RAISE);
        card(m,"ledger",20,206,760,184);text(m,"ledger-title","{meta:op_ledger_title}",400,226,13);
        for(int i=0;i<6;i++) text(m,"ledger-"+i,"{meta:op_line_"+i+"}",400,250+i*22,10);
        go(m,"contributions",354,416,138,"贡献排行","ledger contributions",RAISE);go(m,"transactions",506,416,138,"最近流水","ledger entries",RAISE);return m;
    }
    private static MenuBuilder catalog() {return rows("发展采购目录","永久科技提升城镇能力 · 限时政策按配置受益并限制叠加","catalog-pick");}
    private static MenuBuilder plots() {
        MenuBuilder m=rows("地块与租赁","XZ 矩形覆盖全部高度 · 租期内按租户授权保护","plot-pick");
        go(m,"new",20,368,232,"划分新地块","plot-new",GREEN);go(m,"tool",266,368,230,"领取测绘工具","tool",RAISE);
        go(m,"approvals",510,368,270,"边界修改审批","approvals",RAISE);return m;
    }
    private static MenuBuilder plot() {
        MenuBuilder m=page("地块经营","产权、租约和授权随地块保存 · 商铺迁址请使用建筑迁移服务");
        card(m,"plot-summary",20,66,760,146);
        text(m,"name","{meta:op_plot_name}",400,89,20);
        for(int i=0;i<4;i++) text(m,"line-"+i,"{meta:op_line_"+i+"}",400,122+i*23,11);
        String[] labels={"调整边界","修改名称","修改用途","访客交互","授权玩家","取消授权","转让产权","挂牌出租","取消挂牌","承租地块","关联建筑迁移","撤销地块"};
        String[] actions={"plot-edit","input plot-name","plot-kind","plot-public","input plot-trust","input plot-untrust","input plot-transfer","input plot-offer","plot-unoffer","plot-rent","plot-move","plot-remove"};
        for(int i=0;i<labels.length;i++) go(m,"plot-action-"+i,20+(i%4)*194,228+(i/4)*54,178,labels[i],actions[i],i==9?GREEN:i==11?RED:RAISE);
        return m;
    }
    private static MenuBuilder plotForm() {
        MenuBuilder m=page("矩形地块规划","持测绘工具左键、右键选择两角 · 修改有租户的地块时保留审批");
        card(m,"selection",20,66,760,104);text(m,"selection-title","{meta:op_selection}",400,94,14);text(m,"selection-info","{meta:op_selection_detail}",400,132,11);
        go(m,"name",20,186,244,"名称：{meta:op_form_name}","input form-name",RAISE);
        go(m,"kind",278,186,244,"用途：{meta:op_form_kind}","form-kind",RAISE);
        go(m,"owner",536,186,244,"业主：{meta:op_form_owner}","input form-owner",RAISE);
        card(m,"proposal",20,236,760,96);text(m,"proposal-old","{meta:op_old_bounds}",400,260,11);text(m,"proposal-new","{meta:op_new_bounds}",400,289,11);text(m,"proposal-state","{meta:op_proposal}",400,314,10);
        go(m,"tool",20,352,232,"领取工具并关闭","tool",RAISE);go(m,"preview",266,352,244,"预览边界修改","plot-preview",RAISE);
        go(m,"commit",524,352,256,"{meta:op_plot_submit}","plot-submit",GREEN);return m;
    }
    private static MenuBuilder planning() {
        MenuBuilder m=page("建筑迁移规划","金色为原址 · 绿色为可用目标 · 红色表示冲突 · 调整后重新报价和审批");
        card(m,"details",20,66,760,94);text(m,"source","{meta:op_move_source}",400,85,12);text(m,"target","{meta:op_move_target}",400,111,12);text(m,"problem","{meta:op_move_problem}",400,141,11);
        go(m,"begin",20,176,176,"建立迁移草案","move-begin",GREEN);go(m,"target-input",20,218,176,"输入目标坐标","input move-target",RAISE);
        go(m,"hall",20,260,176,"{meta:op_hall_mode}","move-hall",RAISE);go(m,"tool",20,302,176,"测绘三维选区","tool",RAISE);
        go(m,"north",308,176,100,"北 -Z","shift 0 0 -1",RAISE);go(m,"west",202,218,100,"西 -X","shift -1 0 0",RAISE);
        go(m,"step",308,218,100,"步长 {meta:op_step}","step",GREEN);go(m,"east",414,218,100,"东 +X","shift 1 0 0",RAISE);
        go(m,"south",308,260,100,"南 +Z","shift 0 0 1",RAISE);go(m,"up",534,176,112,"上 +Y","shift 0 1 0",RAISE);go(m,"down",658,176,122,"下 -Y","shift 0 -1 0",RAISE);
        go(m,"rotate",534,218,112,"旋转 90°","rotate",RAISE);go(m,"ground",658,218,122,"贴地","ground",RAISE);
        go(m,"see",534,260,246,"关闭菜单查看建筑投影","world-view",RAISE);go(m,"jobs",534,302,246,"工程与验收","jobs",RAISE);
        text(m,"linked","{meta:op_move_linked}",358,316,10);
        card(m,"quote-card",20,350,360,44);text(m,"quote","{meta:op_move_quote}",200,373,14);
        go(m,"cancel",400,356,128,"取消草案","move-cancel",RAISE);go(m,"confirm",544,356,236,"确认报价并施工","move-confirm",GREEN);return m;
    }
    private static MenuBuilder jobs() {return rows("工程与验收","工程完成后可进入查看 · 验收前容器与建造保持锁定","job-pick");}
    private static MenuBuilder job() {
        MenuBuilder m=page("迁移工程验收","验收会提交政厅、地块和商铺坐标 · 撤回会恢复原址并退还迁移费");
        card(m,"job",20,66,760,242);text(m,"job-stage","{meta:op_job_stage}",400,96,20);
        for(int i=0;i<6;i++) text(m,"job-line-"+i,"{meta:op_line_"+i+"}",400,135+i*27,11);
        go(m,"accept",20,340,240,"验收工程","job-accept",GREEN);go(m,"undo",280,340,240,"撤回并恢复原址","job-undo",RAISE);go(m,"retry",540,340,240,"重试暂停的工程","job-retry",RAISE);return m;
    }
    private static MenuBuilder market() {
        MenuBuilder m=rows("小镇商业街","现货托管 · 收购单预付 · 卖家离线自动结算 · 商品送入收件箱","product-pick");
        go(m,"stock",20,368,232,"开设现货商铺","market-new stock",GREEN);go(m,"order",266,368,232,"发布预付收购单","market-new order",RAISE);
        go(m,"mail",512,368,268,"我的收件箱","mail",RAISE);return m;
    }
    private static MenuBuilder product() {
        MenuBuilder m=page("商品与订单详情","按显示的单价和库存确认交易 · 相同商品数据才可供应订单");
        card(m,"product",20,66,760,186);text(m,"name","{meta:op_product_name}",400,96,20);
        for(int i=0;i<5;i++) text(m,"info-"+i,"{meta:op_line_"+i+"}",400,130+i*24,11);
        go(m,"less",154,274,80,"−1","quantity -1",RAISE);go(m,"quantity",246,274,308,"数量 {meta:op_quantity}","input quantity",RAISE);go(m,"more",566,274,80,"+1","quantity 1",RAISE);
        text(m,"total","{meta:op_total}",400,331,16);
        go(m,"trade",20,360,360,"{meta:op_trade_action}","product-trade",GREEN);go(m,"close-listing",400,360,380,"下架我的商铺／订单","product-close",RAISE);return m;
    }
    private static MenuBuilder marketForm() {
        MenuBuilder m=page("开设商铺／发布订单","请站在已授权商业地块内，并把商品样品放在主手");
        card(m,"preview",20,66,760,112);text(m,"mode","{meta:op_market_mode}",400,96,20);text(m,"sample","{meta:op_sample}",400,136,12);
        go(m,"qty",20,204,370,"数量：{meta:op_quantity}","input quantity",RAISE);go(m,"price",410,204,370,"单价：{meta:op_unit}","input unit",RAISE);
        card(m,"summary",20,256,760,70);text(m,"total","{meta:op_total}",400,281,16);text(m,"escrow","{meta:op_escrow_hint}",400,310,11);
        go(m,"publish",200,354,400,"预览并确认发布","market-publish",GREEN);return m;
    }
    private static MenuBuilder mail() {return rows("我的收件箱","离线收货也会保存 · 点击领取一组，可从商品详情按需购买","mail-pick");}
    private static MenuBuilder approvals() {return rows("我的待办审批","核对原址、目标和受影响范围 · 修改方案后原有迁移同意失效","approval-pick");}
    private static MenuBuilder confirmOperation() {
        MenuBuilder m=page("确认经营与规划操作","核对本次费用与变更后提交");
        card(m,"confirm",72,76,656,250);text(m,"title-op","{meta:op_confirm_title}",400,119,22);
        for(int i=0;i<4;i++) text(m,"confirm-"+i,"{meta:op_line_"+i+"}",400,171+i*33,12);
        go(m,"back-op",112,350,264,"返回检查","back",RAISE);
        button(m,"submit-op",424,350,264,40,GREEN,"确认提交","command: town manage commit {meta:op_confirm_token}");return m;
    }
    public static Map<String,MenuBuilder> all() {
        Map<String,MenuBuilder> m=new LinkedHashMap<>();
        m.put(PREFIX+"civic",civic());m.put(PREFIX+"catalog",catalog());m.put(PREFIX+"plots",plots());m.put(PREFIX+"plot",plot());m.put(PREFIX+"plot-form",plotForm());
        m.put(PREFIX+"planning",planning());m.put(PREFIX+"jobs",jobs());m.put(PREFIX+"job",job());m.put(PREFIX+"market",market());m.put(PREFIX+"product",product());
        m.put(PREFIX+"market-form",marketForm());m.put(PREFIX+"mail",mail());m.put(PREFIX+"approvals",approvals());m.put(PREFIX+"confirm",confirmOperation());return m;
    }
}
