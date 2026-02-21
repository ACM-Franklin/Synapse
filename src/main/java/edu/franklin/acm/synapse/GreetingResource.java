package edu.franklin.acm.synapse;

import edu.franklin.acm.synapse.bot.SynapseBot;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @Inject
    public SynapseBot bot;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Current Ping to Discord: " + bot.ping();
    }
}
