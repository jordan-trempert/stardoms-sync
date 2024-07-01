package stardom.sync;

import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sun.net.httpserver.Request;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Supplier;


public class StardomsSync implements ModInitializer {
	public static final String MOD_ID = "sync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> syncTime(server));
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("realtime")
					.executes(this::executeRealtimeCommand));
		});

	}

	private void syncTime(MinecraftServer server) {
		if (!server.getOverworld().isClient) {
			long realSeconds = LocalTime.now().minusHours(6).toSecondOfDay();
			long minecraftTicks = (realSeconds * 24000) / 86400;
			server.getOverworld().setTimeOfDay(minecraftTicks);
		}
	}

	private int executeRealtimeCommand(CommandContext<ServerCommandSource> context) {
		long minecraftTicks = context.getSource().getWorld().getTimeOfDay();
		long realSeconds = (minecraftTicks * 86400) / 24000;
		LocalTime realTime = LocalTime.ofSecondOfDay(realSeconds).plusHours(6);
		Objects.requireNonNull(context.getSource().getPlayer()).sendMessage(Text.of("Real world time: " + realTime), false);
		return 1;
	}


}