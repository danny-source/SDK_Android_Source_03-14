/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import com.android.internal.os.BatteryStatsImpl;
import com.android.server.Watchdog;

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.IApplicationThread;
import android.app.IInstrumentationWatcher;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.PrintWriterPrinter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Full information about a particular process that
 * is currently running.
 */
class ProcessRecord implements Watchdog.PssRequestor {
    final BatteryStatsImpl.Uid.Proc batteryStats; // where to collect runtime statistics
    final ApplicationInfo info; // all about the first app in the process
    final String processName;   // name of the process
    // List of packages running in the process
    final HashSet<String> pkgList = new HashSet();
    IApplicationThread thread;  // the actual proc...  may be null only if
                                // 'persistent' is true (in which case we
                                // are in the process of launching the app)
    int pid;                    // The process of this application; 0 if none
    boolean starting;           // True if the process is being started
    int maxAdj;                 // Maximum OOM adjustment for this process
    int hiddenAdj;              // If hidden, this is the adjustment to use
    int curRawAdj;              // Current OOM unlimited adjustment for this process
    int setRawAdj;              // Last set OOM unlimited adjustment for this process
    int curAdj;                 // Current OOM adjustment for this process
    int setAdj;                 // Last set OOM adjustment for this process
    int curSchedGroup;          // Currently desired scheduling class
    int setSchedGroup;          // Last set to background scheduling class
    boolean setIsForeground;    // Running foreground UI when last set?
    boolean foregroundServices; // Running any services that are foreground?
    boolean bad;                // True if disabled in the bad process list
    IBinder forcingToForeground;// Token that is forcing this process to be foreground
    int adjSeq;                 // Sequence id for identifying repeated trav
    ComponentName instrumentationClass;// class installed to instrument app
    ApplicationInfo instrumentationInfo; // the application being instrumented
    String instrumentationProfileFile; // where to save profiling
    IInstrumentationWatcher instrumentationWatcher; // who is waiting
    Bundle instrumentationArguments;// as given to us
    ComponentName instrumentationResultClass;// copy of instrumentationClass
    BroadcastRecord curReceiver;// receiver currently running in the app
    long lastRequestedGc;       // When we last asked the app to do a gc
    long lastLowMemory;         // When we last told the app that memory is low
    boolean reportLowMemory;    // Set to true when waiting to report low mem
    int lastPss;                // Last pss size reported by app.
    String adjType;             // Debugging: primary thing impacting oom_adj.
    Object adjSource;           // Debugging: option dependent object.
    Object adjTarget;           // Debugging: target component impacting oom_adj.
    
    // contains HistoryRecord objects
    final ArrayList activities = new ArrayList();
    // all ServiceRecord running in this process
    final HashSet services = new HashSet();
    // services that are currently executing code (need to remain foreground).
    final HashSet<ServiceRecord> executingServices
             = new HashSet<ServiceRecord>();
    // All ConnectionRecord this process holds
    final HashSet<ConnectionRecord> connections
            = new HashSet<ConnectionRecord>();  
    // all IIntentReceivers that are registered from this process.
    final HashSet<ReceiverList> receivers = new HashSet<ReceiverList>();
    // class (String) -> ContentProviderRecord
    final HashMap pubProviders = new HashMap(); 
    // All ContentProviderRecord process is using
    final HashSet conProviders = new HashSet(); 
    
    boolean persistent;         // always keep this application running?
    boolean crashing;           // are we in the process of crashing?
    Dialog crashDialog;         // dialog being displayed due to crash.
    boolean notResponding;      // does the app have a not responding dialog?
    Dialog anrDialog;           // dialog being displayed due to app not resp.
    boolean removed;            // has app package been removed from device?
    boolean debugging;          // was app launched for debugging?
    int persistentActivities;   // number of activities that are persistent
    boolean waitedForDebugger;  // has process show wait for debugger dialog?
    Dialog waitDialog;          // current wait for debugger dialog
    
    String stringName;          // caching of toString() result.
    
    // These reports are generated & stored when an app gets into an error condition.
    // They will be "null" when all is OK.
    ActivityManager.ProcessErrorStateInfo crashingReport;
    ActivityManager.ProcessErrorStateInfo notRespondingReport;

    // Who will be notified of the error. This is usually an activity in the
    // app that installed the package.
    ComponentName errorReportReceiver;

    void dump(PrintWriter pw, String prefix) {
        if (info.className != null) {
            pw.print(prefix); pw.print("class="); pw.println(info.className);
        }
        if (info.manageSpaceActivityName != null) {
            pw.print(prefix); pw.print("manageSpaceActivityName=");
            pw.println(info.manageSpaceActivityName);
        }
        pw.print(prefix); pw.print("dir="); pw.print(info.sourceDir);
                pw.print(" publicDir="); pw.print(info.publicSourceDir);
                pw.print(" data="); pw.println(info.dataDir);
        pw.print(prefix); pw.print("packageList="); pw.println(pkgList);
        if (instrumentationClass != null || instrumentationProfileFile != null
                || instrumentationArguments != null) {
            pw.print(prefix); pw.print("instrumentationClass=");
                    pw.print(instrumentationClass);
                    pw.print(" instrumentationProfileFile=");
                    pw.println(instrumentationProfileFile);
            pw.print(prefix); pw.print("instrumentationArguments=");
                    pw.println(instrumentationArguments);
            pw.print(prefix); pw.print("instrumentationInfo=");
                    pw.println(instrumentationInfo);
            if (instrumentationInfo != null) {
                instrumentationInfo.dump(new PrintWriterPrinter(pw), prefix + "  ");
            }
        }
        pw.print(prefix); pw.print("thread="); pw.print(thread);
                pw.print(" curReceiver="); pw.println(curReceiver);
        pw.print(prefix); pw.print("pid="); pw.print(pid); pw.print(" starting=");
                pw.print(starting); pw.print(" lastPss="); pw.println(lastPss);
        pw.print(prefix); pw.print("oom: max="); pw.print(maxAdj);
                pw.print(" hidden="); pw.print(hiddenAdj);
                pw.print(" curRaw="); pw.print(curRawAdj);
                pw.print(" setRaw="); pw.print(setRawAdj);
                pw.print(" cur="); pw.print(curAdj);
                pw.print(" set="); pw.println(setAdj);
        pw.print(prefix); pw.print("curSchedGroup="); pw.print(curSchedGroup);
                pw.print(" setSchedGroup="); pw.println(setSchedGroup);
        pw.print(prefix); pw.print("setIsForeground="); pw.print(setIsForeground);
                pw.print(" foregroundServices="); pw.print(foregroundServices);
                pw.print(" forcingToForeground="); pw.println(forcingToForeground);
        pw.print(prefix); pw.print("persistent="); pw.print(persistent);
                pw.print(" removed="); pw.print(removed);
                pw.print(" persistentActivities="); pw.println(persistentActivities);
        if (debugging || crashing || crashDialog != null || notResponding
                || anrDialog != null || bad) {
            pw.print(prefix); pw.print("debugging="); pw.print(debugging);
                    pw.print(" crashing="); pw.print(crashing);
                    pw.print(" "); pw.print(crashDialog);
                    pw.print(" notResponding="); pw.print(notResponding);
                    pw.print(" " ); pw.print(anrDialog);
                    pw.print(" bad="); pw.print(bad);

                    // crashing or notResponding is always set before errorReportReceiver
                    if (errorReportReceiver != null) {
                        pw.print(" errorReportReceiver=");
                        pw.print(errorReportReceiver.flattenToShortString());
                    }
                    pw.println();
        }
        if (activities.size() > 0) {
            pw.print(prefix); pw.print("activities="); pw.println(activities);
        }
        if (services.size() > 0) {
            pw.print(prefix); pw.print("services="); pw.println(services);
        }
        if (executingServices.size() > 0) {
            pw.print(prefix); pw.print("executingServices="); pw.println(executingServices);
        }
        if (connections.size() > 0) {
            pw.print(prefix); pw.print("connections="); pw.println(connections);
        }
        if (pubProviders.size() > 0) {
            pw.print(prefix); pw.print("pubProviders="); pw.println(pubProviders);
        }
        if (conProviders.size() > 0) {
            pw.print(prefix); pw.print("conProviders="); pw.println(conProviders);
        }
        if (receivers.size() > 0) {
            pw.print(prefix); pw.print("receivers="); pw.println(receivers);
        }
    }
    
    ProcessRecord(BatteryStatsImpl.Uid.Proc _batteryStats, IApplicationThread _thread,
            ApplicationInfo _info, String _processName) {
        batteryStats = _batteryStats;
        info = _info;
        processName = _processName;
        pkgList.add(_info.packageName);
        thread = _thread;
        maxAdj = ActivityManagerService.EMPTY_APP_ADJ;
        hiddenAdj = ActivityManagerService.HIDDEN_APP_MIN_ADJ;
        curRawAdj = setRawAdj = -100;
        curAdj = setAdj = -100;
        persistent = false;
        removed = false;
        persistentActivities = 0;
    }

    public void setPid(int _pid) {
        pid = _pid;
        stringName = null;
    }
    
    /**
     * This method returns true if any of the activities within the process record are interesting
     * to the user. See HistoryRecord.isInterestingToUserLocked()
     */
    public boolean isInterestingToUserLocked() {
        final int size = activities.size();
        for (int i = 0 ; i < size ; i++) {
            HistoryRecord r = (HistoryRecord) activities.get(i);
            if (r.isInterestingToUserLocked()) {
                return true;
            }
        }
        return false;
    }
    
    public void stopFreezingAllLocked() {
        int i = activities.size();
        while (i > 0) {
            i--;
            ((HistoryRecord)activities.get(i)).stopFreezingScreenLocked(true);
        }
    }
    
    public void requestPss() {
        IApplicationThread localThread = thread;
        if (localThread != null) {
            try {
                localThread.requestPss();
            } catch (RemoteException e) {
            }
        }
    }
    
    public String toString() {
        if (stringName != null) {
            return stringName;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("ProcessRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(' ');
        sb.append(pid);
        sb.append(':');
        sb.append(processName);
        sb.append('/');
        sb.append(info.uid);
        sb.append('}');
        return stringName = sb.toString();
    }
    
    /*
     *  Return true if package has been added false if not
     */
    public boolean addPackage(String pkg) {
        if (!pkgList.contains(pkg)) {
            pkgList.add(pkg);
            return true;
        }
        return false;
    }
    
    /*
     *  Delete all packages from list except the package indicated in info
     */
    public void resetPackageList() {
        pkgList.clear();
        pkgList.add(info.packageName);
    }
    
    public String[] getPackageList() {
        int size = pkgList.size();
        if (size == 0) {
            return null;
        }
        String list[] = new String[size];
        pkgList.toArray(list);
        return list;
    }
}
