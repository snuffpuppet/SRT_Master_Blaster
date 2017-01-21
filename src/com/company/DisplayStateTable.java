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
    /*
    private class EmptyNode extends TreeNode {
        public EmptyNode() {
            this.isTerminal = false;
            this.isEmpty = true;
        }
    }
*/

    private class DecimalNode extends TreeNode {
        TreeNode[] next = new TreeNode[10];

        public DecimalNode(int value) {
            this.value = value;
            this.isTerminal = false;
            /*
            for (int i=0; i<10; i++) {
                this.next[i] = new EmptyNode();
            }
            */
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
                    /*
                    if (sub.sequence > 0) { // last object finished, pop this one in the list
                        this.subtitles.addLast(sub);
                        sub = new Subtitle();
                    }
                    sub.sequence = Integer.parseInt(tok.sequence);
                    */
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

    private DisplayEvent findNearestStorageEvent(TerminalNode tnode, long msecOffset)
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

    private DisplayEvent findNearestIndexEvent(TreeNode node, long msecOffset) throws DisplayStateException
    {
        // The data structure mandates that there must be a digit in this index node so we scan for one
        // no more matching digits in the index, scan this nodes index to find the nearest and scan the event array
        if (node.isTerminal) {
            // bingo - we have the nearest point in the event array, now look for the one just earlier than the offset
            return findNearestStorageEvent((TerminalNode)node, msecOffset);
        }
        else {
            // next level in the index, run through the digits till we find something
            for (int digit = 0; digit < 10; digit++) {
                DecimalNode decNode = (DecimalNode)node;
                if (decNode.next[digit] != null) {
                    // found a matching digit at this level, follow it to the event array
                    return findNearestIndexEvent(decNode.next[digit], msecOffset);
                }
            }
        }
        throw new DisplayStateException("Found no DisplayState match for " + msecOffset);
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
                return findNearestIndexEvent(decNode, msecOffset);
            } else if (decNode.next[thisDigit].isTerminal) {
                // perfect match, send the event back
                return findNearestStorageEvent(((TerminalNode) decNode.next[thisDigit]), msecOffset);
            }

            node = decNode.next[thisDigit];
            offsetIndex %= divisors[divIndex];
        }
        //
        throw new DisplayStateException("Found no DisplayState match for " + msecOffset);
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
