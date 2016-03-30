package me.hqm.mcauth4pe;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.level.Location;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import org.spacehq.mc.auth.GameProfile;
import org.spacehq.mc.auth.SessionService;
import org.spacehq.mc.auth.UserAuthentication;
import org.spacehq.mc.auth.exception.AuthenticationException;
import org.spacehq.mc.auth.exception.ProfileException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


public class MCA4PEPlugin extends PluginBase implements Listener {

    static Map<String, UUID> VERIFIED = new HashMap<>();
    static Map<String, Location> UNVERIFIED = new HashMap<>();
    Location THE_BOX;
    
    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        getServer().getPluginManager().registerEvents(this, this);
        String[] theBox = getConfig().getString("the_box", "0.0;121.0;0.0").split(";");
        try {
            THE_BOX = new Location(
                    Double.valueOf(theBox[0]),
                    Double.valueOf(theBox[1]),
                    Double.valueOf(theBox[2])
            );
        } catch (Exception oops) {
            getLogger().warning("The box location isn't configured correctly, defaulting to \"0.0;121.0;0.0\".");
            THE_BOX = new Location(0.0, 121.0, 0.0);
        }
    }

    public CommandResult onCommand(CommandSender sender, Command command, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equals("login")) {
                if (args.length < 2) {
                    return CommandResult.INVALID_SYNTAX;
                }
                if (!UNVERIFIED.containsKey(player.getName())) {
                    player.sendMessage(TextFormat.RED + "You are already authenticated.");
                    return CommandResult.SUCCESS;
                }
                String email = args[0];
                if (!email.contains("@")) {
                    return CommandResult.INVALID_SYNTAX;
                }
                String password = args[1];
                if (auth(player, email, password)) {
                    return CommandResult.SUCCESS;
                } else {
                    player.sendMessage(TextFormat.RED + "Unable to authenticate, try again.");
                    return CommandResult.QUIET_ERROR;
                }
            } else if (command.getName().equals("setbox")) {
                Location loc = player.getLocation();
                getConfig().set("the_box", loc.getX() + ";" + loc.getY() + ";" + loc.getZ());
                saveConfig();
                player.sendMessage(TextFormat.YELLOW + "The Box's location has been updated.");
                return CommandResult.SUCCESS;
            }
            return CommandResult.INVALID_SYNTAX;
        }
        return CommandResult.PLAYER_ONLY;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!UNVERIFIED.containsKey(player.getName())) {
            UNVERIFIED.put(player.getName(), player.getLocation());
        }
        event.setJoinMessage(TextFormat.ITALIC + TextFormat.GRAY + "**An unverified player has joined the game**");
        player.setNameTag("Unverified");
        player.setDisplayName("Unverified");
        player.setGamemode(Player.ADVENTURE);
        player.sendMessage(TextFormat.DARK_AQUA + "You must log in to continue. Use " + TextFormat.ITALIC +
                "/login <email> <password>");
        player.teleportImmediate(THE_BOX);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (UNVERIFIED.containsKey(player.getName())) {
            event.setQuitMessage(TextFormat.ITALIC + TextFormat.GRAY + "**An unverified player has left the game**");
            player.teleportImmediate(UNVERIFIED.remove(player.getName()));
        }
        if (VERIFIED.containsKey(player.getName())) {
            VERIFIED.remove(player.getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (UNVERIFIED.containsKey(player.getName())) {
            event.setCancelled();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (UNVERIFIED.containsKey(player.getName())) {
            player.sendMessage(TextFormat.DARK_AQUA + "You must log in to continue. Use " + TextFormat.ITALIC +
                    "/login <email> <password>");
            event.setCancelled();
        }
        event.getRecipients().removeAll(UNVERIFIED.keySet().stream().map(name -> getServer().getPlayer(name)).
                collect(Collectors.toList()));
    }

    /*
     * From CensoredLib.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        CommandResult result = onCommand(sender, command, args);
        switch (result) {
            case SUCCESS:
            case QUIET_ERROR:
                break;
            case INVALID_SYNTAX:
                sender.sendMessage(TextFormat.RED + "Invalid syntax, please try again.");
                return false;
            case NO_PERMISSIONS:
                sender.sendMessage(TextFormat.RED + "You don't have the permissions to use this command.");
                break;
            case CONSOLE_ONLY:
                sender.sendMessage(TextFormat.RED + "This command is for the console only.");
                break;
            case PLAYER_ONLY:
                sender.sendMessage(TextFormat.RED + "This command can only be used by a player.");
                break;
            case ERROR:
                sender.sendMessage(TextFormat.RED + "An error occurred, please check the console.");
                break;
            case UNKNOWN:
            default:
                sender.sendMessage(TextFormat.RED + "The command can't run for some unknown reason.");
                break;
        }
        return true;
    }

    private boolean auth(Player player, String email, String password) {
        String clientToken = UUID.randomUUID().toString();
        UserAuthentication auth = new UserAuthentication(clientToken, Proxy.NO_PROXY);
        auth.setUsername(email);
        auth.setPassword(password);

        try {
            auth.login();
        } catch (AuthenticationException oops) {
            return false;
        }

        SessionService service = new SessionService();
        for (GameProfile profile : auth.getAvailableProfiles()) {
            try {
                service.fillProfileProperties(profile);
                String name = profile.getName();
                player.setNameTag(name);
                player.setDisplayName(name);
                try {
                    player.setSkin(getSkin(name));
                } catch (IOException oops) {
                    oops.printStackTrace();
                }
                if (VERIFIED.containsValue(profile.getId())) {
                    String kick = VERIFIED.entrySet().stream().filter(entry -> entry.getValue().
                            equals(profile.getId())).findAny().get().getKey();
                    getServer().getPlayer(kick).kick("You've authenticated from another location.");
                }
                VERIFIED.put(player.getName(), profile.getId());
                player.setGamemode(getServer().getDefaultGamemode());
                player.teleportImmediate(UNVERIFIED.remove(player.getName()));
                getServer().broadcastMessage(TextFormat.YELLOW + player.getDisplayName() + " has authenticated");
                return true;
            } catch (ProfileException oops) {
                oops.printStackTrace();
            }
        }
        return false;
    }

    // -- API -- //

    public static Skin getSkin(String userName) throws IOException, NullPointerException {
        URL imageUrl = new URL("https://mcapi.ca/skin/file/" + userName);
        BufferedImage image = ImageIO.read(imageUrl);
        if (image.getWidth() == 64 && (image.getHeight() == 32 || image.getHeight() == 64)) {
            return new Skin(imageUrl, image.getHeight() == 32 ? Skin.MODEL_STEVE : Skin.MODEL_ALEX);
        }
        throw new NullPointerException("Image is not a valid skin.");
    }

    public static UUID getMojangId(Player player) {
        return VERIFIED.getOrDefault(player.getUniqueId(), null);
    }
}
