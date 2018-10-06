/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.tdb2.loader.base;

import static org.apache.jena.atlas.lib.DateTimeUtils.nowAsString ;

import java.util.Objects;

import org.apache.jena.atlas.lib.Timer;
import org.slf4j.Logger ;

/** Progress monitor - output lines to show the progress of some long running operation.
 * This is based on "ticks", not time.
 * Once per item processed, call the {@link #tick()} operation.  
 */
public class ProgressMonitorOutput implements ProgressMonitor {
    private final MonitorOutput output;
    private final long   tickPoint;
    private final int    superTick;
    private final Timer  timer;
    private Timer getTimer() { return timer; } 

    private final String label;

    // Counters - this monitor.
    private long  counterBatch = 0;
    private long  counterTotal = 0;
    //private final ProgressMonitorContext context;

    private long  lastTime     = -1;
    private long  timeTotalMillis = -1;

    /** ProgressMonitor that outputs to a {@link Logger} */ 
    public static ProgressMonitorOutput create(Logger log, String label, long tickPoint, int superTick) {
        Objects.requireNonNull(log);
        return create(LoaderOps.outputToLog(log), label, tickPoint, superTick) ;
    }
    
    /** ProgressMonitor that outputs to on a {@link MonitorOutput} */ 
    public static ProgressMonitorOutput create(MonitorOutput output, String label, long tickPoint, int superTick) {
        Objects.requireNonNull(output);
        return new ProgressMonitorOutput(label, tickPoint, superTick, output) ;
    }

    /**
     * @param label      
     *      Label added to output strings. 
     *      Usually related to the kind of things being monitored.
     *      e.g "tuples 
     * @param tickPoint
     *      Frequent of output messages 
     * @param superTick
     *      Frequent of "Elapsed" additional message
     * @param output
     *      Function called to deal with progress messages.
     */
    public ProgressMonitorOutput(String label, long tickPoint, int superTick, MonitorOutput output) {
        this.output = output;
        this.label = label;
        this.tickPoint = tickPoint;
        this.superTick = superTick;
        this.timer = new Timer();
    }

    /** Print a start message using the label */
    @Override
    public void startMessage() {
        startMessage(null) ;
    }
    
    /** Print a start message using a different string. */
    @Override
    public void startMessage(String msg) {
        if ( msg != null )
            output.print(msg) ;
        else
            output.print("Start: "+label) ;
    }

    @Override
    public void finishMessage(String msg) {
        // Elapsed.
        long timePoint = getTimer().getTimeInterval();
        long counterTotalMsg = getRunningTotal();

        // /1000L is milli to second conversion
        if ( timePoint != 0 ) {
            double time = timePoint / 1000.0;
            long runAvgRate = (counterTotalMsg * 1000L) / timePoint;

            print("%s: %,d %s %.2fs (Avg: %,d)", msg, counterTotalMsg, label, time, runAvgRate);
        } else
            print("%s: %,d %s (Avg: ----)", msg, counterTotalMsg, label);
    }

    @Override
    public void start() {
        // XXX
        getTimer().startTimer();
        lastTime = 0;
    }

    @Override
    public void finish() {
        timeTotalMillis = getTimer().endTimer();
    }

    @Override
    public void tick() {
        counterBatch++;
        counterTotal++;
    
        if ( tickPoint(getRunningTotal(), tickPoint) ) {
            long timePoint = getTimer().readTimer();
            long thisTime = timePoint - lastTime;
    
            // *1000L is milli to second conversion
            if ( thisTime != 0 && timePoint != 0 ) {
                long batchAvgRate = (counterBatch * 1000L) / thisTime;
                long runAvgRate = (getRunningTotal() * 1000L) / timePoint;
                print("Add: %,d %s (Batch: %,d / Avg: %,d)", getRunningTotal(), label, batchAvgRate, runAvgRate);
            } else {
                print("Add: %,d %s (Batch: ---- / Avg: ----)", getRunningTotal(), label);
            }
    
            lastTime = timePoint;
    
            if ( tickPoint(getRunningTotal(), superTick * tickPoint) )
                elapsed(timePoint);
            counterBatch = 0;
            lastTime = timePoint;
        }
    }

    @Override
    public long getTicks() {
        return counterTotal;
    }

    private long getRunningTotal() {
        return counterTotal;
    }

    @Override
    public long getTime() {
        return timeTotalMillis;
    }

    protected void elapsed(long timerReading) {
        float elapsedSecs = timerReading / 1000F;
        print("  Elapsed: %,.2f seconds [%s]", elapsedSecs, nowAsString());
    }

    /** Print a message */
    private void print(String fmt, Object... args) {
        if ( output != null )
            output.print(fmt, args);
    }

    static boolean tickPoint(long counter, long quantum) {
        return counter % quantum == 0;
    }

}
