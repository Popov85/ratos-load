package ua.edu.ratos.load.ratosload.service;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class Rnd {

    public long rnd(long from, long to) {
        Random r = new Random();
        return r.longs(from, to).findFirst().getAsLong();
    }

}
