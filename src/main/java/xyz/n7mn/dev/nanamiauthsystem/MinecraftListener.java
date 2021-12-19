package xyz.n7mn.dev.nanamiauthsystem;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.awt.*;
import java.util.Date;
import java.util.HashMap;

public class MinecraftListener implements Listener {

    private HashMap<String, TokenData> tokenList;
    private final Plugin plugin;

    public MinecraftListener(Plugin plugin, HashMap<String, TokenData> tokenList) {
        this.tokenList = tokenList;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void AsyncChatEvent (AsyncChatEvent e){
        TextComponent textComponent = (TextComponent) e.message();
        String chatText = textComponent.content();

        if (!chatText.startsWith("vy.")){
            return;
        }
        e.setCancelled(true);

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("ななみ鯖 認証");
        builder.setColor(Color.PINK);
        builder.setThumbnail("https://7mi.site/7m/icon.png");

        String key = chatText.replaceAll("vy.", "");
        TokenData data = tokenList.get(key);
        if (data == null){
            e.getPlayer().sendMessage(Component.text(ChatColor.RED + "文字列が間違っています。"));
            return;
        }

        long time = new Date().getTime() - data.getDate().getTime();
        if (time >= 300000){
            builder.setDescription("""
                    有効期限が切れました。
                    最初からやり直してください。"""
            );

            data.getMessage().editMessageEmbeds(builder.build()).queue(m -> {
                m.addReaction("\uD83D\uDEAB").queue();
            });
            return;
        }

        //TODO MySQL追加処理
        String permName = ""; // 認証レベル

        builder.setDescription("" +
                "認証が成功しました！\n" +
                "プレーヤー名 : ||" + e.getPlayer().getName().replaceAll("\\.","") + "||"
        );

        data.getMessage().editMessageEmbeds(builder.build()).queue(m -> {
            m.clearReactions().queue();
            m.addReaction("\u2705").queue();
        });

        tokenList.remove(key);
        e.getPlayer().sendMessage(Component.text(""+
                ChatColor.GREEN + "認証に成功しました！\n" +
                ChatColor.RESET + "--- 連携情報 --- \n" +
                "MinecraftID : " + e.getPlayer().getName()+"\n" +
                "DiscordUser : " + data.getMessage().getGuild().getMemberById(data.getUserId()).getUser().getName() + "\n" +
                "権限 : " + permName
        ));
    }

}
