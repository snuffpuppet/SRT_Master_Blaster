package com.company;

import java.util.Date;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by adam on 15/01/17.
 */
public class SrtScheduler {

    private SubtitleSequence srtSequence;
    private Timer timer = new Timer(true); // Create just one and make it a daemon thread.

    public SrtScheduler(SubtitleSequence srtSequence) {
        this.srtSequence = srtSequence;
    }

    class Notifier extends TimerTask {
        public void run() {
            notifyAll();
        }
    }

    public void waitUntil(Date date) {
        final Object o = new Object();
        TimerTask tt = new TimerTask() {
        public void run() {
            synchronized (o) {
                o.notify();
                }
            }
        };
        timer.schedule(tt, date);
        synchronized(o) {
            try {
                o.wait();
            } catch (InterruptedException ie) {}
        }
        //timer.cancel();
        timer.purge();
    }

    public synchronized boolean syncTimer(Date syncTime) {
        Timer srtTimer = new Timer();
        Notifier notifier = new Notifier();
        srtTimer.schedule(notifier, syncTime);
        long timediff;
        do {
            try {
                wait();
            } catch (InterruptedException e) {}

            Date now = new Date();
            timediff = now.getTime() - syncTime.getTime();
        } while (timediff > 1000);

        if (timediff > -1000 && timediff < 1000)
            return true;
        else
            return false;
    }

    public void schedule() {
        Date startSequence = new Date();
        LinkedList<SubtitleSequence.Subtitle> subtitles = this.srtSequence.getSubtitles();

        for (SubtitleSequence.Subtitle sub : subtitles) {
            Date displayTime = new Date(startSequence.getTime() + sub.startTime);
            Date endTime = new Date(startSequence.getTime() + sub.endTime);

            waitUntil(displayTime);
            System.out.println(sub.text);

            waitUntil(endTime);
            System.out.println("------------");

            /*
            if (syncTimer(displayTime)) {
                System.out.println(sub.text);
            }

            Date endTime = new Date(startSequence.getTime() + sub.endTime);
            if (syncTimer(endTime)) {
                System.out.println("----------------");
            }
            */
        }
    }
}
