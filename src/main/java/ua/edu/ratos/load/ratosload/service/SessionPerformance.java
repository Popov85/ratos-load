package ua.edu.ratos.load.ratosload.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ua.edu.ratos.load.ratosload.config.AppProperties;
import ua.edu.ratos.load.ratosload.domain.BatchOutDto;
import ua.edu.ratos.load.ratosload.domain.ResultOutDto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

@Slf4j
@Component
public class SessionPerformance {

    @Autowired
    private AppProperties prop;

    @Autowired
    private Rnd rnd;

    private static final String START_URL = "http://localhost:8090/student/session/start?schemeId=";
    private static final String NEXT_URL = "http://localhost:8090/student/session/next";
    private static final String FINISH_URL = "http://localhost:8090/student/session/finish-batch";

    @Autowired
    private RestTemplateFactory restTemplateFactory;

    @Autowired
    @Qualifier("next")
    private RestTemplate restTemplate;


    @Getter
    @Setter
    @ToString(exclude = {"totalMemory", "freeMemory"})
    @AllArgsConstructor
    private static final class Stat{
        private int task;
        private String scheme;
        private long startTiming;
        // in Mb
        private long totalMemory;
        private long freeMemory;

        private ResultStat resultStat;
    }


    @Getter
    @Setter
    @AllArgsConstructor
    private static final class ResultStat{
        private ResultOutDto result;
        private List<Long> timings;
        // Next timings
        private long minTiming;
        private long maxTiming;
        private double avgTiming;
        private double less50msTiming;
        private double less1sTiming;

        @Override
        public String toString() {
            return "ResultStat{" +
                    "result=" + (result!=null ? result.getPercent(): null) +
                    ", questions=" + timings.size() +
                    ", minTiming=" + minTiming +
                    ", maxTiming=" + maxTiming +
                    ", avgTiming=" + Math.round(avgTiming) +
                    ", less50msTiming=" + Math.round(less50msTiming) +
                    ", less1sTiming=" + Math.round(less1sTiming) +
                    ", timings=" + timings +
                    '}';
        }
    }

    public void doPerformanceTry(Long schemeId, int threads) {
        List<Callable<Stat>> tasks = getStartTasks(schemeId, threads);
        log.info("Tasks has been prepared, quantity = {}", tasks.size());
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
            }
            long finish = System.nanoTime();
            // print results
            printResults(start, futures, finish);
        } catch (Exception e) {
            log.error("Error executing the list of tasks, message = {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private List<Callable<Stat>> getStartTasks(Long schemeId, int threads) {
        List<Callable<Stat>> tasks = new ArrayList<>();
        IntStream.range(1, threads+1).forEach(i -> {
            tasks.add(() -> {
                String login = "name" + i + "." + "surname" + i + "@example.com";
                String password = "name&surname" + i;

                // With basic authorization
                RestTemplate restTemplate = restTemplateFactory.getRestTemplateStart(login, password);

                String id;// Select schemeId
                if (schemeId!=null) {
                    id = String.valueOf(schemeId);
                } else {
                    // random selection
                    long rnd = this.rnd.rnd(prop.getSchemeIdMin(), prop.getSchemeIdMax());
                    id = String.valueOf(rnd);
                }
                // Actual test begins
                long start = System.nanoTime();
                ResponseEntity<BatchOutDto> response = restTemplate.getForEntity(START_URL+id, BatchOutDto.class);
                long finish = System.nanoTime();

                long timing = (finish - start) / 1000000;

                long totalMemory = Runtime.getRuntime().totalMemory()/1000000;
                long freeMemory = Runtime.getRuntime().freeMemory()/1000000;

                // Launch loop of request-responses
                HttpHeaders headers = response.getHeaders();
                String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);
                log.debug("Cookies are = {}", cookie);
                ResultStat resultStat = launchNextLoop(i, cookie, response);
                Stat stat = new Stat(i, id, timing, totalMemory, freeMemory, resultStat);
                return stat;
            });
        });
        return tasks;
    }

    private ResultStat launchNextLoop(int i, String cookie, ResponseEntity<BatchOutDto> batch) {
        List<Long> nextResponses = new ArrayList<>();
        BatchOutDto currentBatch = batch.getBody();
        int batchesLeft = currentBatch.getBatchesLeft();
        int batchCounter = 0;

        while (batchesLeft >0) {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", cookie);

            String requestJson = "{\"responses\" : {}}";
            HttpEntity<String> entity = new HttpEntity(requestJson, headers);

            // actual test begins
            long startNext = System.nanoTime();
            ResponseEntity<BatchOutDto> response = restTemplate.postForEntity(NEXT_URL, entity, BatchOutDto.class);
            long finishNext = System.nanoTime();
            nextResponses.add((finishNext-startNext)/1000000);
            try {
                long randomDelay = this.rnd.rnd(prop.getNextDelayMin().toMillis(), prop.getNextDelayMax().toMillis());
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                log.error("Interrupted thread...");
                throw new RuntimeException(e);
            }
            currentBatch = response.getBody();
            batchesLeft = currentBatch.getBatchesLeft();
            log.debug("Batch number = {} performed..., batches left = {}", batchCounter, currentBatch.getBatchesLeft());
            batchCounter++;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Cookie", cookie);

        String requestJson = "{\"responses\" : {}}";
        HttpEntity<String> entity = new HttpEntity(requestJson, headers);

        long startFinish = System.nanoTime();
        ResponseEntity<ResultOutDto> result = restTemplate.postForEntity(FINISH_URL, entity, ResultOutDto.class);
        long finishFinish = System.nanoTime();
        nextResponses.add((finishFinish-startFinish)/1000000);
        ResultOutDto resultOutDto = result.getBody();
        log.info("Finish for task = {}, result = {}", i, result.getBody());
        ResultStat resultStat = calculateTimings(resultOutDto, nextResponses);
        return resultStat;
    }


    private ResultStat calculateTimings(ResultOutDto resultOutDto, List<Long> timings) {
        long sum = 0;
        long min = timings.get(0);
        long max = timings.get(0);
        long sumLess50ms = 0;
        long sumLess1s = 0;
        for (Long timing : timings) {
            if (timing>max) max = timing;
            if (timing<min) min = timing;
            if (timing<=50) sumLess50ms++;
            if (timing<=1000) sumLess1s++;
            sum+=timing;
        }
        double avg = sum/(double)timings.size();
        double less50msTiming = sumLess50ms/(double)timings.size()*100;
        double less1sTiming = sumLess1s/(double)timings.size()*100;
        return new ResultStat(resultOutDto, timings, min, max, avg, less50msTiming, less1sTiming);
    }


    private List<Future<Stat>> startAllInBatches(List<Callable<Stat>> tasks) throws InterruptedException {
        List<Future<Stat>> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<Stat> result = executorService.submit(tasks.get(i));
            results.add(result);
            log.info("Task number = {} has been submitted", i);
            if (i% prop.getBatchSize()==0) {
                long randomDelay = this.rnd.rnd(prop.getBatchDelayMin().toMillis(), prop.getBatchDelayMax().toMillis());
                Thread.sleep(randomDelay);
            }
            Thread.sleep(prop.getThreadDelay().toMillis());
            executorService.shutdown();
        }
        return results;
    }


    private void printResults(long start, List<Future<Stat>> futures, long finish) throws Exception {

        log.info("------------All results----------");

        Stat first = futures.get(0).get();

        long minTiming = first.getStartTiming();
        long maxTiming = first.getStartTiming();
        long sumTiming = 0;
        int sumTimingLess1s = 0;
        int sumTimingLess3s = 0;

        long minNextTiming = first.getResultStat().getMinTiming();
        long maxNextTiming = first.getResultStat().getMaxTiming();

        long sumAvgNextTiming = 0;
        int sumNextTimingLess50ms = 0;
        int sumNextTimingLess1s = 0;

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
                long timing = stat.getStartTiming();
                sumTiming+=timing;
                if (timing<=1000) sumTimingLess1s++;
                if (timing<=3000) sumTimingLess3s++;

                ResultStat resultStat = stat.getResultStat();

                if (stat.getResultStat().getMinTiming()<minNextTiming) minNextTiming = stat.getResultStat().getMinTiming();
                if (stat.getResultStat().getMaxTiming()>maxNextTiming) maxNextTiming = stat.getResultStat().getMaxTiming();

                sumAvgNextTiming+=resultStat.getAvgTiming();
                sumNextTimingLess50ms+=resultStat.getLess50msTiming();
                sumNextTimingLess1s+=resultStat.getLess1sTiming();

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
        log.info("----------------All statistics------------");

        log.info("--------------------Start------------------");

        log.info("Test took = {} min", (finish-start)/60000000000L);
        log.info("Min start startTiming = {} ms", minTiming);
        log.info("Max start startTiming = {} ms", maxTiming);
        log.info("Avg start startTiming = {} ms", sumTiming/(double)futures.size());
        log.info("Less 1s start startTiming = {} %", sumTimingLess1s/(double) futures.size()*100);
        log.info("Less 3s start startTiming = {} %", sumTimingLess3s/(double) futures.size()*100);

        log.info("--------------------Next------------------");

        log.info("Min next startTiming = {} ms", minNextTiming);
        log.info("Max next startTiming = {} ms", maxNextTiming);
        log.info("Avg next startTiming = {} ms", sumAvgNextTiming/(double)futures.size());
        log.info("Avg less 50ms next startTiming = {} %", sumNextTimingLess50ms/(double)futures.size());
        log.info("Avg less 1s next startTiming = {} %", sumNextTimingLess1s/(double)futures.size());

        log.info("-------------Additional statistics------------");

        log.info("Min total memory = {} Mb", minTotalMemory);
        log.info("Max total memory = {} Mb", maxTotalMemory);
        log.info("Avg total memory = {} Mb", sumTotalMemory/futures.size());
        log.info("Min free memory = {} Mb", minFreeMemory);
        log.info("Max free memory = {} Mb", maxFreeMemory);
        log.info("Avg free memory = {} Mb", sumFreeMemory/futures.size());
    }
}
