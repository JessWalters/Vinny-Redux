package com.bot.commands.reddit;

import com.bot.RedditConnection;
import com.bot.commands.MemeCommand;
import com.bot.utils.RedditHelper;
import com.jagrosh.jdautilities.command.CommandEvent;
import datadog.trace.api.Trace;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.models.TimePeriod;

import java.util.logging.Level;

/**
 * This class is technically a reddit command but because of its nature we are calling it a meme category commande
 */
public class ShitpostCommand extends MemeCommand {
    private RedditConnection redditConnection;

    public ShitpostCommand() {
        this.name = "shitpost";
        this.help = "Posts a shitpost";
        this.aliases = new String[]{"shit"};

        redditConnection = RedditConnection.getInstance();
    }

    @Override
    @Trace(operationName = "executeCommand", resourceName = "Shitpost")
    protected void executeCommand(CommandEvent commandEvent) {
        try{
            RedditHelper.getRandomSubmissionAndSend(redditConnection,
                    commandEvent,
                    SubredditSort.HOT,
                    TimePeriod.WEEK,
                    200,
                    true,
                    "shitpost");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error thrown:" + e);
            commandEvent.reply(commandEvent.getClient().getError() + " Sorry, something went wrong getting a reddit post.");
            metricsManager.markCommandFailed(this, commandEvent.getAuthor(), commandEvent.getGuild());
        }
    }
}
