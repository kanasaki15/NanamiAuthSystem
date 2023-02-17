package xyz.n7mn.dev.nanamiauthsystem;

import com.google.gson.Gson;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.n7mn.dev.nanamiauthsystem.MojangAPI.UUIDtoProfile;

import java.awt.*;
import java.io.IOException;
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
    private JDA jda = null;

    public DiscordListener(Plugin plugin, HashMap<String, TokenData> tokenList) {
        this.tokenList = tokenList;
        this.plugin = plugin;
    }

    @Override
    public void onGenericEvent(GenericEvent event) {
        if (event instanceof ReadyEvent){
            jda = event.getJDA();

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

                    sync();
                }
            };
            bukkitRunnable.runTaskTimerAsynchronously(plugin, 0L, 1200L);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        boolean isBot = false;
        if (event.isWebhookMessage() && event.getAuthor().isSystem() && event.getAuthor().isBot() && event.getMessage().isFromType(ChannelType.PRIVATE)){
            isBot = true;
        }

        String raw = event.getMessage().getContentRaw();
        EmbedBuilder builder = new EmbedBuilder();

        if (!isBot && event.getMessage().getGuild().getId().equals("810725404545515561") && (raw.toLowerCase().startsWith("7m.verify") || raw.toLowerCase().startsWith("7m.vy"))){

            builder.setTitle("ななみ鯖 認証");
            builder.setColor(Color.PINK);
            builder.setThumbnail("https://7.4096.xyz/7m/icon.png");

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
                        "1. Minecraftを起動し「`"+plugin.getConfig().getString("LobbyIP")+"`」に接続\n" +
                        "2.「`vy."+token+"`」をMinecraftのチャットで入力をする。(「」は入力しないでください)\n" +
                        "\n" +
                        "(5分経ってしまった場合は最初からやり直してください。)"
                );

                if (event.getGuild().getRolesByName(mapName, true).size() > 0){
                    return;
                }

                event.getGuild().createRole().setName(mapName).queue(role -> {
                    event.getGuild().addRoleToMember(UserSnowflake.fromId(event.getAuthor().getId()), role).queue();
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

            return;
        }

        if (!isBot && raw.toLowerCase().equals("7m.help")){
            builder.setTitle("ななぼっと ヘルプ");
            builder.setColor(Color.PINK);
            builder.setDescription("""
                    ※未実装は隠し中。
                    `7m.sync` -- 強制的に同期する(運営用)
                    ||`7m.new <DiscordID> <MinecraftID>` -- 強制的に連携する (運営用)
                    `7m.del <DiscordID>` -- 強制的に削除 (運営用)
                    ||
                    `7m.check` -- 自分の認証情報をチェックする
                    `7m.opc` -- ななみ鯖運営人数
                    `7m.help` -- いまのこれ。
                    """
            );

            event.getMessage().replyEmbeds(builder.build()).queue();

            return;
        }

        if (raw.toLowerCase().equals("7m.sync")){
            builder.setTitle("ななみ鯖　権限同期");
            builder.setColor(Color.PINK);
            builder.setDescription("同期中～♪");
            event.getMessage().replyEmbeds(builder.build()).queue(m -> {

                boolean isOK = false;
                try {
                    Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                    PreparedStatement statement = con.prepareStatement("SELECT DiscordUserID, RoleName, RoleRank FROM UserList, RoleList WHERE UserList.Active = 1 AND UserList.RoleUUID = RoleList.UUID AND (RoleName = 'Admin' or RoleName = 'Moderator') GROUP BY DiscordUserID, RoleName, RoleRank ORDER BY RoleRank DESC ");
                    ResultSet set = statement.executeQuery();
                    while (set.next()){
                        if (set.getString("DiscordUserID").equals(event.getAuthor().getId())){
                            isOK = true;
                            break;
                        }
                    }
                    set.close();
                    statement.close();
                    con.close();

                    if (!isOK){
                        builder.setColor(Color.RED);
                        builder.setDescription("あなたは運営じゃないですよね！！");
                        m.editMessageEmbeds(builder.build()).queue();
                        return;
                    }
                } catch (Exception e){
                    e.printStackTrace();

                    builder.setColor(Color.RED);
                    builder.setDescription("エラーが発生しました！係員を呼んでね！！");
                    m.editMessageEmbeds(builder.build()).queue();
                    return;

                }

                sync();
                builder.setDescription("強制同期しましたっ！");
                m.editMessageEmbeds(builder.build()).queue();
            });
        }

        if (raw.toLowerCase().equals("7m.opc")){
            builder.setTitle("ななみ鯖　運営人数");
            builder.setColor(Color.PINK);
            builder.setDescription("いま数えていますっ！しばらくまってて！");
            event.getMessage().replyEmbeds(builder.build()).queue(m -> {
                try {
                    Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                    PreparedStatement statement = con.prepareStatement("SELECT DiscordUserID, RoleName, RoleRank FROM UserList, RoleList WHERE UserList.Active = 1 AND UserList.RoleUUID = RoleList.UUID AND (RoleName = 'Admin' or RoleName = 'Moderator' or RoleName = 'Developer' or RoleName = 'SvModerator') GROUP BY DiscordUserID, RoleName, RoleRank ORDER BY RoleRank DESC ");
                    ResultSet set = statement.executeQuery();
                    int playerCount = 0;
                    int adminCount = 0;
                    int moderatorCount = 0;
                    int developerCount = 0;
                    int svModeratorCount = 0;
                    while (set.next()){

                        boolean skip = false;
                        String userID = set.getString("DiscordUserID");
                        if (event.getGuild().getMemberById(userID).getRoles() != null){
                            for (Role role : event.getGuild().getMemberById(userID).getRoles()){
                                if (role.getName().equals("サブ垢")){
                                    skip = true;
                                    break;
                                }
                            }
                        }

                        if (skip){
                            continue;
                        }

                        playerCount++;

                        if (set.getString("RoleName").equals("Admin")){
                            adminCount++;
                            continue;
                        }

                        if (set.getString("RoleName").equals("Moderator")){
                            moderatorCount++;
                            continue;
                        }

                        if (set.getString("RoleName").equals("Developer")){
                            developerCount++;
                            continue;
                        }

                        if (set.getString("RoleName").equals("SvModerator")){
                            svModeratorCount++;
                        }
                    }
                    set.close();
                    statement.close();
                    con.close();

                    builder.setDescription("" +
                            "管理者(Admin) : "+adminCount+" 人\n" +
                            "管理補助(Moderator) : "+moderatorCount+" 人\n" +
                            "開発者(Developer) : "+developerCount+" 人\n" +
                            "生活鯖管理補助(生活Moderator) : "+svModeratorCount+" 人\n" +
                            "\n" +
                            "総勢 : "+playerCount+" 人ですっ"
                    );

                    m.editMessageEmbeds(builder.build()).queue();

                } catch (SQLException ex){
                    ex.printStackTrace();
                }
            });

            return;
        }


        if (!isBot && raw.toLowerCase().equals("7m.check")){
            String fromUserId = event.getMessage().getAuthor().getId();
            String text = event.getMessage().getContentRaw();

            new Thread(()->{
                builder.setTitle("ユーザー情報");
                builder.setColor(Color.PINK);
                builder.setDescription("検索中♪");
                event.getMessage().replyEmbeds(builder.build()).queue(m -> {
                    boolean isAdmin = false;

                    try {
                        boolean found = false;
                        Enumeration<Driver> drivers = DriverManager.getDrivers();

                        while (drivers.hasMoreElements()) {
                            Driver driver = drivers.nextElement();
                            if (driver.equals(new com.mysql.cj.jdbc.Driver())) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
                        }

                        Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                        PreparedStatement statement = con.prepareStatement("SELECT RoleName FROM UserList, RoleList WHERE UserList.Active = 1 AND UserList.RoleUUID = RoleList.UUID AND (RoleName = 'Admin' or RoleName = 'Moderator') AND DiscordUserID = ?");
                        statement.setString(1, fromUserId);
                        ResultSet set = statement.executeQuery();

                        if (set.next()){
                            isAdmin = true;
                        }

                        set.close();
                        statement.close();
                        con.close();

                        String[] s = text.split(" ");
                        if (!text.equals("7m.check") && !isAdmin){
                            builder.setColor(Color.RED);
                            builder.setDescription("権限がありませんっ！");
                            m.editMessageEmbeds(builder.build()).queue();
                            return;
                        }

                        if (text.equals("7m.check")){
                            Connection con1 = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                            PreparedStatement statement1 = con1.prepareStatement("SELECT MinecraftUserID, RoleDisplayName FROM UserList, RoleList WHERE UserList.Active = 1 AND UserList.RoleUUID = RoleList.UUID AND DiscordUserID = ?");
                            statement1.setString(1, fromUserId);
                            ResultSet set1 = statement1.executeQuery();

                            List<UUID> MinecraftUUID = new ArrayList<>();


                            String name = "";
                            while (set1.next()){
                                if (set1.isFirst()){
                                    name = set1.getString("RoleDisplayName");
                                }
                                MinecraftUUID.add(UUID.fromString(set1.getString("MinecraftUserID")));
                            }


                            set1.close();
                            statement1.close();
                            con1.close();

                            StringBuilder sb = new StringBuilder();

                            for (UUID uuid : MinecraftUUID){
                                String s1 = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replaceAll("-","");

                                if (uuid.toString().startsWith("0000")){
                                    continue;
                                }

                                OkHttpClient client = new OkHttpClient();

                                Request request = new Request.Builder()
                                        .url(s1)
                                        .build();

                                Response response = null;
                                try {
                                    response = client.newCall(request).execute();
                                    UUIDtoProfile json = new Gson().fromJson(response.body().string(), UUIDtoProfile.class);
                                    sb.append("`");
                                    sb.append(json.getName());
                                    sb.append("` ");

                                    response.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }

                            builder.setDescription("" +
                                    "あなたの情報は以下のとおりですっ！\n" +
                                    "MinecraftID (Javaのみ) : "+sb.toString()+"\n" +
                                    "権限 : " + name
                            );
                            m.editMessageEmbeds(builder.build()).queue();

                            if (sb.length() > 0){
                                return;
                            }

                            builder.setDescription("" +
                                    "あなたは認証していませんっ！"
                            );

                            m.editMessageEmbeds(builder.build()).queue();
                            return;
                        }

                        System.out.println(3);
                        String discordId = s[1];

                        Guild guildById = event.getJDA().getGuildById(plugin.getConfig().getString("DiscordGuildId"));
                        Member member = guildById.getMemberById(discordId);

                        if (member == null){
                            for (Member me : guildById.getMembers()){
                                if (me.getNickname() != null && me.getNickname().startsWith(discordId)){
                                    discordId = me.getId();
                                    break;
                                }

                                if (me.getUser().getName().startsWith(discordId)){
                                    discordId = me.getId();
                                    break;
                                }
                            }
                        }

                    } catch (SQLException ex){
                        ex.printStackTrace();
                    }
                });
            }).start();
        }
    }

    private void sync(){

        if (jda == null){
            return;
        }



        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("NanamiAuthSystem Ver "+plugin.getDescription().getVersion());
        builder.setColor(Color.PINK);

        new Thread(()-> {
            TextChannel channel = jda.getTextChannelById("922501196286140476");
            //builder.setDescription("同期開始しました。");
            //builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            //channel.sendMessageEmbeds(builder.build()).queue();

            try {
                Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
                PreparedStatement statement = con.prepareStatement("SELECT * FROM UserList, RoleList WHERE UserList.RoleUUID = RoleList.UUID and UserList.Active = 1");
                ResultSet set = statement.executeQuery();
                int i = 0;
                while (set.next()) {
                    i++;
                    String discordUserID = set.getString("DiscordUserID");
                    String minecraftId = set.getString("MinecraftUserID");
                    String displayName = set.getString("RoleDisplayName");
                    String uuid = set.getString("UUID");
                    String oldId = set.getString("DiscordRoleID");

                    Member member = channel.getGuild().getMemberById(discordUserID);
                    if (member == null) {
                        builder.setColor(Color.YELLOW);
                        builder.setDescription("" +
                                "以下のユーザーの同期をスキップ＆自動無効化しました。\n" +
                                "\n" +
                                "DiscordID : " + discordUserID + "\n" +
                                "Minecraft : https://mine.ly/" + minecraftId + "\n" +
                                "理由 : サーバーから抜けているか存在しないユーザー"
                        );
                        builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                        channel.sendMessageEmbeds(builder.build()).queue();
                        PreparedStatement statement1 = con.prepareStatement("UPDATE `UserList` SET `Active`= 0 WHERE `DiscordUserID` = ?");
                        statement1.setString(1, discordUserID);
                        statement1.execute();
                        statement1.close();
                        continue;
                    }

                    List<String> roleList = new ArrayList<>();
                    String permName = null;
                    String roleId = null;

                    try {
                        PreparedStatement statement1 = con.prepareStatement("SELECT * FROM RoleList ORDER BY RoleRank DESC");
                        ResultSet set1 = statement1.executeQuery();
                        while (set1.next()) {
                            roleList.add(set1.getString("DiscordRoleID") + "," + set1.getString("RoleDisplayName"));
                        }
                        set1.close();
                        statement1.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }

                    List<Role> list = member.getRoles();
                    boolean isfound = false;
                    for (Role role : list) {
                        if (isfound){
                            continue;
                        }
                        for (String r : roleList) {
                            if (isfound){
                                continue;
                            }
                            String[] split = r.split(",");
                            if (role.getId().equals(split[0])) {
                                permName = split[1];
                                roleId = role.getId();
                                isfound = true;
                            }
                        }

                    }

                    if (roleId != null) {
                        if (!roleId.equals(oldId)) {
                            builder.setColor(Color.GREEN);
                            builder.setDescription("" +
                                    "以下の変更を検知しました。\n" +
                                    "\n" +
                                    "DiscordName : `" + member.getUser().getName() + "`\n" +
                                    "DiscordID : `" + discordUserID + "`\n" +
                                    "Minecraft : https://mine.ly/" + minecraftId + "\n" +
                                    "\n" +
                                    "旧階級ロール : `" + displayName + "` (ID:" + oldId + ")\n" +
                                    "新階級ロール : `" + permName + "` (ID:" + roleId + ")"
                            );
                            builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                            channel.sendMessageEmbeds(builder.build()).queue();
                            PreparedStatement statement1 = con.prepareStatement("SELECT * FROM RoleList WHERE DiscordRoleID = ?");
                            statement1.setString(1, roleId);
                            ResultSet set1 = statement1.executeQuery();
                            String newUuid = "";
                            if (set1.next()){
                                newUuid = set1.getString("UUID");
                            }
                            set1.close();
                            statement1.close();

                            PreparedStatement statement2 = con.prepareStatement("UPDATE `UserList` SET `RoleUUID`= ? WHERE UUID = ?");
                            statement2.setString(1, newUuid);
                            statement2.setString(2, uuid);
                            statement2.execute();
                            statement2.close();
                        }
                    }
                }

                //builder.setDescription("同期終了しました。 (" + i + " 件)");
                //builder.setFooter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                //builder.setColor(Color.PINK);
                //channel.sendMessageEmbeds(builder.build()).queue();

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
}
