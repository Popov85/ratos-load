package ua.edu.ratos.load.ratosload.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ratos.load.ratosload.config.AppProperties;
import ua.edu.ratos.load.ratosload.service.SessionPerformance;
import ua.edu.ratos.load.ratosload.service.StartPerformance;

@Slf4j
@RestController
public class PerformanceController {

    @Autowired
    private AppProperties prop;

    @Autowired
    private StartPerformance startPerformance;

    @Autowired
    private SessionPerformance sessionPerformance;

    @GetMapping(value = "/start-performance", params = {"threads"})
    public ResponseEntity<String> start(@RequestParam int threads) {
        log.info("Launch start test with threads = {}, with min schemeId = {}, with max schemeId = {}, with batch size = {}, with min batch delay = {}, with max batch delay = {}, with thread delay = {}"
                , threads, prop.getSchemeIdMin(), prop.getSchemeIdMax(), prop.getBatchSize(), prop.getBatchDelayMin().toMillis(), prop.getBatchDelayMax().toMillis(), prop.getThreadDelay().toMillis());
        startPerformance.doPerformanceTry(threads);
        return ResponseEntity.ok("Launched start test with threads = "+threads);
    }

    @GetMapping(value = "/session-performance", params = {"threads"})
    public ResponseEntity<String> session(@RequestParam int threads) {
        log.info("Launch session test with threads = {}, with min schemeId = {}, with max schemeId = {}, with batch size = {}, with min batch delay = {}, with max batch delay = {}, with thread delay = {}"
                , threads, prop.getSchemeIdMin(), prop.getSchemeIdMax(), prop.getBatchSize(), prop.getBatchDelayMin().toMillis(), prop.getBatchDelayMax().toMillis(), prop.getThreadDelay().toMillis());
        sessionPerformance.doPerformanceTry(null, threads);
        return ResponseEntity.ok("Launched session test with threads = "+threads);
    }

    @GetMapping(value = "/session-performance", params = {"schemeId", "threads"})
    public ResponseEntity<String> session(@RequestParam Long schemeId, @RequestParam int threads) {
        log.info("Launch session test with threads = {}, with min schemeId = {}, with max schemeId = {}, with batch size = {}, with min batch delay = {}, with max batch delay = {}, with thread delay = {}"
                , threads, prop.getSchemeIdMin(), prop.getSchemeIdMax(), prop.getBatchSize(), prop.getBatchDelayMin().toMillis(), prop.getBatchDelayMax().toMillis(), prop.getThreadDelay().toMillis());
        sessionPerformance.doPerformanceTry(schemeId, threads);
        return ResponseEntity.ok("Launched sessionId = "+schemeId+" session test with threads = "+threads);
    }

}
