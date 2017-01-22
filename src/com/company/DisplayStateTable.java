package com.company;

import jdk.nashorn.internal.runtime.ParserException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimeZone;

/**
 * Created by adam on 16/01/17.
 *
 * A tree based data structure to allow ~O(1) mappings from subtitle timestamps to subtitle states
 * By using a sparse array type mapping we can translate from subtitle display and removal events
 * to simply asking what the state is at any given time during the movie
 *
 * This allows us to index any specific spot in a movie and determine what state the subtitle bar should be in
 *
 */
public class DisplayStateTable {

    //long[] divisors = {100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1};
    long[] divisors = {100000000, 10000000, 1000000, 100000, 10000, 1000};
    private DecimalNode index = new DecimalNode(-1);
    private int numEvents = 0;
    private DisplayEvent[] events;


    public class DisplayStateException extends Exception {
        public DisplayStateException() { super(); };
        public DisplayStateException(String s) {
            super(s);
        }
    }

    private class TreeNode {
        int value; // the digit the the node represents
        boolean isTerminal;  // is this a terminal node
    }

    private class TerminalNode extends TreeNode {
        int eventIndex = -1;
        public TerminalNode(int value, int eventIndex) {
            this.isTerminal = true;
            this.value = value;
            this.eventIndex = eventIndex;
        }
    }

    private class DecimalNode extends TreeNode {
        TreeNode[] next = new TreeNode[10];

        public DecimalNode(int value) {
            this.value = value;
            this.isTerminal = false;
        }
    }

    public long timestampToMilliseonds(String ts) {
        // Format is HH:mm:ss,SSS
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss,SSS");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date d = dateFormat.parse(ts);
            long millisecs = d.getTime();
            return millisecs;
        } catch (Exception e) {
            throw new ParserException(
                    "Unexpected string in input: " + ts);
        }
    }

    public DisplayStateTable(LinkedList<Tokeniser.Token> tokens, int numSubtitles) {
        this.events = new DisplayEvent[2*numSubtitles+1];
        Iterator<Tokeniser.Token> x = tokens.listIterator();
        int sequence = 1;
        // Iterate through tokens creating new display states as needed and adding them to the state table

        SubtitleEvent subtitle = new SubtitleEvent(); // start of display subtitle timing
        SilenceEvent silence = new SilenceEvent(); // start of empty subtitle timing

        while (x.hasNext()) {
            boolean match = true;
            Tokeniser.Token tok = x.next();
            // SRT format is index, TS_start, '-->', TS_end, text, text, text ...
            // In translating these to states we need to create two objects for each SRT entry
            // 1. A Subtitle displayed state with the text
            // 2. A Subtitle Empty state with a blank
            // When we get to a sequence number we know we have finished an entry so we can process the
            // previously created objects
            switch (tok.token) {
                case 1:  // subtitle sequence number (ignored because we are using our own
                    if (sequence > 1) {  // we have read in some subtitles
                        if (sequence == 3) { // We have read in just one subtitle (and it's blank event)
                            // There is silence at the start so we should have a silence event initially
                            SilenceEvent initialSilence = new SilenceEvent();
                            initialSilence.seq = 0;
                            initialSilence.msecOffset = 0; // starts at the, well, start really
                            addDisplayEvent(initialSilence);
                        }
                        // Add in the subtitle and following silence
                        addDisplayEvent(subtitle);
                        addDisplayEvent(silence);
                        // create some new objects to play with and let the others go, just let them go. It's going to be allright
                        subtitle = new SubtitleEvent();
                        silence = new SilenceEvent();
                    }
                    break;
                case 2: // SRT tiestamp --> SRT timestamp
                    // the first one will be the subtitle display event time
                    subtitle.msecOffset = timestampToMilliseonds(tok.sequence);
                    subtitle.seq = sequence++;

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
                    // The second one will be the end event of the
                    silence.msecOffset = timestampToMilliseonds(tok.sequence) + 1;
                    silence.seq = sequence++;
                    break;
                case 4: // subtitle text
                    String sText = tok.sequence;
                    if (subtitle.text.length() > 0) // already some text, add a new line
                        subtitle.text += "\n" + sText;
                    else
                        subtitle.text = new String(sText);
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
        // Add in the final subtitle and following silence
        addDisplayEvent(subtitle);
        addDisplayEvent(silence);
    }

    // add event with number of milliseconds as timing
    public void addDisplayEvent(DisplayEvent event) {
        long msecOffset = event.msecOffset;
        int thisDigit;
        TreeNode node = this.index;

        // First add the event to the event array for storage
        events[event.seq] = event;
        numEvents++;

        // Then build the index to point to the event in the array
        // The index remains untouched if we have an event who's timestamp is the same at the resolution of the index
        for  (int divIndex = 0; divIndex < divisors.length; divIndex++) {
            thisDigit = (int)(msecOffset / divisors[divIndex]);
            DecimalNode decNode = (DecimalNode)node;
            if (decNode.next[thisDigit] == null) {
                // If this is the last digit, point it to the events array
                if (divIndex == divisors.length-1) {
                    decNode.next[thisDigit] = new TerminalNode(thisDigit, event.seq);
                }
                else {
                    decNode.next[thisDigit] = new DecimalNode(thisDigit);
                }
            }

            node = decNode.next[thisDigit];
            msecOffset %= divisors[divIndex];
        }
    }

    private DisplayEvent findNearestEvent(TerminalNode tnode, long msecOffset)
    {
        // Look through the event storage (rather than the index) to find the closest earliest one
        DisplayEvent indexedEvent = events[tnode.eventIndex];
        int index = tnode.eventIndex;
        if (msecOffset > indexedEvent.msecOffset) {
            while (++index < this.numEvents && msecOffset > events[index].msecOffset) {}
            return events[index-1];
        }
        else if (msecOffset < indexedEvent.msecOffset) {
            while (--index > 0 && msecOffset < events[index].msecOffset) {}
            return events[index];
        }
        else {
            // Exact match? Jackpot
            return events[index];
        }

    }

    private DisplayEvent findNearestIndex(TreeNode node, int thisDigit, long msecOffset, boolean firstScan) throws DisplayStateException
    {
        int[][] searchPatterns = {
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, // 0
                {1, 0, 2, 3, 4, 5, 6, 7, 8, 9}, // 1
                {2, 1, 3, 0, 4, 5, 6, 7, 8, 9}, // 2
                {3, 2, 4, 1, 5, 0, 6, 7, 8, 9}, // 3
                {4, 3, 5, 2, 6, 1, 7, 0, 8, 9}, // 4
                {5, 4, 6, 3, 7, 2, 8, 1, 9, 0}, // 5
                {6, 5, 7, 4, 8, 3, 9, 2, 1, 0}, // 6
                {7, 6, 8, 5, 9, 4, 3, 2, 1, 0}, // 7
                {8, 7, 9, 6, 5, 4, 3, 2, 1, 0}, // 8
                {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}  // 9
        };
        // The data structure mandates that there must be a digit in this index node so we scan for one
        // no more matching digits in the index, scan this nodes index to find the nearest and scan the event array
        if (node.isTerminal) {
            // bingo - we have the nearest point in the event array, now look for the one just earlier than the offset
            return findNearestEvent((TerminalNode)node, msecOffset);
        }
        else {
            // First use the above search pattern to find the closest branch to the one we need
            DecimalNode decNode = (DecimalNode) node;
            int[] searchPattern = searchPatterns[thisDigit];
            for (int scanDigit : searchPattern) {
                // When we change from following nodes to scanning initially, we know that there is no node
                // that matches the digit we want so we just skip it in the scan
                // After we find the nearest first branch, the sub branch scans will need to include all digits
                if (firstScan && (scanDigit == thisDigit)) {
                    continue;
                }
                if (decNode.next[scanDigit] != null) {
                    if (scanDigit < thisDigit) {
                        // If the closest match is lower then look for the highest of the lower matches
                        return findNearestIndex(decNode.next[scanDigit], 9, msecOffset, false);
                    }
                    if (scanDigit > thisDigit) {
                        // If the closest match is higher then look for the lowest of the higher matches
                        return findNearestIndex(decNode.next[scanDigit], 0, msecOffset, false);
                    } else { // scanDigit == thisDigit
                        // digits match, cool - carry on previously calculated scan pattern
                        return findNearestIndex(decNode.next[scanDigit], thisDigit, msecOffset, false);
                    }
                }
            }

        }
        throw new DisplayStateException("Corrupt DisplayStateTable - no valid branches found at first unmatched node :" + msecOffset);
    }

    // return an event that is current for the offset in milliseconds given
    // First use the index to get as close as possible to the entry
    // Then scan the sub branches of the index to find the nearest entry that was logged
    // Then we have a pointer to the Event Storage, the event we require will be close by
    // So scan up or down depending on where we are in relation to the surrounding events.
    // We always want the event just prior to the current timestamp as that is the one that is current
    public DisplayEvent getDisplayState(long msecOffset) throws DisplayStateException
    {
        TreeNode node = this.index;
        int thisDigit;
        long offsetIndex = msecOffset;

        for (int divIndex = 0; divIndex < divisors.length; divIndex++) {
            thisDigit = (int) (offsetIndex / divisors[divIndex]);
            DecimalNode decNode = (DecimalNode) node;
            if (decNode.next[thisDigit] == null) {
                return findNearestIndex(decNode, thisDigit, msecOffset, true);
            } else if (decNode.next[thisDigit].isTerminal) {
                // perfect match, send the event back
                return findNearestEvent(((TerminalNode) decNode.next[thisDigit]), msecOffset);
            }

            node = decNode.next[thisDigit];
            offsetIndex %= divisors[divIndex];
        }
        //
        throw new DisplayStateException("Corrupt DisplayStateTable - No terminal node found at end of index: " + msecOffset);
    }

    /*
    private DisplayEvent findEarlierEvent(TreeNode node, long keyElement, int keyDivIndex) throws DisplayStateException {
        if (node.isTerminal) {
            return ((TerminalNode)node).event;
        }
        else if (node.isEmpty) {
            return null;
        }
        else if (keyDivIndex < divisors.length) {
            int thisDigit = (int)(keyElement / divisors[keyDivIndex]);
            keyElement %= divisors[keyDivIndex];
            for (int newDigit = thisDigit; newDigit >= 0; newDigit--) {
                DisplayEvent event = findEarlierEvent(((DecimalNode)node).next[newDigit], keyElement, keyDivIndex+1);
                if (event != null) {
                    return event;
                }
            }
            // If we get here, we have exhausted all the current branch options and need to
        }
        else {
            // Got to end of divisors and node is not terminal. Data structure corrupted
            throw new DisplayStateException("Got to end of divisors and no TerminalNode, eek!");
        }
        return null;
    }
*/

}
