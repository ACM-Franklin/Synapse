package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.activity.guild.HistoricalScanJob;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanJobDao;
import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.dto.HistoricalScanDto;
import edu.franklin.acm.synapse.api.dto.HistoricalScanRequest;
import edu.franklin.acm.synapse.api.service.AdminOperationsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/scans/historical")
@Produces(MediaType.APPLICATION_JSON)
public class HistoricalScanResource {

    @Inject AuthGuard guard;
    @Inject AdminOperationsService admin;
    @Inject HistoricalScanJobDao scanJobDao;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(HistoricalScanRequest request) {
        guard.requireAdmin();
        if (request == null) {
            throw new WebApplicationException("Body required", Response.Status.BAD_REQUEST);
        }
        long jobId = admin.startHistoricalScan(request.confirm());
        HistoricalScanJob job = scanJobDao.findById(jobId);
        return Response.accepted(toDto(job)).build();
    }

    @GET
    @Path("/{jobId}")
    public HistoricalScanDto status(@PathParam("jobId") long jobId) {
        guard.requireAdmin();
        HistoricalScanJob job = scanJobDao.findById(jobId);
        if (job == null) {
            throw new WebApplicationException("Scan job not found", Response.Status.NOT_FOUND);
        }
        return toDto(job);
    }

    private static HistoricalScanDto toDto(HistoricalScanJob job) {
        return new HistoricalScanDto(
                job.id(),
                String.valueOf(job.guildExtId()),
                job.status(),
                job.startedAt(),
                job.completedAt(),
                job.checkpointCount(),
                job.errorMessage());
    }
}
