package net.frankheijden.serverutils.velocity.managers;

import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import net.frankheijden.serverutils.common.managers.AbstractTaskManager;
import net.frankheijden.serverutils.velocity.ServerUtils;

public class VelocityTaskManager extends AbstractTaskManager<ScheduledTask> {

    private final ServerUtils plugin;

    public VelocityTaskManager(ServerUtils plugin) {
        super(ScheduledTask::cancel);
        this.plugin = plugin;
    }

    @Override
    protected ScheduledTask runTaskImpl(Runnable runnable) {
        return runTaskAsynchronously(runnable);
    }

    @Override
    protected ScheduledTask runTaskLaterImpl(Runnable runnable, long delay) {
        return plugin.getProxy().getScheduler()
                .buildTask(plugin, runnable)
                .delay(Duration.ofMillis(delay * 50))
                .schedule();
    }

    @Override
    protected ScheduledTask runTaskAsynchronouslyImpl(Runnable runnable) {
        return plugin.getProxy().getScheduler()
                .buildTask(plugin, runnable)
                .schedule();
    }

    @Override
    protected void cancelTaskImpl(ScheduledTask task) {
        task.cancel();
    }
}
