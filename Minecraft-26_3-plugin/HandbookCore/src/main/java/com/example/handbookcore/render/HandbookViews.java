package com.example.handbookcore.render;

import com.example.handbookcore.api.*;
import com.example.handbookcore.hook.CraftEngineBridge;
import com.example.handbookcore.internal.HandbookCatalog;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Dialog 只承载一个可点击正文，书页上的文字、图标与翻页按钮共享同一会话。 */
public final class HandbookViews {
    private static final TextColor LINK = TextColor.color(0x426432);
    private static final int LIST_SIZE = 6;
    private final JavaPlugin plugin;
    private final HandbookCatalog catalog;
    private final CraftEngineBridge ce;
    private final BookText text;
    private final BookPaginator paginator;
    private final BookIcons icons;
    private record Settings(boolean enabled, boolean requirePack, String title, List<String> welcome) { }
    private volatile Settings settings;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private static final class Session {
        final String nonce = UUID.randomUUID().toString().replace("-", "");
        final long revision, created = System.nanoTime();
        final List<Consumer<Player>> actions = new ArrayList<>();
        Session(long revision) { this.revision = revision; }
        ClickEvent action(Consumer<Player> action) {
            int index = actions.size(); actions.add(action);
            return ClickEvent.runCommand("/handbook _nav " + nonce + " " + index);
        }
    }
    public HandbookViews(JavaPlugin plugin, HandbookCatalog catalog, CraftEngineBridge ce) {
        this.plugin = plugin; this.catalog = catalog; this.ce = ce;
        text = new BookText(ce); paginator = new BookPaginator(text, ce); icons = new BookIcons(ce);
        reloadSettings();
    }
    public void reloadSettings() {
        var config = plugin.getConfig();
        settings = new Settings(config.getBoolean("book.enabled", true), config.getBoolean("book.require-pack-confirmation", false),
                config.getString("book.title", "服务器手册"), List.copyOf(config.getStringList("book.welcome")));
    }
    public void run(Player player, Consumer<Player> task) {
        if (!plugin.isEnabled() || !player.isOnline()) return;
        Consumer<Player> checked = p -> {
            if (!p.hasPermission("handbookcore.use")) { p.sendMessage(Component.text("你没有阅读手册的权限。")); return; }
            task.accept(p);
        };
        if (Bukkit.isOwnedByCurrentRegion(player)) checked.accept(player);
        else player.getScheduler().run(plugin, ignored -> { if (player.isOnline()) checked.accept(player); }, null);
    }
    private boolean permitted(Player player, String permission) { return permission.isEmpty() || player.hasPermission(permission); }
    private Session begin(Player player, long revision) {
        Session session = new Session(revision); sessions.put(player.getUniqueId(), session); return session;
    }
    public void forget(UUID player) { sessions.remove(player); }
    public void clear() { sessions.clear(); }
    public int sessions() { return sessions.size(); }
    public boolean usesBook(Player player) {
        if (!settings.enabled() || !ce.bookAvailable()) return false;
        var status = player.getResourcePackStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD || status == PlayerResourcePackStatusEvent.Status.INVALID_URL) return false;
        return !settings.requirePack() || player.hasResourcePack();
    }
    public void navigate(Player player, String nonce, int index) {
        run(player, p -> {
            Session session = sessions.get(p.getUniqueId());
            if (session == null || !session.nonce.equals(nonce) || index < 0 || index >= session.actions.size()) return;
            if (session.revision != catalog.snapshot().revision() || System.nanoTime() - session.created > Duration.ofMinutes(30).toNanos()) {
                p.sendMessage(Component.text("手册内容已更新，请重新选择章节。")); home(p, 0); return;
            }
            session.actions.get(index).accept(p);
        });
    }
    public void home(Player player, int page) { run(player, p -> showHome(p, Math.max(0, page))); }
    private void showHome(Player player, int requested) {
        var snapshot = catalog.snapshot(); var categories = snapshot.categories(permission -> permitted(player, permission));
        int page = Math.min(requested, Math.max(0, (categories.size() - 1) / 4));
        Session session = begin(player, snapshot.revision());
        var visible = categories.subList(page * 4, Math.min(categories.size(), page * 4 + 4));
        if (!usesBook(player)) {
            List<DialogBody> body = new ArrayList<>();
            body.add(DialogBody.plainMessage(Component.text("玩法指南与服务器更新记录"), 360));
            body.add(DialogBody.plainMessage(Component.text("书本外观需要服务器的手册资源包；当前使用文字目录。"), 360));
            body.add(pageCounter(page + 1, Math.max(1, (categories.size() + 3) / 4)));
            List<ActionButton> buttons = new ArrayList<>();
            for (var c : visible) buttons.add(button(c.title(), session, p -> category(p, c.id(), 0)));
            if (page > 0) buttons.add(button("上一页", session, p -> home(p, page - 1)));
            if ((page + 1) * 4 < categories.size()) buttons.add(button("下一页", session, p -> home(p, page + 1)));
            buttons.add(button("搜索", session, this::searchDialog)); buttons.add(button("合上", session, this::close));
            showNative(player, title(), body, buttons); return;
        }
        BookCanvas canvas = new BookCanvas(ce);
        heading(canvas, false, title()); heading(canvas, true, "分类目录");
        var subtitle = label("玩法指南与更新记录", BookCanvas.COLUMN_WIDTH);
        canvas.centered(false, 9, subtitle.component(), subtitle.width());
        List<String> welcome = settings.welcome();
        int row = 12;
        for (String paragraph : welcome) for (var line : text.wrap(text.parse(paragraph), BookCanvas.COLUMN_WIDTH)) {
            if (row > 23) break;
            canvas.left(row, line.component(), line.width()); row += 2;
        }
        for (int i = 0; i < visible.size(); i++) {
            var category = visible.get(i); int x = BookCanvas.RIGHT + (i % 2) * 94; int y = 8 + (i / 2) * 10;
            ClickEvent click = session.action(p -> category(p, category.id(), 0));
            canvas.hit(y, x, 82, 8, click, text.parse(category.description()));
            var icon = icons.resolve(category.icon());
            canvas.put(y + 1, x + (82 - icon.width()) / 2, icon.component().clickEvent(click)
                    .hoverEvent(text.parse(category.description())), icon.width());
            var labels = text.wrap(text.parse(category.title()), 78);
            for (int line = 0; line < Math.min(2, labels.size()); line++) {
                var value = labels.get(line);
                if (line == 1 && labels.size() > 2) {
                    Component remainder = Component.empty();
                    for (int rest = 1; rest < labels.size(); rest++) remainder = remainder.append(labels.get(rest).component());
                    value = text.ellipsize(remainder, 78);
                }
                canvas.put(y + 5 + line * 2, x + (82 - value.width()) / 2, value.component().color(LINK).clickEvent(click)
                        .hoverEvent(text.parse(category.title() + "\n" + category.description())), value.width());
            }
        }
        if (visible.isEmpty()) write(canvas, true, 7, "暂时没有可见章节");
        navigation(canvas, session, p -> home(p, 0));
        bookmarks(player, canvas, session, null, page);
        footer(canvas, session, page + 1, Math.max(1, (categories.size() + 3) / 4),
                page > 0 ? p -> home(p, page - 1) : null,
                (page + 1) * 4 < categories.size() ? p -> home(p, page + 1) : null);
        showBook(player, canvas, session);
    }
    public void category(Player player, NamespacedKey id, int page) {
        run(player, p -> {
            var snapshot = catalog.snapshot(); var owned = snapshot.categories().get(id);
            if (owned == null || !permitted(p, owned.value().permission())) { unavailable(p); return; }
            list(p, owned.value().title(), owned.value().description(), snapshot.entries(id, permission -> permitted(p, permission)),
                    Math.max(0, page), next -> category(p, id, next));
        });
    }
    public void results(Player player, String query, int page) {
        String normalized = query.strip(); if (normalized.length() > 100) normalized = normalized.substring(0, 100);
        final String term = normalized;
        run(player, p -> list(p, "搜索结果", term.isEmpty() ? "请输入关键词" : "关键词：" + term,
                catalog.snapshot().search(term, permission -> permitted(p, permission)), Math.max(0, page),
                next -> results(p, term, next)));
    }
    private void list(Player player, String title, String description, List<HandbookEntry> entries,
                      int requested, java.util.function.IntConsumer changePage) {
        int page = Math.min(requested, Math.max(0, (entries.size() - 1) / LIST_SIZE));
        Session session = begin(player, catalog.snapshot().revision());
        List<HandbookEntry> visible = entries.subList(page * LIST_SIZE, Math.min(entries.size(), page * LIST_SIZE + LIST_SIZE));
        if (!usesBook(player)) {
            List<ActionButton> buttons = new ArrayList<>();
            for (var e : visible) buttons.add(button(e.title(), session, p -> entry(p, e.id(), 0)));
            if (page > 0) buttons.add(button("上一页", session, p -> changePage.accept(page - 1)));
            if ((page + 1) * LIST_SIZE < entries.size()) buttons.add(button("下一页", session, p -> changePage.accept(page + 1)));
            buttons.add(button("目录", session, p -> home(p, 0))); buttons.add(button("搜索", session, this::searchDialog));
            showNative(player, HandbookCatalog.plain(title), List.of(DialogBody.plainMessage(text.parse(description), 360),
                    DialogBody.plainMessage(Component.text(entries.isEmpty() ? "没有找到可阅读的内容" : "选择条目开始阅读")),
                    pageCounter(page + 1, Math.max(1, (entries.size() + LIST_SIZE - 1) / LIST_SIZE))), buttons); return;
        }
        BookCanvas canvas = new BookCanvas(ce); heading(canvas, false, title); heading(canvas, true, "条目目录");
        int row = BookCanvas.CONTENT_ROW;
        for (var line : text.wrap(text.parse(description), BookCanvas.COLUMN_WIDTH)) {
            if (row > 19) break; canvas.left(row, line.component(), line.width()); row += 2;
        }
        write(canvas, false, 23, "共 " + entries.size() + " 个条目");
        for (int i = 0; i < visible.size(); i++) {
            var entry = visible.get(i); var line = label("› " + entry.title(), BookCanvas.COLUMN_WIDTH);
            ClickEvent click = session.action(p -> entry(p, entry.id(), 0));
            int y = 8 + i * 3;
            canvas.hit(y, BookCanvas.RIGHT, BookCanvas.COLUMN_WIDTH, 2, click, text.parse(entry.title() + "\n" + entry.summary()));
            canvas.right(y, line.component().color(LINK).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                    .clickEvent(click).hoverEvent(text.parse(entry.title() + "\n" + entry.summary())), line.width());
        }
        if (visible.isEmpty()) write(canvas, true, 6, "没有找到可读内容");
        navigation(canvas, session, p -> home(p, 0));
        bookmarks(player, canvas, session, entries.isEmpty() ? null : entries.getFirst().categoryId(), 0);
        footer(canvas, session, page + 1, Math.max(1, (entries.size() + LIST_SIZE - 1) / LIST_SIZE), page > 0 ? p -> changePage.accept(page - 1) : null,
                (page + 1) * LIST_SIZE < entries.size() ? p -> changePage.accept(page + 1) : null);
        showBook(player, canvas, session);
    }
    public void entry(Player player, NamespacedKey id, int requested) {
        run(player, p -> {
            var snapshot = catalog.snapshot(); var owned = snapshot.entries().get(id);
            if (owned == null || !snapshot.visible(owned.value(), permission -> permitted(p, permission))) { unavailable(p); return; }
            HandbookEntry entry = owned.value(); Session session = begin(p, snapshot.revision());
            if (!usesBook(p)) { nativeEntry(p, entry, Math.max(0, requested), session); return; }
            List<BookPaginator.Paper> papers = paginator.paginate(entry);
            int spread = Math.min(Math.max(0, requested), (papers.size() - 1) / 2);
            BookCanvas canvas = new BookCanvas(ce);
            for (int side = 0; side < 2; side++) {
                int index = spread * 2 + side; if (index >= papers.size()) continue;
                var paper = papers.get(index); heading(canvas, side == 1, paper.title());
                for (int row = 0; row < paper.lines().size(); row++) {
                    var line = paper.lines().get(row); Component value = line.text();
                    if (line.action() instanceof HandbookBlock.Link link) value = value.color(LINK)
                            .clickEvent(session.action(viewer -> entry(viewer, link.entryId(), 0)));
                    if (line.action() instanceof HandbookBlock.Item item) value = value.color(LINK)
                            .hoverEvent(Component.text("点击查看物品模型与悬停信息"))
                            .clickEvent(session.action(viewer -> preview(viewer, entry, spread, item)));
                    if (line.action() instanceof HandbookBlock.Url url) value = value.color(LINK)
                            .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .hoverEvent(Component.text(url.url())).clickEvent(ClickEvent.openUrl(url.url()));
                    if (line.action() instanceof HandbookBlock.Command command) value = value.color(LINK)
                            .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .hoverEvent(Component.text("执行 /" + command.command()))
                            .clickEvent(session.action(viewer -> execute(viewer, command)));
                    if (line.centered()) canvas.centered(side == 1, BookCanvas.CONTENT_ROW + row * 2, value, line.width());
                    else if (side == 0) canvas.left(BookCanvas.CONTENT_ROW + row * 2, value, line.width());
                    else canvas.right(BookCanvas.CONTENT_ROW + row * 2, value, line.width());
                }
            }
            navigation(canvas, session, viewer -> category(viewer, entry.categoryId(), 0));
            bookmarks(p, canvas, session, entry.categoryId(), 0);
            footer(canvas, session, spread + 1, (papers.size() + 1) / 2,
                    spread > 0 ? viewer -> entry(viewer, id, spread - 1) : null,
                    (spread + 1) * 2 < papers.size() ? viewer -> entry(viewer, id, spread + 1) : null);
            showBook(p, canvas, session);
        });
    }
    private void preview(Player player, HandbookEntry entry, int spread, HandbookBlock.Item item) {
        Session session = begin(player, catalog.snapshot().revision());
        showNative(player, HandbookCatalog.plain(item.caption()), List.of(DialogBody.item(ce.item(item.item(), player)).build(),
                DialogBody.plainMessage(text.parse(item.caption()), 300)), List.of(
                button("返回书页", session, p -> entry(p, entry.id(), spread)), button("合上", session, this::close)));
    }
    private void nativeEntry(Player player, HandbookEntry entry, int requested, Session session) {
        int page = Math.min(requested, entry.pages().size() - 1); var paper = entry.pages().get(page);
        List<DialogBody> body = new ArrayList<>(); List<ActionButton> buttons = new ArrayList<>();
        for (HandbookBlock block : paper.blocks()) switch (block) {
            case HandbookBlock.Text t -> body.add(DialogBody.plainMessage(text.parse(t.miniMessage()), 360));
            case HandbookBlock.Image i -> body.add(DialogBody.plainMessage(Component.text(i.altText()), 360));
            case HandbookBlock.Item i -> {
                body.add(DialogBody.plainMessage(text.parse(i.caption()), 360)); body.add(DialogBody.item(ce.item(i.item(), player)).build());
            }
            case HandbookBlock.Link l -> buttons.add(button(l.label(), session, p -> entry(p, l.entryId(), 0)));
            case HandbookBlock.Url u -> buttons.add(ActionButton.builder(text.parse(u.label())).width(150)
                    .action(DialogAction.staticAction(ClickEvent.openUrl(u.url()))).build());
            case HandbookBlock.Command c -> buttons.add(button(c.label(), session, p -> execute(p, c)));
            case HandbookBlock.Space s -> body.add(DialogBody.plainMessage(Component.text("\n".repeat(s.lines()))));
        }
        if (page > 0) buttons.add(button("上一页", session, p -> entry(p, entry.id(), page - 1)));
        if (page + 1 < entry.pages().size()) buttons.add(button("下一页", session, p -> entry(p, entry.id(), page + 1)));
        buttons.add(button("返回目录", session, p -> category(p, entry.categoryId(), 0)));
        buttons.add(button("合上", session, this::close));
        body.add(pageCounter(page + 1, entry.pages().size()));
        showNative(player, HandbookCatalog.plain(paper.title().isEmpty() ? entry.title() : paper.title()), body, buttons);
    }
    private DialogBody pageCounter(int current, int total) {
        return DialogBody.plainMessage(Component.text("第 " + current + " 页 / 共 " + total + " 页"), 360);
    }
    public void searchDialog(Player player) {
        run(player, p -> {
            ActionButton search = ActionButton.builder(Component.text("搜索"))
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player viewer && viewer.getUniqueId().equals(p.getUniqueId()))
                            results(viewer, Objects.requireNonNullElse(response.getText("query"), ""), 0);
                    }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build())).build();
            p.showDialog(Dialog.create(builder -> builder.empty().base(DialogBase.builder(Component.text("搜索手册"))
                    .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.NONE)
                    .inputs(List.of(DialogInput.text("query", Component.text("搜索内容")).maxLength(100).build())).build())
                    .type(DialogType.multiAction(List.of(search, ActionButton.builder(Component.text("返回目录"))
                            .action(DialogAction.staticAction(ClickEvent.runCommand("/handbook"))).build())).build())));
        });
    }
    private void footer(BookCanvas canvas, Session session, int current, int total,
                         Consumer<Player> previous, Consumer<Player> next) {
        canvas.hit(26, 291, 30, 3, previous == null ? null : session.action(previous), Component.text(previous == null ? "已在首页" : "上一页"));
        canvas.hit(26, 420, 30, 3, next == null ? null : session.action(next), Component.text(next == null ? "已在末页" : "下一页"));
        var counter = text.ellipsize(text.parse(current + " / " + total).font(BookText.BUTTON_FONT), 110);
        canvas.put(27, 365 - counter.width()/2, counter.component()
                .hoverEvent(Component.text("第 " + current + " 页，共 " + total + " 页；每次显示双页")), counter.width());
    }
    private void navigation(BookCanvas canvas, Session session, Consumer<Player> back) {
        bookButton(canvas, 26, 68, "目录", session, back);
        bookButton(canvas, 26, 124, "检索", session, this::searchDialog);
        bookButton(canvas, 26, 180, "合上", session, this::close);
    }
    private void bookButton(BookCanvas canvas, int row, int x, String title, Session session, Consumer<Player> action) {
        ClickEvent click = session.action(action); canvas.hit(row - 1, x, 52, 3, click, Component.text(title));
        var label = text.ellipsize(text.parse(title).font(BookText.BUTTON_FONT), 46);
        canvas.put(row, x + (52-label.width())/2, label.component().color(LINK).clickEvent(click), label.width());
    }
    private void heading(BookCanvas canvas, boolean right, String label) {
        var line = text.ellipsize(text.parse(label).font(BookText.HEADING_FONT), right ? BookCanvas.COLUMN_WIDTH : 138);
        Component value = line.component().color(right ? BookText.HEADING : TextColor.color(0xffdf78))
                .hoverEvent(text.parse(label));
        if (right) canvas.centered(true, 4, value, line.width());
        else canvas.put(5, 143 - line.width()/2, value, line.width());
    }
    private void bookmarks(Player player, BookCanvas canvas, Session session, NamespacedKey selected, int requestedBank) {
        var categories = catalog.snapshot().categories(permission -> permitted(player, permission));
        int bank = requestedBank;
        if (selected != null) for (int i=0; i<categories.size(); i++) if (categories.get(i).id().equals(selected)) { bank=i/4; break; }
        for (int i=bank*4; i<Math.min(categories.size(),bank*4+4); i++) {
            var category=categories.get(i); int position=i%4, row=8+position*5;
            ClickEvent click=session.action(p -> category(p,category.id(),0));
            Component tooltip=text.parse(category.title()+"\n"+category.description());
            canvas.bookmark(row,position,category.id().equals(selected),click,tooltip);
            var icon=icons.resolveSmall(category.icon());
            canvas.put(row,BookCanvas.BOOKMARK_X+(BookCanvas.BOOKMARK_WIDTH-icon.width())/2,
                    icon.component().clickEvent(click).hoverEvent(tooltip),icon.width());
        }
    }
    private void write(BookCanvas canvas, boolean right, int row, String value) {
        var line = label(value, BookCanvas.COLUMN_WIDTH);
        if (right) canvas.right(row, line.component(), line.width()); else canvas.left(row, line.component(), line.width());
    }
    private BookText.Run label(String value, int width) { return text.ellipsize(text.parse(value), width); }
    private ActionButton button(String title, Session session, Consumer<Player> action) {
        return ActionButton.builder(Component.text(HandbookCatalog.plain(title))).width(150)
                .action(DialogAction.staticAction(session.action(action))).build();
    }
    private String title() { return settings.title(); }
    private void showBook(Player player, BookCanvas canvas, Session session) {
        player.showDialog(Dialog.create(builder -> builder.empty().base(DialogBase.builder(Component.empty())
                .externalTitle(Component.text(title())).canCloseWithEscape(true).pause(false)
                // FocusableTextWidget 在正文两侧各留 4px，实际换行宽度需另加 8px。
                .afterAction(DialogBase.DialogAfterAction.NONE).body(List.of(DialogBody.plainMessage(canvas.build(), BookCanvas.DISPLAY_WIDTH + 8)))
                .build()).type(DialogType.notice(button("合上手册", session, this::close)))));
    }
    private void showNative(Player player, String title, List<DialogBody> body, List<ActionButton> buttons) {
        player.showDialog(Dialog.create(builder -> builder.empty().base(DialogBase.builder(Component.text(title))
                .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.NONE).body(body).build())
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }
    private void unavailable(Player player) { player.sendMessage(Component.text("该条目不存在，或你暂时没有阅读权限。")); home(player, 0); }
    private void close(Player player) { forget(player.getUniqueId()); player.closeDialog(); }
    private void execute(Player player, HandbookBlock.Command command) {
        if (command.closeBeforeRun()) close(player);
        player.performCommand(command.command());
    }
}
