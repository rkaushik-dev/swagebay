package io.github.rkaushikdev.swagebay.api;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes a test mounts beside the service's own, to watch the frame: errors thrown from a handler,
 * and a unit of work that commits or does not. Only an application started with this class among
 * its routes has them.
 */
@RestController
public class ProbeRoutes {

    private final Work work;

    public ProbeRoutes(Work work) {
        this.work = work;
    }

    @GetMapping("/boom")
    public Map<String, String> boom() {
        throw new NotFoundError("source", "IR-9999");
    }

    @GetMapping("/busy")
    public Map<String, String> busy() {
        throw new ConflictError("that source is already out", Context.of("serial", "IR-5512"));
    }

    @GetMapping("/other")
    public Map<String, String> other() {
        throw new IllegalStateException("something else");
    }

    @PostMapping("/probe/write")
    public Map<String, Integer> write() {
        return work.apply(uow -> {
            uow.jobs().add(job(1));
            uow.flush();
            return Map.of("serial", 1);
        });
    }

    @GetMapping("/probe/count")
    public Map<String, Integer> count() {
        return work.apply(
                uow -> Map.of("count", uow.jobs().find(new JobNumber(24, 1)).isPresent() ? 1 : 0));
    }

    @PostMapping("/probe/bad")
    public Map<String, Integer> bad() {
        return work.apply(uow -> {
            uow.jobs().add(job(2));
            uow.flush();
            throw new ConflictError("changed my mind");
        });
    }

    @GetMapping("/probe/two")
    public Map<String, Integer> two() {
        return work.apply(
                uow -> Map.of("count", uow.jobs().find(new JobNumber(24, 2)).isPresent() ? 1 : 0));
    }

    private static JobRow job(int serial) {
        JobRow job = new JobRow();
        job.setYear(24);
        job.setSerial(serial);
        job.setClient("c");
        job.setSite("s");
        job.setAcceptanceCode("API 1104");
        job.setProcedureNumber("RT-001");
        job.setOpenedOn(LocalDate.of(2024, 9, 25));
        return job;
    }
}
