package me.gallowsdove.foxymachines.commands;

import io.github.mooy1.infinitylib.commands.SubCommand;
import me.gallowsdove.foxymachines.abstracts.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import java.util.List;

public class ListallCommand extends SubCommand {
    public ListallCommand() {
        super("listall", "Lists all Custom Mobs from FoxyMachines", "foxymachines.admin");
    }

    @Override
    protected void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length != 0) {
            sender.sendMessage(Component.text("Usage: /foxy listall", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        CustomMob.debug();
    }

    @Override
    protected void complete(@Nonnull CommandSender commandSender, @Nonnull String[] strings, @Nonnull List<String> list) { }
}