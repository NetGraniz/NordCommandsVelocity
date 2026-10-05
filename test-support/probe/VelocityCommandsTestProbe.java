package com.nordfjell.nordcommandsvelocity.test;

import com.google.inject.Inject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.*;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.*;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.permission.*;
import com.velocitypowered.api.proxy.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** LOCAL fixture only: all executed targets increment counters, no admin operations. */
public final class VelocityCommandsTestProbe {
    private final ProxyServer proxy;
    private final Logger logger;
    private final AtomicInteger executions = new AtomicInteger();
    private final AtomicInteger guarded = new AtomicInteger();
    private final Map<UUID,Map<String,Boolean>> permissions = new ConcurrentHashMap<>();
    private volatile String raw = "";
    @Inject public VelocityCommandsTestProbe(ProxyServer proxy, Logger logger) {this.proxy=proxy;this.logger=logger;}
    @Subscribe public void initialize(ProxyInitializeEvent event) {
        RawCommand counter=new RawCommand(){
            public void execute(Invocation i){executions.incrementAndGet();raw=i.arguments();i.source().sendMessage(Component.text("VUNSAFE_EXECUTED "+raw));}
            public List<String> suggest(Invocation i){return List.of("SYNTHETIC_PUBLIC_SUGGESTION");}
        };
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("vunsafe").aliases("va","test:vunsafe","vlate").plugin(this).build(),counter);
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("vguarded").plugin(this).build(),new RawCommand(){
            public boolean hasPermission(Invocation i){return i.source().hasPermission("vtest.guarded");}
            public void execute(Invocation i){if(!hasPermission(i)){i.source().sendMessage(Component.text("VGUARDED_DENIED"));return;}guarded.incrementAndGet();i.source().sendMessage(Component.text("VGUARDED_EXECUTED"));}
        });
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("vtcontrol").plugin(this).build(),new SimpleCommand(){
            public boolean hasPermission(Invocation i){return !(i.source() instanceof Player);}
            public void execute(Invocation i){if(i.source() instanceof Player)return;control(i.arguments());}
        });
        logger.info("VPROBE_READY");
    }
    @Subscribe public void permission(PermissionsSetupEvent event) {
        if(!(event.getSubject() instanceof Player player))return;
        PermissionFunction original=event.createFunction(player);
        event.setProvider(subject->permission->{
            Boolean override=permissions.getOrDefault(player.getUniqueId(),Map.of()).get(permission);
            return override==null?original.getPermissionValue(permission):Tristate.fromBoolean(override);
        });
    }
    @Subscribe(priority=0) public void rewrite(CommandExecuteEvent event) {
        if(!(event.getCommandSource() instanceof Player))return;
        switch(event.getCommand()) {
            case "safe proxyrewrite" -> event.setResult(CommandExecuteEvent.CommandResult.command("vunsafe REWRITTEN_SYNTHETIC"));
            case "safe proxyalias" -> event.setResult(CommandExecuteEvent.CommandResult.command("va ALIAS_SYNTHETIC"));
            case "safe proxynamespace" -> event.setResult(CommandExecuteEvent.CommandResult.command("test:vunsafe NAMESPACE_SYNTHETIC"));
            case "safe proxycase" -> event.setResult(CommandExecuteEvent.CommandResult.command("   VuNsAfE CASE_SYNTHETIC"));
            case "safe proxyforward" -> event.setResult(CommandExecuteEvent.CommandResult.forwardToServer("vunsafe FORWARD_SYNTHETIC"));
            case "safe backendforward" -> event.setResult(CommandExecuteEvent.CommandResult.forwardToServer("msg VTBeta BACKEND_FORWARD"));
            case "safe backendrewrite" -> event.setResult(CommandExecuteEvent.CommandResult.command("msg VTBeta BACKEND_REWRITE"));
            case "safe proxydeny" -> {event.setResult(CommandExecuteEvent.CommandResult.denied());logger.info("VPROBE_BACKEND_DENIED");}
            case "vunsafe resurrect" -> event.setResult(CommandExecuteEvent.CommandResult.allowed());
        }
    }
    @Subscribe(priority=0) public void lateTree(PlayerAvailableCommandsEvent event) {
        addLateRoot(event.getRootNode());
    }
    private static <T> void addLateRoot(com.mojang.brigadier.tree.RootCommandNode<T> root) {
        root.addChild(LiteralArgumentBuilder.<T>literal("vlate").build());
    }
    private static String decode(String value){return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private int notices() throws Exception {
        Object target=proxy.getPluginManager().getPlugin("nordcommands").orElseThrow().getInstance().orElseThrow();
        try {
            Field field=target.getClass().getDeclaredField("notices");field.setAccessible(true);Object gate=field.get(target);
            Method size=gate.getClass().getDeclaredMethod("size");size.setAccessible(true);return (int)size.invoke(gate);
        } catch(NoSuchFieldException old){return -1;}
    }
    private void control(String[] args) {
        String operation=String.join(" ",args);
        try {
            switch(args[0]) {
                case "state" -> logger.info("VSTATE {} executions={} guarded={} notices={} raw={}",args[1],executions.get(),guarded.get(),notices(),encode(raw));
                case "permission" -> {
                    Player player=proxy.getPlayer(args[1]).orElseThrow();
                    permissions.computeIfAbsent(player.getUniqueId(),ignored->new ConcurrentHashMap<>()).put(args[2],Boolean.parseBoolean(args[3]));
                }
                case "register" -> proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("vdynamic").plugin(this).build(),new RawCommand(){
                    public void execute(Invocation i){executions.incrementAndGet();i.source().sendMessage(Component.text("VDYNAMIC_EXECUTED"));}
                });
                case "unregister" -> proxy.getCommandManager().unregister("vdynamic");
                case "api" -> {
                    Player player=proxy.getPlayer(args[1]).orElseThrow();
                    proxy.getCommandManager().executeAsync(player,decode(args[3])).get(10,TimeUnit.SECONDS);
                    logger.info("VAPI_DONE {}",args[2]);
                }
                case "burst" -> {
                    Player player=proxy.getPlayer(args[1]).orElseThrow();int mark=executions.get();
                    for(int i=0;i<1000;i++)proxy.getCommandManager().executeAsync(player,"vunsafe burst").get(10,TimeUnit.SECONDS);
                    logger.info("VBURST executions={} notices={}",executions.get()-mark,notices());
                }
                case "offer" -> {
                    Player player=proxy.getPlayer(args[1]).orElseThrow();
                    var offered=proxy.getCommandManager().offerSuggestions(player,"vunsafe ").get(10,TimeUnit.SECONDS);
                    logger.info("VOFFER {} count={} synthetic={}",args[2],offered.size(),offered.contains("SYNTHETIC_PUBLIC_SUGGESTION"));
                }
            }
            logger.info("VTEST_OK {}",operation);
        } catch(Exception error){logger.error("VTEST_FAILED {}",operation,error);}
    }
}
