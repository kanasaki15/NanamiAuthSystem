package xyz.n7mn.dev.nanamiauthsystem;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DiscordListener extends ListenerAdapter {

    private HashMap<String, TokenData> tokenList;
    private final Plugin plugin;

    public DiscordListener(Plugin plugin, HashMap<String, TokenData> tokenList) {
        this.tokenList = tokenList;
        this.plugin = plugin;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        List<Guild> guilds = event.getJDA().getGuilds();
        for (Guild guild : guilds){
            Member member = guild.getMemberById(event.getJDA().getSelfUser().getId());
            if (member != null && member.getNickname() == null){
                if (member.hasPermission(Permission.NICKNAME_CHANGE)){
                    member.modifyNickname("[7m.] ななぼっと").queue();
                }
            }
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.isWebhookMessage() && event.getAuthor().isSystem() && event.getAuthor().isBot() && event.getMessage().isFromType(ChannelType.PRIVATE)){
            return;
        }

        String raw = event.getMessage().getContentRaw();

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("ななみ鯖 認証");
        builder.setColor(Color.PINK);
        builder.setThumbnail("https://7.4096.xyz/7m/icon.png");
        if (event.getMessage().getGuild().getId().equals("810725404545515561") && (raw.toLowerCase().startsWith("7m.verify") || raw.toLowerCase().startsWith("7m.vy"))){
            try {
                MessageDigest instance = MessageDigest.getInstance("SHA-256");
                String str = UUID.randomUUID() + " " + new SecureRandom().nextInt() + " " + event.getMessage().getId();
                instance.update(str.getBytes(StandardCharsets.UTF_8));
                byte[] cipher_byte = instance.digest();
                StringBuilder sb = new StringBuilder(2 * cipher_byte.length);
                for(byte b : cipher_byte) {
                    sb.append(String.format("%02x", b&0xff) );
                }

                int i = new SecureRandom().nextInt(sb.length() - 5);
                String token = sb.substring(i, i + 5);

                // チャンネル・ロール作成
                String mapName = "auth"+event.getAuthor().getId();
                builder.setDescription("" +
                        "「`"+plugin.getConfig().getString("LobbyIP")+"`」に入り\n" +
                        "以下の文字列を入力してください。\n" +
                        "\n" +
                        "`vy."+token+"`\n" +
                        "(5分経ってしまった場合は最初からやり直してください。)"
                );

                event.getGuild().createRole().setName(mapName).queue(role -> {
                    event.getGuild().addRoleToMember(event.getAuthor().getId(), role).queue();
                    event.getGuild().createTextChannel(mapName, event.getGuild().getCategoryById(plugin.getConfig().getString("DiscordVerifyCategoryID"))).syncPermissionOverrides().addRolePermissionOverride(role.getIdLong(), 68672, 0).queue((channel->{
                        channel.sendMessage(event.getAuthor().getAsMention()).setEmbeds(builder.build()).queue();
                        event.getMessage().reply(channel.getAsMention() + " に進んで指示に従ってください。").queue(m -> {
                            tokenList.put(token, new TokenData(token, m, new Date(), event.getAuthor().getId(), channel.getId()));
                        });

                    }));
                });


            } catch (Exception e) {
                e.printStackTrace();
                builder.setDescription("なにやらエラーが発生したようです。");
                event.getMessage().replyEmbeds(builder.build()).queue();
            }



        }
    }
}
