package xyz.n7mn.dev.nanamiauthsystem;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

public class DiscordListener extends ListenerAdapter {

    private HashMap<String, TokenData> tokenList;
    private final Plugin plugin;

    public DiscordListener(Plugin plugin, HashMap<String, TokenData> tokenList) {
        this.tokenList = tokenList;
        this.plugin = plugin;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        BukkitRunnable bukkitRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("タイマー起動");
                new Thread(()->{
                    List<Guild> guilds = event.getJDA().getGuilds();
                    for (Guild guild : guilds){
                        Member member = guild.getMemberById(event.getJDA().getSelfUser().getId());
                        if (member != null && member.getNickname() == null){
                            if (member.hasPermission(Permission.NICKNAME_CHANGE)){
                                member.modifyNickname("[7m.] ななぼっと").queue();
                            }
                        }
                    }
                }).start();

                EmbedBuilder builder = new EmbedBuilder();
                builder.setTitle("NanamiAuthSystem Ver "+plugin.getDescription().getVersion());
                builder.setColor(Color.PINK);

                new Thread(()-> {
                    TextChannel channel = event.getJDA().getTextChannelById("922501196286140476");
                    builder.setDescription("同期開始しました。");
                    builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                    channel.sendMessageEmbeds(builder.build()).queue();

                    try {
                        Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                        PreparedStatement statement = con.prepareStatement("SELECT * FROM UserList, RoleList WHERE UserList.RoleUUID = RoleList.UUID and UserList.Active = 1");
                        ResultSet set = statement.executeQuery();
                        int i = 0;
                        while (set.next()) {
                            i++;
                            String discordUserID = set.getString("DiscordUserID");
                            Member member = channel.getGuild().getMemberById(discordUserID);
                            if (member == null) {
                                builder.setColor(Color.YELLOW);
                                builder.setDescription("" +
                                        "以下のユーザーの同期をスキップしました。\n" +
                                        "\n" +
                                        "DiscordID : " + discordUserID + "\n" +
                                        "Minecraft : https://mine.ly/" + set.getString("MinecraftUserID") + "\n" +
                                        "理由 : サーバーから抜けているか存在しないユーザー"
                                );
                                builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                                channel.sendMessageEmbeds(builder.build()).queue();
                                set.close();
                                statement.close();
                                con.close();
                                return;
                            }

                            List<String> roleList = new ArrayList<>();
                            String permName = null;
                            String roleId = null;

                            try {
                                PreparedStatement statement1 = con.prepareStatement("SELECT * FROM RoleList ORDER BY RoleRank DESC");
                                ResultSet set1 = statement.executeQuery();
                                while (set1.next()) {
                                    roleList.add(set.getString("DiscordRoleID") + "," + set.getString("RoleDisplayName"));
                                }
                                set1.close();
                                statement1.close();
                            } catch (SQLException ex) {
                                ex.printStackTrace();
                            }

                            List<Role> list = member.getRoles();
                            for (Role role : list) {
                                boolean isfound = false;
                                for (String r : roleList) {
                                    String[] split = r.split(",");
                                    if (role.getId().equals(split[0])) {
                                        permName = split[1];
                                        roleId = role.getId();
                                        isfound = true;
                                        break;
                                    }
                                }
                                if (isfound) {
                                    break;
                                }
                            }

                            if (roleId == null) {
                                continue;
                            }

                            if (roleId.equals(set.getString("DiscordRoleID"))) {
                                continue;
                            }

                            builder.setColor(Color.GREEN);
                            builder.setDescription("" +
                                    "以下の変更を検知しました。\n" +
                                    "\n" +
                                    "DiscordName : `" + member.getAsMention() + "`\n" +
                                    "DiscordID : `" + discordUserID + "`\n" +
                                    "Minecraft : https://mine.ly/" + set.getString("MinecraftUserID") + "\n" +
                                    "\n" +
                                    "旧階級ロール : `" + set.getString("RoleDisplayName") + "` (ID:" + set.getString("DiscordRoleID") + ")\n" +
                                    "新階級ロール : `" + permName + "` (ID:" + roleId + ")"
                            );
                            builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                            channel.sendMessageEmbeds(builder.build()).queue();

                            PreparedStatement statement1 = con.prepareStatement("UPDATE `UserList` SET `RoleUUID`= ? WHERE UUID = ?");
                            statement1.setString(1, roleId);
                            statement1.setString(2, set.getString("UUID"));
                            statement1.execute();
                            statement1.close();
                        }

                        builder.setDescription("同期終了しました。 (" + i + " 件)");
                        builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                        channel.sendMessageEmbeds(builder.build()).queue();

                        set.close();
                        statement.close();
                        con.close();

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        builder.setColor(Color.RED);
                        builder.setDescription("" +
                                "MySQLの接続に失敗 または 処理失敗しました。"
                        );
                        builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                        channel.sendMessageEmbeds(builder.build()).queue();
                    }
                }).start();
            }
        };
        bukkitRunnable.runTaskTimerAsynchronously(plugin, 0L, 1200L);
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
