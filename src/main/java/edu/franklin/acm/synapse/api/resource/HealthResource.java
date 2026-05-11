package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.api.dto.HealthDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Public liveness probe. Returns {@code {"status":"ok"}} as long as the JVM
 * is serving HTTP. Intentionally does not depend on DB or Discord — those
 * states are reflected in {@code /api/system/status} instead.
 */
@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public HealthDto get() {
        return HealthDto.ok();
    }
}
