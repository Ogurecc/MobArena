package com.garbagemule.MobArena.commands.user;

import com.garbagemule.MobArena.ArenaClass;
import com.garbagemule.MobArena.ClassLimitManager;
import com.garbagemule.MobArena.Msg;
import com.garbagemule.MobArena.commands.Command;
import com.garbagemule.MobArena.commands.CommandInfo;
import com.garbagemule.MobArena.commands.Commands;
import com.garbagemule.MobArena.framework.Arena;
import com.garbagemule.MobArena.framework.ArenaMaster;
import com.garbagemule.MobArena.util.ClassChests;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandInfo(
    name    = "class",
    pattern = "(pick)?class",
    usage   = "/ma class <class>",
    desc    = "pick a class",
    permission = "mobarena.use.class"
)
public class PickClassCommand implements Command
{
    @Override
    public boolean execute(ArenaMaster am, CommandSender sender, String... args) {
        if (!Commands.isPlayer(sender)) {
            am.getGlobalMessenger().tell(sender, Msg.MISC_NOT_FROM_CONSOLE);
            return true;
        }

        // Require a class name
        if (args.length != 1) return false;
        
        // Unwrap the sender
        Player p = Commands.unwrap(sender);

        // Make sure the player is in an arena
        Arena arena = am.getArenaWithPlayer(p);
        if (arena == null) return true;

        // Make sure the player is in the lobby
        if (!arena.inLobby(p)) {
            arena.getMessenger().tell(p, Msg.MISC_NO_ACCESS);
            return true;
        }

        // Grab the ArenaClass, if it exists
        String lowercase = args[0].toLowerCase();
        ArenaClass ac = am.getClasses().get(lowercase);
        if (ac == null) {
            arena.getMessenger().tell(p, Msg.LOBBY_NO_SUCH_CLASS, lowercase);
            return true;
        }

        // Check for permission.
        if (!am.getPlugin().has(p, "mobarena.classes." + lowercase) && !lowercase.equals("random")) {
            arena.getMessenger().tell(p, Msg.LOBBY_CLASS_PERMISSION);
            return true;
        }

        // Grab the old ArenaClass, if any, same => ignore
        ArenaClass oldAC = arena.getArenaPlayer(p).getArenaClass();
        if (ac.equals(oldAC)) return true;

        // If the new class is full, inform the player.
        ClassLimitManager clm = arena.getClassLimitManager();
        if (!clm.canPlayerJoinClass(ac)) {
            arena.getMessenger().tell(p, Msg.LOBBY_CLASS_FULL);
            return true;
        }

        // Check price, balance, and inform
        double price = ac.getPrice();
        if (price > 0D) {
            if (!am.getPlugin().hasEnough(p, price)) {
                arena.getMessenger().tell(p, Msg.LOBBY_CLASS_TOO_EXPENSIVE, am.getPlugin().economyFormat(price));
                return true;
            }
        }

        // Otherwise, leave the old class, and pick the new!
        clm.playerLeftClass(oldAC, p);
        clm.playerPickedClass(ac, p);

        if (!lowercase.equalsIgnoreCase("random")) {
            if (arena.getSettings().getBoolean("use-class-chests", false)) {
                if (ClassChests.assignClassFromStoredClassChest(arena, p, ac)) {
                    return true;
                }
                // No linked chest? Fall through to config-file
            }
            arena.assignClass(p, lowercase);
            arena.getMessenger().tell(p, Msg.LOBBY_CLASS_PICKED, arena.getClasses().get(lowercase).getConfigName());
            if (price > 0D) {
                arena.getMessenger().tell(p, Msg.LOBBY_CLASS_PRICE, am.getPlugin().economyFormat(price));
            }
        } else {
            arena.addRandomPlayer(p);
            arena.getMessenger().tell(p, Msg.LOBBY_CLASS_RANDOM);
        }
        return true;
    }
}
