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


public class MCA4PEPlugin extends PluginBase implements Listener {

    Map<Long, Location> UNVERIFIED = new HashMap<>();
    Location THE_BOX;
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        String[] theBox = getConfig().getString("the_box", "0.0;121.0;0.0;").split(";");
        try {
            THE_BOX = new Location(
                    Double.valueOf(theBox[0]),
                    Double.valueOf(theBox[1]),
                    Double.valueOf(theBox[2])
            );
        } catch (Exception oops) {
            getLogger().warning("The box location isn't configured correctly, defaulting to \"0.0;121.0;0.0;\".");
            THE_BOX = new Location(0.0, 121.0, 0.0);
        }
    }

    public CommandResult onCommand(CommandSender sender, Command command, String[] args) {
        if(command.getName().equals("login")) {
            if(args.length < 2) {
                return CommandResult.INVALID_SYNTAX;
            }
        } else if(command.getName().equals("setbox")) {

        }
        return CommandResult.INVALID_SYNTAX;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(!UNVERIFIED.containsKey(player.getId())) {
            UNVERIFIED.put(player.getId(), player.getLocation());
        }
        player.sendMessage(TextFormat.DARK_AQUA + "You must log in to continue. Use " + TextFormat.ITALIC + "/login <email> <password>");
        player.teleportImmediate(THE_BOX);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if(UNVERIFIED.containsKey(player.getId())) {
            player.sendMessage(TextFormat.DARK_AQUA + "You must log in to continue. Use " + TextFormat.ITALIC + "/login <email> <password>");
            event.setCancelled(true);
        }
    }

    public static Skin getSkin(String userName) throws IOException, NullPointerException {
        URL imageUrl = new URL("https://mcapi.ca/skin/file/" + userName);
        BufferedImage image = ImageIO.read(imageUrl);
        if(image.getWidth() == 64 && (image.getHeight() == 32 || image.getHeight() == 64)) {
            return new Skin(imageUrl, image.getHeight() == 32 ? Skin.MODEL_STEVE : Skin.MODEL_ALEX);
        }
        throw new NullPointerException("Image is not a valid skin.");
    }

    private boolean auth(Player player, String email, String password) {
        String clientToken = UUID.randomUUID().toString();
        UserAuthentication auth = new UserAuthentication(clientToken, Proxy.NO_PROXY);
        auth.setUsername(email);
        auth.setPassword(password);

        try {
            auth.login();
        } catch(AuthenticationException oops) {
            return false;
        }

        SessionService service = new SessionService();
        for(GameProfile profile : auth.getAvailableProfiles()) {
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
                player.teleportImmediate(UNVERIFIED.remove(player.getId()));
                return true;
            } catch(ProfileException oops) {
                oops.printStackTrace();
            }
        }
        return false;
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
}
