package com.nordfjell.nordcommandsvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(
        id = "nordcommands",
        name = "NordCommands",
        version = "1.1.1",
        description = "Bounded Velocity command filter for Nord Fjell",
        authors = {"Nord Fjell"}
)
public final class NordCommandsVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final NoticeGate notices = new NoticeGate();
    private static final Component DENIED = Component.text("No such command.");

    @Inject
    public NordCommandsVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe(async = false)
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("NordCommands 1.1.0 enabled: early/final proxy-command gates, bounded session notices.");
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (canBypass(event.getPlayer())) {
            return;
        }
        event.getRootNode().getChildren().removeIf(node ->
                proxy.getCommandManager().hasCommand(node.getName()));
    }

    @Subscribe(priority = Short.MAX_VALUE, async = false)
    public void onCommand(CommandExecuteEvent event) {
        filter(event, false);
    }

    // Larger Velocity priorities run FIRST. Inspect the final replacement LAST.
    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onFinalCommand(CommandExecuteEvent event) {
        filter(event, true);
    }

    private void filter(CommandExecuteEvent event, boolean notify) {
        if (!(event.getCommandSource() instanceof Player player) || canBypass(player)) {
            return;
        }
        String originalText = event.getCommand();
        String original = CommandInput.label(originalText);
        String effectiveText = event.getResult().getCommand().orElse(originalText);
        String effective = effectiveText == originalText ? original : CommandInput.label(effectiveText);
        if (original == null || effective == null
                || proxy.getCommandManager().hasCommand(original)
                || proxy.getCommandManager().hasCommand(effective)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            if (notify && player.isActive() && notices.admit(player, System.nanoTime())) player.sendMessage(DENIED);
        }
        // Never change an existing denial or force forwarding for backend commands.
        // Later trusted plugins at the same MIN priority can still modify the result.
    }

    private boolean canBypass(Player player) {
        return player.hasPermission("nordcommands.bypass");
    }

    @Subscribe(async = false) public void onDisconnect(DisconnectEvent event) {
        notices.remove(event.getPlayer());
    }

    @Subscribe(async = false) public void onShutdown(ProxyShutdownEvent event) {
        notices.close();
    }
}
