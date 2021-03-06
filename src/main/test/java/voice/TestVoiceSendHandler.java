package voice;

import com.bot.voice.VoiceSendHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class TestVoiceSendHandler {

    private AudioPlayer audioPlayer;

    @Before
    public void setUp() {
        audioPlayer = mock(AudioPlayer.class);
    }

    @Test
    public void testConstructor() {
        VoiceSendHandler voiceSendHandler = new VoiceSendHandler(audioPlayer);

        assertEquals(audioPlayer, voiceSendHandler.getPlayer());
        assertNull(voiceSendHandler.getNowPlaying());
        assertEquals(0, voiceSendHandler.getTracks().size());
    }
}
