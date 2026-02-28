package edu.franklin.acm.synapse.activity;

import edu.franklin.acm.synapse.activity.migrations.MigrationDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.member.MemberRoleDao;
import edu.franklin.acm.synapse.activity.message.MessageAttachmentDao;
import edu.franklin.acm.synapse.activity.message.MessageEventDao;
import edu.franklin.acm.synapse.activity.message.MessageReactionDao;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleEvaluationDao;
import edu.franklin.acm.synapse.activity.rules.RuleOutcomeDao;
import edu.franklin.acm.synapse.activity.rules.RulePredicateDao;
import edu.franklin.acm.synapse.activity.voice.VoiceSessionDao;
import io.agroal.api.AgroalDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer factory for JDBI data access objects.
 * 
 * <p>Initializes the JDBI instance with the application's datasource and the
 * {@link SqlObjectPlugin}, then produces {@code @ApplicationScoped} DAO beans
 * for dependency injection throughout the application.
 */
@ApplicationScoped
public class DaoProducer {

    @Inject
    AgroalDataSource ds;

    private Jdbi jdbi;

    /**
     * Initializes the JDBI instance with the datasource and enables SQL object
     * mapping via the {@link SqlObjectPlugin}.
     */
    @PostConstruct
    public void init() {
        jdbi = Jdbi.create(ds);
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    // Bean Export for JDBI itself, for more advanced SQL work like migrations.
    @Produces
    @ApplicationScoped
    public Jdbi jdbi() {
        return jdbi;
    }

    // DAO Producers: each method creates an application-scoped, on-demand JDBI
    // proxy for the corresponding DAO interface.

    @Produces
    @ApplicationScoped
    public GuildMetadataDao guildMetadataDao() {
        return jdbi.onDemand(GuildMetadataDao.class);
    }

    @Produces
    @ApplicationScoped
    public MemberDao memberDao() {
        return jdbi.onDemand(MemberDao.class);
    }

    @Produces
    @ApplicationScoped
    public EventDao eventDao() {
        return jdbi.onDemand(EventDao.class);
    }

    @Produces
    @ApplicationScoped
    public CategoryDao categoryDao() {
        return jdbi.onDemand(CategoryDao.class);
    }

    @Produces
    @ApplicationScoped
    public ChannelDao channelDao() {
        return jdbi.onDemand(ChannelDao.class);
    }

    @Produces
    @ApplicationScoped
    public MessageEventDao messageEventDao() {
        return jdbi.onDemand(MessageEventDao.class);
    }

    @Produces
    @ApplicationScoped
    public MessageAttachmentDao messageAttachmentDao() {
        return jdbi.onDemand(MessageAttachmentDao.class);
    }

    @Produces
    @ApplicationScoped
    public MessageReactionDao messageReactionDao() {
        return jdbi.onDemand(MessageReactionDao.class);
    }

    @Produces
    @ApplicationScoped
    public MemberRoleDao memberRoleDao() {
        return jdbi.onDemand(MemberRoleDao.class);
    }

    @Produces
    @ApplicationScoped
    public VoiceSessionDao voiceSessionDao() {
        return jdbi.onDemand(VoiceSessionDao.class);
    }

    @Produces
    @ApplicationScoped
    public MigrationDao migrationDao() {
        return jdbi.onDemand(MigrationDao.class);
    }

    @Produces
    @ApplicationScoped
    public RuleDao ruleDao() {
        return jdbi.onDemand(RuleDao.class);
    }

    @Produces
    @ApplicationScoped
    public RulePredicateDao rulePredicateDao() {
        return jdbi.onDemand(RulePredicateDao.class);
    }

    @Produces
    @ApplicationScoped
    public RuleOutcomeDao ruleOutcomeDao() {
        return jdbi.onDemand(RuleOutcomeDao.class);
    }

    @Produces
    @ApplicationScoped
    public RuleEvaluationDao ruleEvaluationDao() {
        return jdbi.onDemand(RuleEvaluationDao.class);
    }

    @Produces
    @ApplicationScoped
    public SeasonDao seasonDao() {
        return jdbi.onDemand(SeasonDao.class);
    }
}
