package com.bot.commands.reddit;

import com.bot.RedditConnection;
import com.bot.commands.RedditCommand;
import com.bot.db.ChannelDAO;
import com.bot.exceptions.ScheduledCommandFailedException;
import com.bot.models.InternalTextChannel;
import com.bot.utils.CommandCategories;
import com.bot.utils.ConstantStrings;
import com.bot.utils.RedditHelper;
import com.jagrosh.jdautilities.command.CommandEvent;
import datadog.trace.api.Trace;
import net.dean.jraw.ApiException;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.models.TimePeriod;
import net.dv8tion.jda.api.entities.ChannelType;

import java.util.logging.Level;


public class TopPostCommand extends RedditCommand{
    private RedditConnection redditConnection;
    private ChannelDAO channelDAO;

    public TopPostCommand() {
        this.name = "tr";
        this.help = "Grabs a random post from the top all time posts on a given subreddit";
        this.arguments = "<subreddit name>";
        this.category = CommandCategories.REDDIT;
        redditConnection = RedditConnection.getInstance();
        channelDAO = ChannelDAO.getInstance();
    }

    @Override
    //@trace(operationName = "executeCommand", resourceName = "TopReddit")
    protected void executeCommand(CommandEvent commandEvent) {
        boolean isNSFWAllowed = true;

        // TODO: Move to static helper
        if (!commandEvent.isFromType(ChannelType.PRIVATE)) {
            InternalTextChannel channel = channelDAO.getTextChannelForId(commandEvent.getTextChannel().getId(), true);

            if (channel == null) {
                commandEvent.reply(commandEvent.getClient().getError() + " Something went wrong getting the channel from the db. Please try again.");
                metricsManager.markCommandFailed(this, commandEvent.getAuthor(), commandEvent.getGuild());
                return;
            }

            isNSFWAllowed = commandEvent.getTextChannel().isNSFW();
        }

        try {
            RedditHelper.getRandomSubmissionAndSend(redditConnection,
                    commandEvent,
                    SubredditSort.TOP,
                    TimePeriod.ALL,
                    200,
                    isNSFWAllowed);
        } catch (NullPointerException e) {
            commandEvent.replyWarning("I could not find that subreddit, please make sure it is public, and spelled correctly.");
        } catch (ApiException e) {
            if (e.getCode().equals("403")) {
                commandEvent.replyWarning("Subreddit is private, please stick to public subreddits.");
            } else {
                commandEvent.replyError("Recieved error: " + e.getCode() + " from reddit.");
            }
        } catch (NetworkException e) {
            commandEvent.replyWarning("I was unable to get info the subreddit, please make sure it is correctly spelled.");
        } catch (ScheduledCommandFailedException e) {
            logger.warning("Failed to get webhook for scheduled command " + commandEvent.getTextChannel().getId(), e);
            commandEvent.replyWarning(ConstantStrings.SCHEDULED_WEBHOOK_FAIL);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error thrown: " + e);
            commandEvent.replyError("Sorry, something went wrong getting a reddit post.");
            metricsManager.markCommandFailed(this, commandEvent.getAuthor(), commandEvent.getGuild());
        }
    }
}
