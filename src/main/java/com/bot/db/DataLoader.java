package com.bot.db;

import com.bot.Config;
import com.bot.ShardingManager;
import com.bot.voice.QueuedAudioTrack;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataLoader {
	private static ShardingManager shardingManager;
	// Needs shards for when running on PROD
	private static final int NUM_SHARDS = 12;

	public static void main(String[] args) throws Exception {
		// Config gets tokens
		Config config = new Config();
		long startTime = System.currentTimeMillis();

		shardingManager = new ShardingManager(NUM_SHARDS, config, true);

		if (config.getConfig("USE_DB").equals("False")) {
			System.out.println("USE_DB Set to False in the config file. Exiting...");
			return;
		}

		if (config.getConfig("DB_USERNAME") == null) {
			System.out.println("DB_USERNAME not set in the config file. Exiting...");
			return;
		}

		if (config.getConfig("DB_PASSWORD") == null) {
			System.out.println("DB_PASSWORD not set in the config file. Exiting...");
			return;
		}

		List<LoadThread> loadThreads = new ArrayList<>();
		try {
			for (JDA bot: shardingManager.getShards()){
				loadThreads.add(new LoadThread(bot, config, startTime));
			}
			for (LoadThread thread : loadThreads) {
				thread.start();
			}
		}
		catch (Exception e) {
			System.out.println(e);
		}

	}

	private static class LoadThread extends Thread {

		private JDA bot;
		private Connection connection = null;
		private long startTime = 0;
		private String guildInsertQuery = "INSERT INTO guild (id, name) VALUES (?, ?)";
		private String textChannelInsertQuery = "INSERT INTO text_channel (id, guild, name) VALUES (?, ?, ?)";
		private String userInsertQuery = "INSERT INTO users (id, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE id = id";
		private String guildMembershipInsertQuery = "INSERT INTO guild_membership (user_id, guild) VALUES (?, ?)";

		public LoadThread(JDA bot, Config config, long startTime) throws SQLException, ClassNotFoundException {
			this.bot = bot;
			this.startTime = startTime;
			Class.forName("com.mysql.jdbc.Driver");
			this.connection = DriverManager
					.getConnection("jdbc:mysql://" + config.getConfig("DB_URI") + "/test?"
							+ "user=" + config.getConfig("DB_USERNAME") + "&password=" + config.getConfig("DB_PASSWORD"));

		}

		@Override
		public void run() {
			try {
				System.out.println("Starting shard: " + bot.getShardInfo().getShardId() + " for " + bot.getGuilds().size() + " guilds");
				PreparedStatement statement = null;
				List<User> users = bot.getUsers();
				List<Guild> guilds = bot.getGuilds();
				int guildCount = 0;
				int textChannelCount = 0;

				// Loads guilds into db
				for (Guild g : guilds) {
					statement = connection.prepareStatement(guildInsertQuery);
					statement.setString(1, g.getId());
					statement.setString(2, g.getName());
					statement.execute();
					guildCount++;

					if (guildCount % 250 == 0) {
						System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Added " + guildCount + " guilds");
					}

					// Load all text channels for the guild
					for (TextChannel c : g.getTextChannels()) {
						statement = connection.prepareStatement(textChannelInsertQuery);
						statement.setString(1, c.getId());
						statement.setString(2, g.getId());
						statement.setString(3, c.getName());
						statement.execute();
						textChannelCount++;

						if (textChannelCount % 500 == 0) {
							System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Added " + textChannelCount + " textChannels");
						}
					}
				}
				System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Added " + guildCount + " guilds and " + textChannelCount + " channels.");


				System.out.println("Starting user and membership migration");
				// Users must be added after ALL guilds to ensure no sharding discrepancies.
				System.out.println("Starting shard: " + bot.getShardInfo().getShardId() + " for " + bot.getUsers().size() + " users");
				int userCount = 0;
				int membershipCount = 0;

				// Populate users
				for (User u : users) {
					statement = connection.prepareStatement(userInsertQuery);
					statement.setString(1, u.getId());
					statement.setString(2, u.getName());
					statement.execute();
					userCount++;

					if (userCount % 2500 == 0) {
						System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Added " + userCount + " users");
					}

					// Populate Mutual guild memberships
					for (Guild mg : u.getMutualGuilds()) {
						statement = connection.prepareStatement(guildMembershipInsertQuery);
						statement.setString(1, u.getId());
						statement.setString(2, mg.getId());
						statement.execute();
						membershipCount++;

						if (membershipCount % 5000 == 0) {
							System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Added " + membershipCount + " memberships");
						}
					}
				}
				System.out.println("FINISHED: Shard: " + bot.getShardInfo().getShardId() + " Added " + userCount + " users and " + membershipCount + " memberships.");
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				try {
					connection.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				System.out.println("Shard: " + bot.getShardInfo().getShardId() + " Successfully migrated. Elapsed Time: " + QueuedAudioTrack.msToMinSec(System.currentTimeMillis() - startTime));
			}
		}
	}

}