package xyz.n7mn.dev.nanamiauthsystem;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.List;

public class MinecraftListener implements Listener {

    private HashMap<String, TokenData> tokenList;
    private final Plugin plugin;
    private final JDA jda;

    public MinecraftListener(Plugin plugin, HashMap<String, TokenData> tokenList, JDA jda) {
        this.tokenList = tokenList;
        this.plugin = plugin;
        this.jda = jda;
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
        builder.setThumbnail("https://7.4096.xyz/7m/icon.png");

        String key = chatText.replaceAll("vy.", "");
        TokenData data = tokenList.get(key);
        if (data == null){
            e.getPlayer().sendMessage(Component.text(ChatColor.RED + "文字列が間違っています！"));
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
            e.getPlayer().sendMessage(Component.text(ChatColor.RED+"最初からやり直してください！"));
            data.getMessage().getGuild().getRolesByName("auth"+data.getUserId(), false).get(0).delete().queue();
            data.getMessage().getGuild().getTextChannelById(data.getChannelId()).delete().queue();
            return;
        }


        String permName = ""; // 認証レベル

        /* 近々 MySQL鯖に直接読み込みに行くのではなくてAPI経由にしたいよね～ */
        // おまじない (MySQL)
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
        } catch (SQLException ex){
            ex.printStackTrace();
        }

        // 権限レベル順に持ってくる
        List<String> roleList = new ArrayList<>();
        try {
            Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
            PreparedStatement statement = con.prepareStatement("SELECT * FROM RoleList ORDER BY RoleRank DESC");
            ResultSet set = statement.executeQuery();
            while (set.next()) {
                roleList.add(set.getString("DiscordRoleID")+","+set.getString("RoleDisplayName"));
            }
            set.close();
            statement.close();
            con.close();
        } catch (SQLException ex){
            ex.printStackTrace();
        }

        String roleId = ""; // あとで使う
        // Discord側ロール取得
        Guild guild = jda.getGuildById(plugin.getConfig().getString("DiscordGuildId"));
        if (guild != null){
            Member member = guild.getMemberById(data.getUserId());
            if (member == null){
                data.getMessage().getGuild().getRolesByName("auth"+data.getUserId(), false).get(0).delete().queue();
                data.getMessage().getGuild().getTextChannelById(data.getChannelId()).delete().queue();
                return;
            }

            List<Role> list = member.getRoles();
            for (Role role : list){
                boolean isfound = false;
                for (String r : roleList){
                    String[] split = r.split(",");
                    if (role.getId().equals(split[0])){
                        permName = split[1];
                        roleId = role.getId();
                        isfound = true;
                        break;
                    }
                }
                if (isfound){
                    break;
                }
            }
        }

        // 改めて認証ロールつける
        if (roleId.equals("")){
            roleId = plugin.getConfig().getString("DiscordVerifyUserRoleID");
            Role role = guild.getRoleById(roleId);
            if (role == null){
                return;
            }
            Member member = guild.getMemberById(data.getUserId());
            if (member == null) {
                return;
            }

            guild.addRoleToMember(member, role).queue();
        }

        // MySQLに書き出し
        String finalRoleId = roleId;
        try {
            Connection con = DriverManager.getConnection("jdbc:mysql://" + plugin.getConfig().getString("MySQLServer") + ":" + plugin.getConfig().getInt("MySQLPort") + "/" + plugin.getConfig().getString("MySQLDatabase") + plugin.getConfig().getString("MySQLOption"), plugin.getConfig().getString("MySQLUsername"), plugin.getConfig().getString("MySQLPassword"));
            PreparedStatement statement = con.prepareStatement("SELECT * FROM UserList WHERE MinecraftUserID = ? AND Active = 1");
            statement.setString(1, e.getPlayer().getUniqueId().toString());
            ResultSet set = statement.executeQuery();
            if (set.next()){
                set.close();
                statement.close();
                con.close();

                builder.setDescription("このMinecraftIDは認証済みです。");
                data.getMessage().editMessageEmbeds(builder.build()).queue(m -> {
                    m.addReaction("\uD83D\uDEAB").queue();
                });

                data.getMessage().getGuild().getRolesByName("auth"+data.getUserId(), false).get(0).delete().queue();
                data.getMessage().getGuild().getTextChannelById(data.getChannelId()).delete().queue();
                e.getPlayer().sendMessage(Component.text(ChatColor.RED+"すでに認証が済んでいます！"));
                return;
            }
            set.close();
            statement.close();

            new Thread(()->{
                try {

                    String role = "";
                    PreparedStatement statement1 = con.prepareStatement("SELECT * FROM `RoleList` WHERE DiscordRoleID = ?");
                    statement1.setString(1, finalRoleId);
                    ResultSet query = statement1.executeQuery();
                    if (query.next()){
                        role = query.getString("UUID");
                    }
                    query.close();
                    statement1.close();

                    PreparedStatement statement2 = con.prepareStatement("INSERT INTO `UserList`(`UUID`, `DiscordUserID`, `MinecraftUserID`, `RoleUUID`, `VerifyDate`, `Active`) VALUES (?,?,?,?,?,?)");
                    statement2.setString(1, UUID.randomUUID().toString());
                    statement2.setString(2, data.getUserId());
                    statement2.setString(3, e.getPlayer().getUniqueId().toString());
                    statement2.setString(4, role);
                    statement2.setTimestamp(5, new Timestamp(new Date().getTime()));
                    statement2.setBoolean(6, true);
                    statement2.execute();
                    statement2.close();
                    con.close();
                } catch (SQLException ex){
                    ex.printStackTrace();
                }
            }).start();
        } catch (SQLException ex){
            ex.printStackTrace();
        }

        builder.setDescription("" +
                "認証が成功しました！\n" +
                "プレーヤー名 : ||" + e.getPlayer().getName().replaceAll("\\.","") + "||"
        );

        data.getMessage().editMessageEmbeds(builder.build()).queue(m -> {
            m.clearReactions().queue();
            m.addReaction("\u2705").queue();
        });

        data.getMessage().getGuild().getRolesByName("auth"+data.getUserId(), false).get(0).delete().queue();
        data.getMessage().getGuild().getTextChannelById(data.getChannelId()).delete().queue();
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
