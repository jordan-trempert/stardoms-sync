package stardom.sync;

import com.github.prominence.openweathermap.api.enums.WeatherCondition;
import com.github.prominence.openweathermap.api.model.weather.Rain;
import com.github.prominence.openweathermap.api.model.weather.Weather;
import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sun.net.httpserver.Request;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.github.prominence.openweathermap.api.OpenWeatherMapClient;


public class StardomsSync implements ModInitializer {
	public static final String MOD_ID = "sync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final OpenWeatherMapClient openWeatherClient = new OpenWeatherMapClient("41a84f3cdddc172f98411e2d746e3e51");
	public int weatherTickCounter = 0;
	public static final GameRules.Key<GameRules.BooleanRule> SYNC_TIME = GameRuleRegistry.register("shouldSyncTime", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));
	public static final GameRules.Key<GameRules.BooleanRule> SYNC_WEATHER = GameRuleRegistry.register("shouldSyncWeather", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));


	@Override
	public void onInitialize() {



		ServerTickEvents.END_SERVER_TICK.register(this::syncTime);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++weatherTickCounter >= 1200) {
				syncWeather(server); // Only call every 1200 ticks
				weatherTickCounter = 0;
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("realtime")
					.executes(this::executeRealtimeCommand));
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("syncweather")
					.executes(this::executeSyncWeatherCommand));
		});
	}
	private void syncTime(MinecraftServer server) {
		if (!server.getOverworld().isClient && server.getOverworld().getGameRules().getBoolean(SYNC_TIME)) {
			long realSeconds = LocalTime.now().minusHours(6).toSecondOfDay();
			long minecraftTicks = (realSeconds * 24000) / 86400;
			server.getOverworld().setTimeOfDay(minecraftTicks);
		}
	}

	public boolean isRaining(String zipcode, String countryCode) {
		Weather weather = openWeatherClient
				.currentWeather()
				.single()
				.byZipCodeAndCountry(zipcode, countryCode)
				.retrieve()
				.asJava();

		Rain rain = weather.getRain();
		if (rain != null) {
			Double oneHour = rain.getOneHourLevel();
			Double threeHour = rain.getThreeHourLevel();
			return (oneHour != null && oneHour > 0.0) || (threeHour != null && threeHour > 0.0);
		}
		return false;
	}





	public static String getZipCodeFromIP() {
		HttpURLConnection conn = null;
		BufferedReader in = null;

		try {
			URL url = new URL("https://ipapi.co/json/");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);  // 5 seconds connection timeout
			conn.setReadTimeout(5000);     // 5 seconds read timeout

			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP request failed with code: " + responseCode);
			}

			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}

			// Using json-smart library to parse
			JSONParser parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
			JSONObject json = (JSONObject) parser.parse(response.toString());

			// Get zip code or return "Unknown" if not present
			Object zipCode = json.get("postal");
			return zipCode != null ? zipCode.toString() : "Unknown";

		} catch (ParseException e) {
			System.err.println("Error parsing JSON response: " + e.getMessage());
			return "Unknown";
		} catch (Exception e) {
			System.err.println("Error getting ZIP code from IP: " + e.getMessage());
			return "Unknown";
		} finally {
			// Properly close resources
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					System.err.println("Error closing reader: " + e.getMessage());
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	public static String getCountryCodeFromIP() {
		HttpURLConnection conn = null;
		BufferedReader in = null;

		try {
			URL url = new URL("https://ipapi.co/json/");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);  // 5 seconds connection timeout
			conn.setReadTimeout(5000);     // 5 seconds read timeout

			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP request failed with code: " + responseCode);
			}

			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}

			// Using json-smart library to parse
			JSONParser parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
			JSONObject json = (JSONObject) parser.parse(response.toString());

			// Get country code or return "Unknown" if not present
			Object countryCode = json.get("country_code");
			return countryCode != null ? countryCode.toString() : "Unknown";

		} catch (ParseException e) {
			System.err.println("Error parsing JSON response: " + e.getMessage());
			return "Unknown";
		} catch (Exception e) {
			System.err.println("Error getting country code from IP: " + e.getMessage());
			return "Unknown";
		} finally {
			// Properly close resources
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					System.err.println("Error closing reader: " + e.getMessage());
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}


	private void syncWeather(MinecraftServer server) {
		String zipCode = getZipCodeFromIP();
		String countryCode = getCountryCodeFromIP();
		boolean raining = isRaining(zipCode, countryCode);

		if(server.getOverworld().getGameRules().getBoolean(SYNC_WEATHER)){
			if (raining) {
				server.getOverworld().setWeather(0, 12000, true, false);
				//broadcastMessage(server, "🌧️ It's raining in " + zipCode + countryCode + " — syncing Minecraft rain!");
			} else {
				server.getOverworld().setWeather(0, 0, false, false);
				//broadcastMessage(server, "☀️ No rain in " + zipCode + countryCode + " — clearing Minecraft weather.");
			}
		}
	}


	public void broadcastMessage(MinecraftServer server, String message) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.sendMessage(Text.of(message), false); // false = not a system message
		}
	}

	private int executeRealtimeCommand(CommandContext<ServerCommandSource> context) {
		long minecraftTicks = context.getSource().getWorld().getTimeOfDay();
		long realSeconds = (minecraftTicks * 86400) / 24000;
		LocalTime realTime = LocalTime.ofSecondOfDay(realSeconds).plusHours(6);
		Objects.requireNonNull(context.getSource().getPlayer()).sendMessage(Text.of("Real world time: " + realTime), false);
		return 1;
	}

	private int executeSyncWeatherCommand(CommandContext<ServerCommandSource> context) {
		try {
			MinecraftServer server = context.getSource().getServer();
			syncWeather(server);
			context.getSource().sendFeedback(() -> Text.of("✅ Weather sync triggered."), false);
		} catch (Exception e) {
			//LOGGER.error("❌ Error executing /syncweather command", e);
			context.getSource().sendError(Text.of("⚠️ Failed to sync weather. Check server logs."));
		}
		return 1;
	}



}