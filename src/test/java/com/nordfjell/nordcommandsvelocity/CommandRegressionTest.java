package com.nordfjell.nordcommandsvelocity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CommandRegressionTest {
    private static int passed;
    private static void check(String name, Runnable test) {
        test.run(); passed++; System.out.println("PASS: " + name);
    }
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == Optional.class) return Optional.empty();
        return null;
    }
    public static void main(String[] args) throws Exception {
        if(!CommandRegressionTest.class.desiredAssertionStatus())throw new IllegalStateException("Run with -ea");
        AtomicBoolean bypass = new AtomicBoolean(), active = new AtomicBoolean(true); AtomicInteger messages = new AtomicInteger();
        Set<String> registered = ConcurrentHashMap.newKeySet(); registered.addAll(Set.of("vunsafe", "va", "test:vunsafe"));
        CommandManager manager = stub(CommandManager.class, (p,m,a) -> m.getName().equals("hasCommand")
                ? registered.contains(((String)a[0]).toLowerCase(Locale.ROOT)) : defaultValue(m.getReturnType()));
        ProxyServer server = stub(ProxyServer.class, (p,m,a) -> m.getName().equals("getCommandManager") ? manager : defaultValue(m.getReturnType()));
        Player player = stub(Player.class, (p,m,a) -> {
            if (m.getName().equals("hasPermission")) return bypass.get();
            if (m.getName().equals("isActive")) return active.get();
            if (m.getName().equals("sendMessage")) { messages.incrementAndGet(); return null; }
            return defaultValue(m.getReturnType());
        });
        NordCommandsVelocityPlugin plugin = new NordCommandsVelocityPlugin(server, LoggerFactory.getLogger("regression"));
        check("Case and leading-space labels match Velocity normalization", () -> {
            assert CommandInput.label("   VuNsAfE args  ").equals("vunsafe");
            assert CommandInput.label("test:vunsafe args").equals("test:vunsafe");
        });
        check("Null, empty, slash and control input rejected", () -> {
            for (String raw : Arrays.asList(null,"","   ","/vunsafe","//vunsafe","vunsafe\targs","safe\nline","safe \u2028x","safe \u0000x")) assert CommandInput.label(raw) == null;
        });
        check("Length and label bounds enforced", () -> {
            assert CommandInput.label("x".repeat(257)) == null;
            assert CommandInput.label("safe " + "x".repeat(32763)) == null;
            assert CommandInput.label("safe " + "x".repeat(32762)).equals("safe");
        });
        check("Backend namespaced label is inspected without rebuilding arguments", () -> {
            assert CommandInput.label("minecraft:msg Name two  spaces ; /op Nobody").equals("minecraft:msg");
        });
        check("Direct proxy command denied at early and final gates", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"vunsafe x"); plugin.onCommand(e);
            assert !e.getResult().isAllowed(); plugin.onFinalCommand(e); assert !e.getResult().isAllowed();
        });
        check("Backend-to-proxy rewritten result denied", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe rewrite"); plugin.onCommand(e);
            e.setResult(CommandExecuteEvent.CommandResult.command("vunsafe changed")); plugin.onFinalCommand(e);
            assert !e.getResult().isAllowed();
        });
        check("Registered alias and namespaced rewrites denied", () -> {
            for(String target : List.of("va changed","test:vunsafe changed","  VuNsAfE changed")) {
                CommandExecuteEvent e = new CommandExecuteEvent(player,"safe");
                e.setResult(CommandExecuteEvent.CommandResult.command(target)); plugin.onFinalCommand(e); assert !e.getResult().isAllowed();
            }
        });
        check("Later re-allow cannot resurrect original proxy root", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"vunsafe"); plugin.onCommand(e);
            e.setResult(CommandExecuteEvent.CommandResult.command("safe changed")); plugin.onFinalCommand(e); assert !e.getResult().isAllowed();
        });
        check("Backend command and exact arguments untouched", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe two  spaces ; /op Nobody");
            Object before=e.getResult(); plugin.onCommand(e); plugin.onFinalCommand(e); assert before == e.getResult();
        });
        check("Allowed backend rewrite result object preserved", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe");
            var result=CommandExecuteEvent.CommandResult.command("msg Name two  spaces"); e.setResult(result);
            plugin.onFinalCommand(e); assert e.getResult() == result;
        });
        check("Explicit backend forwarding result object preserved", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe");
            var result=CommandExecuteEvent.CommandResult.forwardToServer("msg Name payload"); e.setResult(result);
            plugin.onFinalCommand(e); assert e.getResult() == result;
        });
        check("Existing denial is never cancelled for a backend root", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe");
            var result=CommandExecuteEvent.CommandResult.denied(); e.setResult(result);
            plugin.onCommand(e); plugin.onFinalCommand(e); assert e.getResult()==result;
        });
        check("Malformed replacement fails closed", () -> {
            CommandExecuteEvent e = new CommandExecuteEvent(player,"safe");
            e.setResult(CommandExecuteEvent.CommandResult.command("safe\nline")); plugin.onFinalCommand(e);
            assert !e.getResult().isAllowed();
        });
        check("Console source is not filtered", () -> {
            CommandSource console = stub(CommandSource.class, (p,m,a) -> defaultValue(m.getReturnType()));
            CommandExecuteEvent e=new CommandExecuteEvent(console,"vunsafe"); plugin.onCommand(e); plugin.onFinalCommand(e);
            assert e.getResult().isAllowed();
        });
        check("Bypass checked at execution and revocation immediately effective", () -> {
            bypass.set(true); var e=new CommandExecuteEvent(player,"vunsafe"); plugin.onCommand(e); plugin.onFinalCommand(e); assert e.getResult().isAllowed();
            bypass.set(false); var revoked=new CommandExecuteEvent(player,"vunsafe"); plugin.onCommand(revoked); plugin.onFinalCommand(revoked); assert !revoked.getResult().isAllowed();
        });
        check("Dynamic proxy command registrations checked without stale cache", () -> {
            var e=new CommandExecuteEvent(player,"dynamic"); plugin.onFinalCommand(e); assert e.getResult().isAllowed();
            registered.add("dynamic"); var added=new CommandExecuteEvent(player,"dynamic"); plugin.onFinalCommand(added); assert !added.getResult().isAllowed();
            registered.remove("dynamic"); var removed=new CommandExecuteEvent(player,"dynamic"); plugin.onFinalCommand(removed); assert removed.getResult().isAllowed();
        });
        check("Available tree removes only proxy-owned roots, preserving backend", () -> {
            RootCommandNode<CommandSource> root=new RootCommandNode<>();
            for(String name:List.of("safe","msg","vunsafe","va","test:vunsafe")) root.addChild(LiteralArgumentBuilder.<CommandSource>literal(name).build());
            plugin.onAvailableCommands(new PlayerAvailableCommandsEvent(player,root));
            assert root.getChildren().stream().map(n->n.getName()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("safe","msg"));
        });
        check("Explicit tree bypass retained", () -> {
            bypass.set(true); RootCommandNode<CommandSource> root=new RootCommandNode<>(); root.addChild(LiteralArgumentBuilder.<CommandSource>literal("vunsafe").build());
            plugin.onAvailableCommands(new PlayerAvailableCommandsEvent(player,root)); assert root.getChild("vunsafe")!=null; bypass.set(false);
        });
        check("Priority annotations bracket normal handlers", () -> {
            try {
                assert NordCommandsVelocityPlugin.class.getMethod("onCommand",CommandExecuteEvent.class).getAnnotation(Subscribe.class).priority()==Short.MAX_VALUE;
                assert NordCommandsVelocityPlugin.class.getMethod("onFinalCommand",CommandExecuteEvent.class).getAnnotation(Subscribe.class).priority()==Short.MIN_VALUE;
            } catch(Exception e){throw new RuntimeException(e);}
        });
        check("Fast handlers do not force asynchronous dispatch", () -> {
            for(var method:NordCommandsVelocityPlugin.class.getDeclaredMethods()) {
                Subscribe annotation=method.getAnnotation(Subscribe.class);
                if(annotation!=null)assert !annotation.async();
            }
        });
        check("Notice gate uses monotonic interval and session identity", () -> {
            NoticeGate gate=new NoticeGate(); Object a=new String("same"),b=new String("same");
            assert gate.admit(a,1000); assert !gate.admit(a,1001); assert gate.admit(b,1001);
            assert gate.admit(a,1000+NoticeGate.INTERVAL_NANOS); assert gate.size()==2;
        });
        check("Notice capacity never grows on overflow; cleanup admits a new session", () -> {
            NoticeGate gate=new NoticeGate(); Object[] sessions=new Object[NoticeGate.CAPACITY];
            for(int i=0;i<sessions.length;i++){sessions[i]=new Object();assert gate.admit(sessions[i],0);}
            assert !gate.admit(new Object(),0); assert gate.size()==NoticeGate.CAPACITY;
            gate.remove(sessions[0]); assert gate.admit(new Object(),1); assert gate.size()==NoticeGate.CAPACITY;
            gate.close(); assert gate.size()==0; assert !gate.admit(new Object(),2);
        });
        check("Concurrent session notices cannot exceed cap", () -> {
            NoticeGate gate=new NoticeGate(); List<Thread> threads=new ArrayList<>();
            for(int i=0;i<8;i++){Thread t=new Thread(()->{for(int j=0;j<1000;j++)gate.admit(new Object(),0);});threads.add(t);t.start();}
            for(Thread t:threads)try{t.join();}catch(InterruptedException e){throw new RuntimeException(e);}
            assert gate.size()==NoticeGate.CAPACITY;
        });
        check("Repeated denial sends bounded notices without granting execution", () -> {
            int before=messages.get(); long began=System.nanoTime();
            for(int i=0;i<1000;i++) {var e=new CommandExecuteEvent(player,"vunsafe");plugin.onCommand(e);plugin.onFinalCommand(e);assert !e.getResult().isAllowed();}
            assert messages.get()-before<=1+(System.nanoTime()-began)/NoticeGate.INTERVAL_NANOS;
        });
        check("Disconnect clears session notice state", () -> {
            plugin.onDisconnect(new DisconnectEvent(player,DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
            int before=messages.get(); var e=new CommandExecuteEvent(player,"vunsafe");plugin.onFinalCommand(e);assert messages.get()==before+1;
        });
        check("Inactive player still denied without outbound notice", () -> {
            plugin.onDisconnect(new DisconnectEvent(player,DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
            active.set(false);int before=messages.get();var e=new CommandExecuteEvent(player,"vunsafe");plugin.onFinalCommand(e);
            assert !e.getResult().isAllowed();assert messages.get()==before;active.set(true);
        });
        System.out.println("UNIT_CHECKS_PASSED="+passed);
        if(args.length==1)java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
                "{\"passed\":true,\"scenarios\":"+passed+",\"assertionsEnabled\":true}\n");
    }
}
