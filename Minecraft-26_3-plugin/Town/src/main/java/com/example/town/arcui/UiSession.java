package com.example.town.arcui;

import com.example.town.TownPlugin;
import com.example.town.arcui.ClaimMapScreen;
import com.example.town.arcui.HomeScreen;
import com.fentai.arcmenu.api.ArcMenuActivateEvent;
import com.fentai.arcmenu.api.ArcMenuApplicationCloseEvent;
import com.fentai.arcmenu.api.ArcMenuApplicationContext;
import com.fentai.arcmenu.api.ArcMenuApplicationSession;
import com.fentai.arcmenu.api.ArcMenuPointerButton;
import com.fentai.arcmenu.api.ArcMenuPointerMoveEvent;
import com.fentai.arcmenu.api.ArcMenuSurfaceEvent;
import com.fentai.arcmenu.api.ArcMenuTickEvent;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 一次打开的界面会话：持有渲染器与界面栈，把 ArcMenu 指针事件翻译给当前界面。
 * 界面栈：push 进入下一层，pop 返回上一层，栈空 = 关闭应用。
 */
public final class UiSession extends ArcMenuApplicationSession {

    private final TownPlugin plugin;
    private final ArcMenuApplicationContext ctx;
    private Renderer renderer;
    private final Deque<Screen> stack = new ArrayDeque<>();
    private boolean closed;

    public UiSession(TownPlugin plugin, ArcMenuApplicationContext ctx) {
        this.plugin = plugin;
        this.ctx = ctx;
    }

    public TownPlugin plugin() {
        return plugin;
    }

    public ArcMenuApplicationContext ctx() {
        return ctx;
    }

    public Renderer renderer() {
        return renderer;
    }

    public Screen screen() {
        return stack.peek();
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onSurfaceChanged(ArcMenuSurfaceEvent event) {
        if (renderer == null) {
            renderer = new Renderer(plugin, ctx);
        }
        renderer.attach(event.getSurface());
        if (stack.isEmpty()) {
            open(new HomeScreen(this));
        } else {
            redraw();
        }
    }

    @Override
    public void onActivate(ArcMenuActivateEvent event) {
        Screen current = screen();
        if (current != null) {
            current.activate(event.getPoint().getX(), event.getPoint().getY(),
                    event.getButton(), event.getSneaking());
        }
    }

    @Override
    public void onPointerMove(ArcMenuPointerMoveEvent event) {
        Screen current = screen();
        if (current != null) {
            current.hover(event.getPoint().getX(), event.getPoint().getY());
        }
    }

    @Override
    public void onTick(ArcMenuTickEvent event) {
        Screen current = screen();
        if (current != null) {
            current.tick();
        }
    }

    @Override
    public void onClose(ArcMenuApplicationCloseEvent event) {
        closed = true;
        if (renderer != null) {
            renderer.clear();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------
    // 导航
    // ------------------------------------------------------------------

    /** 进入下一层界面。 */
    public void open(Screen screen) {
        Screen previous = screen();
        stack.push(screen);
        if (previous != null) {
            previous.onLeave();
        }
        redraw();
    }

    /** 返回上一层；栈空则关闭。 */
    public void back() {
        Screen current = stack.poll();
        if (current != null) {
            current.onLeave();
        }
        if (stack.isEmpty()) {
            ctx.close();
            return;
        }
        redraw();
    }

    /** 回到主界面（清空栈，重建主屏）。 */
    public void home() {
        while (!stack.isEmpty()) {
            stack.poll().onLeave();
        }
        open(new HomeScreen(this));
    }

    /** 重绘当前界面。 */
    public void redraw() {
        if (renderer == null) {
            return;
        }
        renderer.clear();
        ctx.execute(() -> {
            Screen current = screen();
            if (current != null && !closed) {
                current.draw();
            }
        });
    }

    /** 关闭整个界面（会话结束）。 */
    public void close() {
        ctx.close();
    }
}
