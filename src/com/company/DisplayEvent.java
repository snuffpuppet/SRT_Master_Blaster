package com.company;

/**
 * Created by adam on 20/01/17.
 */
public class DisplayEvent {
    int seq = -1;     // sequence number for events, easy checking to see if we are at the same one
    long msecOffset = -1; // offset in number of milliseconds
    boolean isSilence;  // is this a subtitle or a blank display event
    String text = "";      // subtitle text

    public DisplayEvent() {
    }

    public DisplayEvent(DisplayEvent e) {
        this.seq = e.seq;
        this.msecOffset = e.msecOffset;
        this.isSilence = e.isSilence;
        this.text = new String(e.text);
    }
}

