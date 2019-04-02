package ua.edu.ratos.load.ratosload.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ua.edu.ratos.load.ratosload.config.AppProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

@Slf4j
@Component
public class StartPerformance {

    private static final String START_URL = "http://localhost:8090/student/session/start?schemeId=";

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private Rnd rnd;

    @Autowired
    private RestTemplateFactory restTemplateFactory;

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    private static final class Stat{

        private int task;
        private long scheme;
        private long timing;
        // in Mb
        private long totalMemory;
        private long freeMemory;
    }

    public void doPerformanceTry(int threads) {
        List<Callable<Stat>> tasks = new ArrayList<>();
        IntStream.range(1, threads+1).forEach(i -> {
            tasks.add(() -> {
                String login = "name" + i + "." + "surname" + i + "@example.com";
                String password = "name&surname" + i;
                RestTemplate restTemplate = restTemplateFactory.getRestTemplateStart(login, password);
                // Randomly select a scheme
                long rnd = this.rnd.rnd(appProperties.getSchemeIdMin(), appProperties.getSchemeIdMax());
                String id = String.valueOf(rnd);
                // Actual test begins
                long start = System.nanoTime();
                ResponseEntity<String> response = restTemplate.getForEntity(START_URL+id, String.class);
                long finish = System.nanoTime();

                long timing = (finish - start) / 1000000;

                long totalMemory = Runtime.getRuntime().totalMemory()/1000000;
                long freeMemory = Runtime.getRuntime().freeMemory()/1000000;

                Stat stat = new Stat(i, rnd, timing, totalMemory, freeMemory);
                return stat;

            });
        });

        try {
            long start = System.nanoTime();
            List<Future<Stat>> futures = startAllInBatches(tasks);
            boolean completed = false;
            while (!completed) {
                boolean allFinished = true;
                for (Future<Stat> future : futures) {
                    if (!future.isDone()) {
                        allFinished = false;
                        break;
                    }
                }
                if (allFinished) completed = true;
                Thread.sleep(1000);
            }
            long finish = System.nanoTime();

            log.debug("------------All results----------");

            Stat first = futures.get(0).get();

            long minTiming = first.getTiming();
            long maxTiming = first.getTiming();
            long sumTiming = 0;
            int sumTimingLess1s = 0;
            int sumTimingLess3s = 0;

            long minTotalMemory = first.getTotalMemory();
            long maxTotalMemory = first.getTotalMemory();
            long sumTotalMemory = 0;

            long minFreeMemory = first.getFreeMemory();
            long maxFreeMemory = first.getFreeMemory();
            long sumFreeMemory = 0;

            for (Future<Stat> future : futures) {
                try {
                    Stat stat = future.get();
                    log.info("Stat = {}", stat);
                    long timing = stat.getTiming();
                    sumTiming+=timing;
                    if (timing<=1000) sumTimingLess1s++;
                    if (timing<=3000) sumTimingLess3s++;
                    if (timing <minTiming) minTiming = timing;
                    if (timing >maxTiming) maxTiming = timing;
                    long freeMemory = stat.getFreeMemory();
                    sumFreeMemory+=freeMemory;
                    if (freeMemory <minFreeMemory) minFreeMemory = freeMemory;
                    if (freeMemory >maxFreeMemory) maxFreeMemory = freeMemory;
                    long totalMemory = stat.getTotalMemory();
                    sumTotalMemory+=totalMemory;
                    if (totalMemory <minTotalMemory) minTotalMemory = totalMemory;
                    if (totalMemory >maxTotalMemory) maxTotalMemory = totalMemory;
                } catch (Exception e) {
                    log.error("Error getting result of execution, message = {}", e.getMessage());
                }
            }
            log.info("-------------All statistics------------");

            log.info("Test took = {} min", (finish-start)/60000000000L);

            log.info("Min startTiming = {} ms", minTiming);
            log.info("Max startTiming = {} ms", maxTiming);
            log.info("Avg startTiming = {} ms", sumTiming/futures.size());
            log.info("Less 1s startTiming = {} %", sumTimingLess1s/(double) futures.size()*100);
            log.info("Less 3s startTiming = {} %", sumTimingLess3s/(double) futures.size()*100);
            log.info("Min total memory = {} Mb", minTotalMemory);
            log.info("Max total memory = {} Mb", maxTotalMemory);
            log.info("Avg total memory = {} Mb", sumTotalMemory/futures.size());
            log.info("Min free memory = {} Mb", minFreeMemory);
            log.info("Max free memory = {} Mb", maxFreeMemory);
            log.info("Avg free memory = {} Mb", sumFreeMemory/futures.size());
        } catch (Exception e) {
            log.error("Error executing the list of tasks, message = {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<Future<Stat>> startAllInBatches(List<Callable<Stat>> tasks) throws InterruptedException {
        List<Future<Stat>> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<Stat> result = executorService.submit(tasks.get(i));
            results.add(result);
            log.info("Task = {} has been submitted", i);
            if (i%appProperties.getBatchSize()==0) {
                long randomDelay = this.rnd.rnd(appProperties.getBatchDelayMin().toMillis(), appProperties.getBatchDelayMax().toMillis());
                log.debug("Delay for task = {} = {}", i, randomDelay);
                Thread.sleep(randomDelay);
            }
            Thread.sleep(appProperties.getThreadDelay().toMillis());
            executorService.shutdown();
        }
        return results;
    }
}
