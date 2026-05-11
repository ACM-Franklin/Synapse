package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.dto.RuleDto;
import edu.franklin.acm.synapse.api.service.RuleQueryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/rules")
@Produces(MediaType.APPLICATION_JSON)
public class RulesResource {

    @Inject AuthGuard guard;
    @Inject RuleQueryService rules;

    @GET
    public java.util.List<RuleDto> list() {
        guard.requireAdmin();
        return rules.findAll();
    }

    @GET
    @Path("/{ruleId}")
    public RuleDto get(@PathParam("ruleId") long ruleId) {
        guard.requireAdmin();
        RuleDto dto = rules.findById(ruleId);
        if (dto == null) {
            throw new WebApplicationException("Rule not found", Response.Status.NOT_FOUND);
        }
        return dto;
    }
}
