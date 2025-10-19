package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.events.CoreEvent;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.gpu.Renderer;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.ui.UITemplate;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.events.EnemyCountEvent;
import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.systems.rendering.passes.ui.UIQuadPass;
import com.controllerface.trongle.systems.rendering.passes.ui.UITextPass;
import org.w3c.dom.Node;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class HUDRenderer extends Renderer<Component>
{
    private final Queue<Event> event_queue = new LinkedBlockingQueue<>();

    private final UIQuadPass ui_quad_pass;
    private final UITextPass ui_text_pass;
    private final Window window;
    private final UITemplate ui_template;

    private boolean dirty;

    private final Node drone_count;
    private final Node ufo_count;

    public HUDRenderer(ECS<Component> _ecs)
    {
        super(_ecs);
        this.window = Component.MainWindow.global(ecs);

        var event_bus = Component.Events.<EventBus>global(ecs);
        event_bus.register(event_queue, CoreEvent.WINDOW_RESIZE, GameEvent.ENEMY_COUNT_CHANGED);

        ui_template = new UITemplate("/ui/ui.html", "/ui/ui.css");
        ui_template.try_rebuild(window.width(), window.height());

        ui_quad_pass = new UIQuadPass(ecs, ui_template);
        ui_text_pass = new UITextPass(ecs, ui_template);

        add_pass(ui_quad_pass);
        add_pass(ui_text_pass);

        drone_count = Optional.ofNullable(ui_template.document().getElementById("rem_drone"))
            .orElseThrow(()->new RuntimeException("unable to locate drone count label"));

        ufo_count = Optional.ofNullable(ui_template.document().getElementById("rem_ufo"))
            .orElseThrow(()->new RuntimeException("unable to locate ufo count label"));
    }

    @Override
    public void destroy()
    {
        super.destroy();
    }

    @Override
    public void render()
    {
        Event next_event;
        while ((next_event = event_queue.poll()) != null)
        {
            if (next_event.type() == CoreEvent.WINDOW_RESIZE)
            {
                dirty = true;
            }
            if (next_event instanceof EnemyCountEvent countEvent)
            {
                switch (countEvent.enemy_type())
                {
                    case GENERAL -> {}
                    case TANKER -> {}
                    case DRONE -> drone_count.setTextContent(String.valueOf(countEvent.count()));
                    case UFO -> ufo_count.setTextContent(String.valueOf(countEvent.count()));
                }
                dirty = true;
            }
        }

        if (dirty)
        {
            ui_template.try_rebuild(window.width(), window.height());
            ui_quad_pass.mark_rebuild();
            ui_text_pass.mark_rebuild();
            dirty = false;
        }

        super.render();
    }
}
