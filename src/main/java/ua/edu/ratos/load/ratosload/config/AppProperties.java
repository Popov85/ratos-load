package ua.edu.ratos.load.ratosload.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@ToString
@Component
@ConfigurationProperties("ratos.load")
public class AppProperties {

    private int schemeIdMin;
    private int schemeIdMax;

    private int batchSize;

    private Duration threadDelay;

    private Duration batchDelayMin;
    private Duration batchDelayMax;

    // for session test only
    private Duration nextDelayMin;
    private Duration nextDelayMax;

}
