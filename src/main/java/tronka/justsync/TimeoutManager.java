package tronka.justsync;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class TimeoutManager extends ListenerAdapter {

    private final JustSyncApplication integration;

    public TimeoutManager(JustSyncApplication integration) {
        this.integration = integration;
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        Member member = event.getMember();
        this.integration.getLinkManager().kickAccounts(member, this.integration.getConfig().kickMessages.kickOnTimeOut);
    }
}

