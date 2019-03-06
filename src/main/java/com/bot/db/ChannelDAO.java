package com.bot.db;

import com.bot.db.mappers.TextChannelMapper;
import com.bot.db.mappers.VoiceChannelMapper;
import com.bot.models.InternalTextChannel;
import com.bot.models.InternalVoiceChannel;
import com.bot.utils.DbHelpers;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChannelDAO {
    private static final Logger LOGGER = Logger.getLogger(ChannelDAO.class.getName());

    private HikariDataSource read;
    private HikariDataSource write;
    private static ChannelDAO instance;

    private ChannelDAO() {
        try {
            initialize();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // This constructor is only to be used by integration tests so we can pass in a connection to the integration-db
    public ChannelDAO(HikariDataSource dataSource) {
        read = dataSource;
        write = dataSource;
    }


    public static ChannelDAO getInstance() {
        if (instance == null) {
            instance = new ChannelDAO();
        }
        return instance;
    }

    private void initialize() throws SQLException {
        this.write = ConnectionPool.getDataSource();
        this.read = ReadConnectionPool.getDataSource();
    }

    public void addVoiceChannel(VoiceChannel voiceChannel) {
        String query = "INSERT INTO voice_channel(id, guild, name) VALUES (?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)";
        PreparedStatement preparedStatement = null;
        Connection connection = null;
        try {
            connection = write.getConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, voiceChannel.getId());
            preparedStatement.setString(2, voiceChannel.getGuild().getId());
            preparedStatement.setString(3, voiceChannel.getName());
            preparedStatement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add voice channel to the db " +e.getMessage());
        } finally {
            DbHelpers.close(preparedStatement, null, connection);
        }
    }

    public void addTextChannel(TextChannel textChannel) {
        String query = "INSERT INTO text_channel(id, guild, name) VALUES (?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)";
        PreparedStatement preparedStatement = null;
        Connection connection = null;

        try {
            connection = write.getConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, textChannel.getId());
            preparedStatement.setString(2, textChannel.getGuild().getId());
            preparedStatement.setString(3, textChannel.getName());
            preparedStatement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add text channel to the db: " + e.getMessage());
        } finally {
            DbHelpers.close(preparedStatement, null, connection);
        }
    }

    public void removeVoiceChannel(VoiceChannel channel) {
        String query = "DELETE FROM voice_channel WHERE id = ?";
        PreparedStatement preparedStatement = null;
        Connection connection = null;

        try {
            connection = write.getConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, channel.getId());
            preparedStatement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to remove a voice channel from db: " +e.getMessage());
        } finally {
            DbHelpers.close(preparedStatement, null, connection);
        }
    }

    public void removeTextChannel(TextChannel channel) {
        String query = "DELETE FROM text_channel WHERE id = ?";
        PreparedStatement preparedStatement = null;
        Connection connection = null;

        try {
            connection = write.getConnection();
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, channel.getId());
            preparedStatement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to remove a text channel from db: " +e.getMessage());
        } finally {
            DbHelpers.close(preparedStatement, null, connection);
        }
    }

    // Updates a voice channels enabled status, creates the channel as a failsafe
    public boolean setVoiceChannelEnabled(VoiceChannel channel, boolean enabled) {
        String query = "INSERT INTO voice_channel(id, name, guild, voice_enabled) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE voice_enabled = VALUES(voice_enabled)";
        PreparedStatement statement = null;
        Connection connection = null;

        try {
            connection = write.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, channel.getId());
            statement.setString(2, channel.getName());
            statement.setString(3, channel.getGuild().getId());
            statement.setBoolean(4, enabled);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update channel voice: " + e.getMessage());
            return false;
        } finally {
            DbHelpers.close(statement, null, connection);
        }
        return true;
    }

    public boolean setTextChannelNSFW(TextChannel textChannel, boolean enabled) {
        String query = "INSERT INTO text_channel (id, name, guild, nsfw_enabled) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE nsfw_enabled = VALUES(nsfw_enabled)";
        PreparedStatement statement = null;
        Connection connection = null;

        try {
            connection = write.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, textChannel.getId());
            statement.setString(2, textChannel.getName());
            statement.setString(3, textChannel.getGuild().getId());
            statement.setBoolean(4, enabled);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update channel nsfw: " + e.getMessage());
            return false;
        } finally {
            DbHelpers.close(statement, null, connection);
        }
        return true;
    }

    public List<InternalVoiceChannel> getVoiceChannelsForGuild(String guildId) {
        String query = "Select c.id, c.name, c.guild, c.voice_enabled FROM voice_channel c WHERE c.guild = ?";
        return getVoiceChannelsForQuery(guildId, query);
    }

    public List<InternalTextChannel> getTextChannelsForGuild(String guildId) {
        String query = "Select c.id, c.name, c.guild, c.voice_enabled, c.announcement, c.nsfw_enabled, c.commands_enabled FROM text_channel c WHERE c.guild = ?";
        return getTextChannelsForQuery(guildId, query);
    }

    public InternalTextChannel getTextChannelForId(String channelId) {
        String query = "SELECT c.id, c.name, c.guild, c.voice_enabled, c.announcement, c.nsfw_enabled, c.commands_enabled FROM text_channel c WHERE c.id = ?";
        PreparedStatement statement = null;
        InternalTextChannel toReturn = null;
        Connection connection = null;
        ResultSet set = null;
        try {
            connection = read.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, channelId);
            set = statement.executeQuery();

            if (set.next()) {
                toReturn = TextChannelMapper.mapSetToInternalTextChannel(set);
            }
        } catch (SQLException e ) {
            LOGGER.severe("Failed to get Text channel: "+ channelId +" for id.." + e.getMessage());
        } finally {
            DbHelpers.close(statement, set, connection);
        }

        return toReturn;
    }

    public InternalVoiceChannel getVoiceChannelForId(String channelId) {
        String query = "SELECT c.id, c.name, c.voice_enabled, c.guild FROM voice_channel c WHERE c.id = ?";
        PreparedStatement statement = null;
        InternalVoiceChannel toReturn = null;
        Connection connection = null;
        ResultSet set = null;

        try {
            connection = read.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, channelId);
            set = statement.executeQuery();

            if (set.next()) {
                toReturn = VoiceChannelMapper.mapSetToInternalVoiceChannel(set);
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to get voiceChannel: "+ channelId + " for id.. " + e.getMessage());
        } finally {
            DbHelpers.close(statement, set, connection);
        }
        return toReturn;
    }

    private List<InternalTextChannel> getTextChannelsForQuery(String guildId, String query) {
        List<InternalTextChannel> channels = null;
        PreparedStatement statement = null;
        ResultSet set = null;
        Connection connection = null;

        try {
            connection = read.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, guildId);
            set = statement.executeQuery();
            channels = new ArrayList<>();
            while (set.next()) {
                channels.add(TextChannelMapper.mapSetToInternalTextChannel(set));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            DbHelpers.close(statement, set, connection);
        }

        return channels;
    }

    private List<InternalVoiceChannel> getVoiceChannelsForQuery(String guildId, String query) {
        List<InternalVoiceChannel> channels = null;
        PreparedStatement statement = null;
        ResultSet set = null;
        Connection connection = null;

        try {
            connection = read.getConnection();
            statement = connection.prepareStatement(query);
            statement.setString(1, guildId);
            set = statement.executeQuery();
            channels = new ArrayList<>();
            while (set.next()) {
                channels.add(VoiceChannelMapper.mapSetToInternalVoiceChannel(set));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            DbHelpers.close(statement, set, connection);
        }

        return channels;
    }
}