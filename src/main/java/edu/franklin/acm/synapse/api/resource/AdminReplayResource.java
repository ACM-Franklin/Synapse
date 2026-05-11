package edu.franklin.acm.synapse.api.resource;

import edu.franklin.acm.synapse.activity.rules.RewardReplayJob;
import edu.franklin.acm.synapse.api.auth.AuthGuard;
import edu.franklin.acm.synapse.api.dto.ReplayJobDto;
import edu.franklin.acm.synapse.api.dto.ReplayRequest;
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

@Path("/api/admin/replay/messages")
@Produces(MediaType.APPLICATION_JSON)
public class AdminReplayResource {

    @Inject AuthGuard guard;
    @Inject AdminOperationsService admin;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replay(ReplayRequest request) {
        guard.requireAdmin();
        if (request == null) {
            throw new WebApplicationException("Body required", Response.Status.BAD_REQUEST);
        }
        Integer requestedBatchSize = request.batchSize();
        int batchSize = requestedBatchSize == null ? 200 : requestedBatchSize;
        return Response.accepted(toDto(admin.startMessageRewardReplay(request.confirm(), batchSize))).build();
    }

    @GET
    @Path("/{jobId}")
    public ReplayJobDto status(@PathParam("jobId") long jobId) {
        guard.requireAdmin();
        RewardReplayJob job = admin.findRewardReplayJob(jobId);
        if (job == null) {
            throw new WebApplicationException("Reward replay job not found", Response.Status.NOT_FOUND);
        }
        return toDto(job);
    }

    private static ReplayJobDto toDto(RewardReplayJob job) {
        return new ReplayJobDto(
                job.id(),
                job.status(),
                job.batchSize(),
                job.batchesProcessed(),
                job.scannedCount(),
                job.replayedCount(),
                job.failedCount(),
                job.lastMessageId(),
                job.startedAt(),
                job.completedAt(),
                job.errorMessage());
    }
}
