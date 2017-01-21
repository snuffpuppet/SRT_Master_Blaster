package com.company;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by adam on 20/01/17.
 */
public class SubtitleSequencer {
    private Timer timer;
    private AvTracker tracker;
    private DisplayStateTable stateTable;

    public SubtitleSequencer(AvTracker tracker, DisplayStateTable stateTable) {
        timer = new Timer(true); // Create just one and make it a daemon thread.
        this.tracker = tracker;
        this.stateTable = stateTable;
    }

    public void display() {
        final Object o = new Object();
        TimerTask tt = new TimerTask() {
            public void run() {
                synchronized (o) {
                    o.notify();
                }
            }
        };
        DisplayEvent currentEvent = new DisplayEvent();

        // Every tenth of a second, wake up the main thread to check if we need to print a subtitle
        timer.scheduleAtFixedRate(tt, 0, 100);
        try {
            while (true) {
                synchronized (o) {
                    try {
                        o.wait();
                    } catch (InterruptedException ie) {
                    }
                }
                //System.out.print(">");
                DisplayEvent newEvent = stateTable.getDisplayState(tracker.getAvMilliseconds());

                if (newEvent.seq != currentEvent.seq) {
                    if (newEvent.isSilence) {
                        System.out.println("<                         >");
                    } else {
                        System.out.println(newEvent.text);
                        System.out.println("---------------------------");
                    }
                    currentEvent = newEvent;
                }
            }
        }

        catch (Exception e) {
            System.out.println("Crap! " + e.toString());
        }

        timer.cancel();
        timer.purge();
    }
}
