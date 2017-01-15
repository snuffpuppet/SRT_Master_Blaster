package com.company;
import jdk.nashorn.internal.runtime.ParserException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;


public class Main {
    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public static void main(String[] args) throws IOException {
        // write your code here
        System.out.println("G'day World!");
        Tokeniser tokeniser = new Tokeniser();
        tokeniser.add("[1-9][0-9]*", 1); // index
        tokeniser.add("[0-9][0-9]:[0-9][0-9]:[0-9][0-9],[0-9][0-9][0-9]", 2); // timestamp
        tokeniser.add("-->", 3); // span
        tokeniser.add("[\\S ]+", 4); // text

        String content = new String(Files.readAllBytes(Paths.get("/home/adam/dev/SRT_Master_Blaster/Pulp.Fiction.1994.720p.BluRay.x264-SiNNERS.English.srt")));
        //String content = new String(Files.readAllBytes(Paths.get("/home/adam/dev/SRT_Master_Blaster/Pulp.test.srt")));
        //String content = new String(Files.readAllBytes(Paths.get(args[1])));


        try {
            tokeniser.tokenise(content);
/*
            for (Tokeniser.Token tok : tokeniser.getTokens()) {
                System.out.println("" + tok.token + " " + tok.sequence);
            }
            */
        } catch (ParserException e) {
            System.out.println(e.getMessage());
        }

        // Now build the sequencer structure
        SubtitleSequence srtSequence = new SubtitleSequence(tokeniser.getTokens());

        SrtScheduler srtScheduler = new SrtScheduler(srtSequence);
        System.out.println("-------------- Starting --------------");
        srtScheduler.schedule();

        System.exit(0);
    }

}
