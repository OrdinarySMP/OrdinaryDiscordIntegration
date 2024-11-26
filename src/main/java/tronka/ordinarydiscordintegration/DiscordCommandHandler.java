package tronka.ordinarydiscordintegration;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import tronka.ordinarydiscordintegration.linking.PlayerData;


public class DiscordCommandHandler extends ListenerAdapter {
    OrdinaryDiscordIntegration integration;
    public DiscordCommandHandler(OrdinaryDiscordIntegration integration) {
        this.integration = integration;

        integration.getGuild().updateCommands()
                .addCommands(
                        net.dv8tion.jda.api.interactions.commands.build.Commands.slash("link", "Link your minecraft with the code you got when joining")
                                .addOption(OptionType.STRING, "code", "Link code", true),
                        net.dv8tion.jda.api.interactions.commands.build.Commands.slash("linking", "Misc linking stuff")
                                .addSubcommands(new SubcommandData("get", "Retrieve linking information")
                                                .addOption(OptionType.USER, "user", "whose data to get"),
                                        new SubcommandData("unlink", "Unlink your account")
                                                .addOption(OptionType.USER, "user", "user to unlink")
                                ),
                        net.dv8tion.jda.api.interactions.commands.build.Commands.slash("list", "List the currently online players")
                ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getGuild() != integration.getGuild()) { return; }
        switch (event.getName()) {
            case "link" -> {
                var code = event.getOptions().getFirst().getAsString();
                var message = integration.getLinkManager().confirmLink(event.getUser().getIdLong(), code);
                event.reply(message).setEphemeral(true).queue();
            }
            case "list" -> {
                var names = integration.getServer().getPlayerManager()
                        .getPlayerList().stream()
                        .filter(player -> !integration.getVanishIntegration().isVanished(player))
                        .map(player -> player.getName().getLiteralString());
                event.reply(String.join(", ", names.toList())).setEphemeral(true).queue();
            }
            case "linking" -> {
                runLinkingCommand(event);
            }
        }
    }

    private void runLinkingCommand(SlashCommandInteractionEvent event) {
        var linkManager = integration.getLinkManager();

        var user = event.getOption("user", OptionMapping::getAsUser);
        if (user == null) {
            user = event.getUser();
        }
        var plink = linkManager.getDataOf(user.getIdLong());
        switch (event.getSubcommandName()) {
            case "get" -> {
                String message;
                if (plink.isPresent()) {
                    var link = plink.get();
                    message = user.getAsMention() + " is linked to " + link.getPlayerName();
                    var alts = link.getAlts();
                    if (!alts.isEmpty()) {
                        message += "\nWith " + alts.size() + " alts: " + String.join(", ", alts.stream().map(PlayerData::name).toList());
                    }

                } else {
                    message = user.getAsMention() + " is not linked to any player";
                }
                event.reply(message).setEphemeral(true).queue();
            }
            case "unlink" -> {
                if (plink.isEmpty()) {
                    var message = user.equals(event.getUser()) ?
                            "You are not linked to any player" :
                            user.getAsMention() + " is not linked to any player";
                    event.reply(message).setEphemeral(true).queue();
                    return;
                }
                if (!user.equals(event.getUser()) && !event.getMember().getPermissions().contains(Permission.MODERATE_MEMBERS)) {
                    event.reply("You do not have permission to use this command!").setEphemeral(true).queue();
                    return;
                }
                linkManager.unlinkPlayer(user.getIdLong());
                event.reply("Successfully unlinked " + user.getAsMention()).setEphemeral(true).queue();
            }
            case null -> {}
            default -> throw new IllegalStateException("Unexpected value: " + event.getSubcommandName());
        }
    }
}
