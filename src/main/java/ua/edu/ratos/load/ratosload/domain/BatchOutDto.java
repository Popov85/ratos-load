package ua.edu.ratos.load.ratosload.domain;

import lombok.Getter;
import java.io.Serializable;

@Getter
public class BatchOutDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int batchesLeft;
}
