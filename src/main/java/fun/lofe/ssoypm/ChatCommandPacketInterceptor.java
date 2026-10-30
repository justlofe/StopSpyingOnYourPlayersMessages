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

import com.mojang.brigadier.ParseResults;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.FutureChain;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unchecked")
final class ChatCommandPacketInterceptor extends ChannelInboundHandlerAdapter {

    private static final Method
            collectSignedArguments,
            parseCommand,
            tryHandleChat,
            unpackAndApplyLastSeen,
            detectCommandRateSpam;

    private static final Logger LOGGER;
    private static final Component INVALID_COMMAND_SIGNATURE;

    static {
        try {
            collectSignedArguments = ServerGamePacketListenerImpl.class.getDeclaredMethod("collectSignedArguments", ServerboundChatCommandSignedPacket.class, SignableCommand.class, LastSeenMessages.class);
            collectSignedArguments.setAccessible(true);

            parseCommand = ServerGamePacketListenerImpl.class.getDeclaredMethod("parseCommand", String.class);
            parseCommand.setAccessible(true);

            tryHandleChat = ServerGamePacketListenerImpl.class.getDeclaredMethod("tryHandleChat", String.class, boolean.class, Runnable.class, boolean.class);
            tryHandleChat.setAccessible(true);

            unpackAndApplyLastSeen = ServerGamePacketListenerImpl.class.getDeclaredMethod("unpackAndApplyLastSeen", LastSeenMessages.Update.class);
            unpackAndApplyLastSeen.setAccessible(true);

            detectCommandRateSpam = ServerGamePacketListenerImpl.class.getDeclaredMethod("detectCommandRateSpam", String.class);
            detectCommandRateSpam.setAccessible(true);

            Field loggerField = ServerGamePacketListenerImpl.class.getDeclaredField("LOGGER");
            loggerField.setAccessible(true);
            LOGGER = (Logger) loggerField.get(null);

            Field invalidCommandSignatureField = ServerGamePacketListenerImpl.class.getDeclaredField("INVALID_COMMAND_SIGNATURE");
            invalidCommandSignatureField.setAccessible(true);
            INVALID_COMMAND_SIGNATURE = (Component) invalidCommandSignatureField.get(null);
        }
        catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private final ServerGamePacketListenerImpl serverGamePacketListener;
    private final Set<String> targetCommands;

    private final MinecraftServer server;
    private final FutureChain chatMessageChain;

    ChatCommandPacketInterceptor(ServerGamePacketListenerImpl serverGamePacketListener, Set<String> targetCommands) {
        this.serverGamePacketListener = serverGamePacketListener;
        this.targetCommands = targetCommands;

        try {
            Field serverField = ServerCommonPacketListenerImpl.class.getDeclaredField("server");
            serverField.setAccessible(true);
            server = (MinecraftServer) serverField.get(serverGamePacketListener);

            Field chatMessageChainField = ServerGamePacketListenerImpl.class.getDeclaredField("chatMessageChain");
            chatMessageChainField.setAccessible(true);
            chatMessageChain = (FutureChain) chatMessageChainField.get(serverGamePacketListener);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof ServerboundChatCommandPacket(String command) && isMsgCommand(command)) {
            tryHandleChat(command, true, () -> {
                if(!serverGamePacketListener.player.hasDisconnected()) {
                    ParseResults<CommandSourceStack> parsed = this.parseCommand(command);
                    if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parsed)) {
                        LOGGER.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", serverGamePacketListener.player.getGameProfile().name(), command);
                        serverGamePacketListener.player.sendSystemMessage(INVALID_COMMAND_SIGNATURE);
                    }
                    else {
                        this.server.getCommands().performCommand(parsed, command);
                    }

                    detectCommandRateSpam(command);
                }
            }, true);
            return;
        }

        if(msg instanceof ServerboundChatCommandSignedPacket packet && isMsgCommand(packet.command())) {
            Optional<LastSeenMessages> unpackedLastSeen = this.unpackAndApplyLastSeen(packet.lastSeenMessages());
            if(unpackedLastSeen.isEmpty()) return;

            tryHandleChat(packet.command(), true, () -> {
                if(!serverGamePacketListener.player.hasDisconnected()) {
                    ParseResults<CommandSourceStack> command = this.parseCommand(packet.command());

                    Map<String, PlayerChatMessage> signedArguments;
                    try {
                        signedArguments = this.collectSignedArguments(packet, SignableCommand.of(command), unpackedLastSeen.get());
                    }
                    catch (SignedMessageChain.DecodeException e) {
                        this.handleMessageDecodeFailure(e);
                        return;
                    }

                    CommandSigningContext signingContext = new CommandSigningContext.SignedArguments(signedArguments);
                    command = Commands.mapSource(command, (source) -> source.withSigningContext(signingContext, this.chatMessageChain));
                    this.server.getCommands().performCommand(command, packet.command());

                    detectCommandRateSpam(packet.command());
                }
            }, true);
            return;
        }

        super.channelRead(ctx, msg);
    }

    private boolean isMsgCommand(String command) {
        String[] parts = command.split(" ");
        if(parts.length == 1) return false;
        return targetCommands.contains(parts[0]);
    }

    private void tryHandleChat(String message, boolean isCommand, Runnable chatHandler, boolean sync) throws Exception {
        tryHandleChat.invoke(serverGamePacketListener, message, isCommand, chatHandler, sync);
    }

    private ParseResults<CommandSourceStack> parseCommand(String command) {
        try {
            return (ParseResults<CommandSourceStack>) parseCommand.invoke(serverGamePacketListener, command);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private <S> Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandSignedPacket packet, SignableCommand<S> command, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        try {
            return (Map<String, PlayerChatMessage>) collectSignedArguments.invoke(serverGamePacketListener, packet, command, lastSeenMessages);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void handleMessageDecodeFailure(SignedMessageChain.DecodeException e) {
        LOGGER.warn("Failed to update secure chat state for {}: '{}'", serverGamePacketListener.player.getGameProfile().name(), e.getComponent().getString());
        serverGamePacketListener.player.sendSystemMessage(e.getComponent().copy().withStyle(ChatFormatting.RED));
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update update) {
        try {
            return (Optional<LastSeenMessages>) unpackAndApplyLastSeen.invoke(serverGamePacketListener, update);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void detectCommandRateSpam(String command) {
        try {
            detectCommandRateSpam.invoke(serverGamePacketListener, command);
        }
        catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

}
