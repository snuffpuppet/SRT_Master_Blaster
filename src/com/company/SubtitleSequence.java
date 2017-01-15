package com.company;

import jdk.nashorn.internal.runtime.ParserException;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adam on 15/01/17.
 */
public class SubtitleSequence {
    public class Subtitle {
        public int sequence = 0;
        public long startTime = 0;
        public long endTime = 0;
        public String text = "";

        Subtitle() {
        }
    }

    long stringToTimestamp(String s) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss,SSS");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date d = dateFormat.parse(s);
            long millisecs = d.getTime();
            return millisecs;
        }
        catch (Exception e) {
            throw new ParserException(
                    "Unexpected string in input: " + s);
        }

    }

    private LinkedList<Subtitle> subtitles;

    public LinkedList<Subtitle> getSubtitles() {
        return this.subtitles;
    }

    SubtitleSequence(LinkedList<Tokeniser.Token> tokens) {

        this.subtitles = new LinkedList<Subtitle>();
        Subtitle sub = new Subtitle();

        Iterator<Tokeniser.Token> x = tokens.listIterator();

        // Iterate through tokens constructing subtitle objects
        // Add them to list when we start a new subtitle or if we run out of subtitles
        while (x.hasNext()) {
            boolean match = true;
            Tokeniser.Token tok = x.next();
            // SRT format is index, TS_start, '-->', TS_end, text, text, text ...

            switch (tok.token) {
                case 1:
                    if (sub.sequence > 0) { // last object finished, pop this one in the list
                        this.subtitles.addLast(sub);
                        sub = new Subtitle();
                    }
                    sub.sequence = Integer.parseInt(tok.sequence);
                    break;
                case 2:
                    sub.startTime = this.stringToTimestamp(tok.sequence);
                    tok = x.next(); // "-->"
                    if (tok.token != 3) {
                        match = false;
                        break;
                    }
                    tok = x.next(); // end timestamp
                    if (tok.token != 2) {
                        match = false;
                        break;
                    }
                    sub.endTime = this.stringToTimestamp(tok.sequence);
                    break;
                case 4: // subtitle text
                    String sText = tok.sequence;
                    if (sub.text.length() > 0) // already some text, add a new line
                        sub.text += "\n" + sText;
                    else
                        sub.text = sText;
                    break;
                default:
                    match = false;
                    break;
            }

            if (!match) {
                throw new ParserException(
                        "Unexpected token in input: " + tok.sequence);
            }
        }
        subtitles.addLast(sub);
    }
}

