package tw.yuaner.neoauth.forge.login;

import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forge 登入握手狀態持有器（放置於非 Mixin 套件中，避免 Mixin ClassLoader 攔截拋出 IllegalClassLoadError）。
 */
public class ForgeLoginStateHolder {
    private final ServerboundHelloPacket helloPacket;
    private final AtomicBoolean released = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> watchdogTask;

    public ForgeLoginStateHolder(ServerboundHelloPacket helloPacket) {
        this.helloPacket = helloPacket;
    }

    public ServerboundHelloPacket getHelloPacket() {
        return helloPacket;
    }

    public AtomicBoolean getReleased() {
        return released;
    }

    public ScheduledFuture<?> getWatchdogTask() {
        return watchdogTask;
    }

    public void setWatchdogTask(ScheduledFuture<?> watchdogTask) {
        this.watchdogTask = watchdogTask;
    }

    public void cancelWatchdog() {
        ScheduledFuture<?> task = this.watchdogTask;
        if (task != null) {
            task.cancel(false);
        }
    }
}
