/*
MIT License

Copyright (c) 2026 Kirill Dunchenko

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package fun.lofe.ssoypm;

import io.netty.channel.ChannelPipeline;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.stream.Collectors;

public final class StopSpyingOnYourPlayersMessages {

    public static final Set<String> DEFAULT_TARGET_COMMANDS = Set.of(
            "tell", "msg", "w", "m", "r", "reply"
    );
    private static final String HANDLER_NAME = "chat_command_packet_interceptor_handler";

    private StopSpyingOnYourPlayersMessages() {}

    /**
     * Injects an interceptor into the player's connection, ensuring that commands never reach the standard command-processing mechanism.
     * This prevents commands from being logged anywhere.
     * @implNote This also stops {@link PlayerCommandPreprocessEvent} from being fired.
     * @param target Bukkit player instance
     * @see StopSpyingOnYourPlayersMessages#stopSpying(Player, Set)
     */
    public static void stopSpying(Player target) {
        stopSpying(target, DEFAULT_TARGET_COMMANDS);
    }

    /**
     * Injects an interceptor into the player's connection, ensuring that commands never reach the standard command-processing mechanism.
     * This prevents commands from being logged anywhere.
     * @implNote This also stops {@link PlayerCommandPreprocessEvent} from being fired.
     * @param target Bukkit player instance
     * @param targetCommands specific commands that will not be logged
     */
    public static void stopSpying(Player target, Set<String> targetCommands) {
        if(!(target instanceof CraftPlayer craftPlayer)) throw new IllegalArgumentException("player object is not an instance of CraftPlayer.");
        stopSpying(craftPlayer.getHandle(), targetCommands);
    }

    /**
     * Injects an interceptor into the player's connection, ensuring that commands never reach the standard command-processing mechanism.
     * This prevents commands from being logged anywhere.
     * @implNote This also stops {@link PlayerCommandPreprocessEvent} from being fired.
     * @param target NMS player instance
     * @see StopSpyingOnYourPlayersMessages#stopSpying(ServerPlayer, Set)
     */
    public static void stopSpying(ServerPlayer target) {
        stopSpying(target, DEFAULT_TARGET_COMMANDS);
    }

    /**
     * Injects an interceptor into the player's connection, ensuring that commands never reach the standard command-processing mechanism.
     * This prevents commands from being logged anywhere.
     * @implNote This also stops {@link PlayerCommandPreprocessEvent} from being fired.
     * @param target Bukkit player instance
     * @param targetCommands specific commands that will not be logged
     */
    public static void stopSpying(ServerPlayer target, Set<String> targetCommands) {
        ChannelPipeline pipeline = target.connection.connection.channel.pipeline();
        if(pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);

        pipeline.addBefore(
                "packet_handler",
                HANDLER_NAME,
                new ChatCommandPacketInterceptor(target.connection, prepareOrRejectTargetCommands(targetCommands))
        );
    }

    private static Set<String> prepareOrRejectTargetCommands(Set<String> targetCommands) {
        if(targetCommands == DEFAULT_TARGET_COMMANDS) return targetCommands;

        return targetCommands.stream()
                .map(String::toLowerCase)
                .filter(string -> {
                    if (string.indexOf(' ') != -1) throw new IllegalArgumentException("Command's name cannot contain spaces.");
                    return true;
                })
                .collect(Collectors.toSet());
    }

}
