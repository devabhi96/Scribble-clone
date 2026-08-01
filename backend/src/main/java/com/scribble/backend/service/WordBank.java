package com.scribble.backend.service;

import org.springframework.stereotype.Component;
import java.util.Collections;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Component
public class WordBank {

    private static final List<String> WORDS = List.of(
            "apple","guitar","elephant","castle","rainbow","bicycle","mountain","pizza","robot","umbrella","dragon","spaceship"
    );

    private final SecureRandom random = new SecureRandom();

    public List<String> getRandomOptions(int count){
        List<String> shuffled = new ArrayList<>(WORDS);
        Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }


}
