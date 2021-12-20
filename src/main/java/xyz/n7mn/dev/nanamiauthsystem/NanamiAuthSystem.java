package xyz.n7mn.dev.nanamiauthsystem;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

public final class NanamiAuthSystem extends JavaPlugin {
    private HashMap<String, TokenData> tokenList = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        JDA jda = null;
        try {
            jda = JDABuilder.createLight(getConfig().getString("DiscordToken"), GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_EMOJIS)
                    .addEventListeners(new DiscordListener(this, tokenList))
                    .enableCache(CacheFlag.EMOTE)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setActivity(Activity.playing("HelpCommand : 7m.help"))
                    .build();
        } catch (LoginException e) {
            e.printStackTrace();
            this.onDisable();
        }

        getServer().getPluginManager().registerEvents(new MinecraftListener(this, tokenList, jda), this);
        getLogger().info(this.getDescription().getName() + " "+this.getDescription().getVersion()+" 起動完了");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
