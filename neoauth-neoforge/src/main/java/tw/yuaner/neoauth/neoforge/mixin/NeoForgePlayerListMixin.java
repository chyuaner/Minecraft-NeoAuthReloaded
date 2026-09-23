package tw.yuaner.neoauth.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tw.yuaner.neoauth.AuthManager;

@Mixin(PlayerList.class)
public class NeoForgePlayerListMixin {
    @Redirect(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void neoauth$onBroadcastJoinMessage(PlayerList instance, Component message, boolean bypassHiddenChat, net.minecraft.network.Connection connection, ServerPlayer player) {
        // 如果玩家尚未登入，不廣播加入遊戲的訊息
        if (AuthManager.isLoggedIn(player.getUUID())) {
            tw.yuaner.neoauth.core.PlayerSessionData session = AuthManager.getSession(player.getUUID());
            if (session != null) {
                session.setJoinMessageBroadcasted(true);
            }
            instance.broadcastSystemMessage(message, bypassHiddenChat);
        }
    }
}
