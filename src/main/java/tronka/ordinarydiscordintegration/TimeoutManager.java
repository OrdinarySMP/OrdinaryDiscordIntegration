package tronka.ordinarydiscordintegration;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;


public class TimeoutManager extends ListenerAdapter {

    private final OrdinaryDiscordIntegration integration;

    public TimeoutManager(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        Member member = event.getMember();
        integration.getLinkManager().kickAccounts(member, integration.getConfig().kickMessages.kickOnTimeOut);
    }
}

