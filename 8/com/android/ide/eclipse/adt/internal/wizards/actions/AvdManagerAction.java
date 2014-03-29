/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.wizards.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtPlugin.CheckSdkErrorHandler;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdkuilib.repository.UpdaterWindow;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

/**
 * Delegate for the toolbar/menu action "Android AVD Manager".
 * It displays the Android AVD Manager.
 */
public class AvdManagerAction implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {

    private boolean mFinished;
	private Shell mShell;

	public void dispose() {
        // nothing to dispose.
    }

    public void init(IWorkbenchWindow window) {
        // no init
    }

    public void run(IAction action) {
        Sdk sdk = getSdk();
        if (sdk != null) {

            // Runs the updater window, directing all logs to the ADT console.
            UpdaterWindow window = new UpdaterWindow(
                    getShell(),
                    new AdtConsoleSdkLog(),
                    sdk.getSdkLocation(),
                    false /*userCanChangeSdkRoot*/);
            window.addListeners(new UpdaterWindow.ISdkListener() {
                public void onSdkChange(boolean init) {
                    if (init == false) { // ignore initial load of the SDK.
                        AdtPlugin.getDefault().reparseSdk();
                    }
                }
            });
            window.open();
        } else {
            AdtPlugin.displayError("Android SDK",
                    "Location of the Android SDK has not been setup in the preferences.");
        }
    }

	private Shell getShell() {

		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				mShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
						.getShell();
			}
		});
		return mShell;
	}

	private Sdk getSdk() {
		String sdkLocation = AdtPrefs.getPrefs().getOsSdkFolder();
		if (sdkLocation == null || sdkLocation.length() == 0) {
			return null;
		}
		boolean isValid = AdtPlugin.getDefault().checkSdkLocationAndId(
				sdkLocation, new CheckSdkErrorHandler() {

					@Override
					public boolean handleWarning(String message) {
						return true;
					}

					@Override
					public boolean handleError(String message) {
						return false;
					}
				});
		if (isValid) {
			return waitForSDK();
		}
		return null;
	}

	private Sdk waitForSDK() {
		final Job waitJob = new Job("Android SDK") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Please wait until SDK is loading ...", 10);

				while (Sdk.getCurrent() == null) {
					if (monitor.isCanceled()) {
						monitor.done();
						return Status.CANCEL_STATUS;
					}
					try {
						Thread.sleep(250);
						monitor.worked(1);
					} catch (InterruptedException e) {
						monitor.done();
						return Status.CANCEL_STATUS;
					}
				}
				monitor.done();
				return Status.OK_STATUS;
			}

		};
		waitJob.setUser(true);
		waitJob.setPriority(Job.SHORT);
		mFinished = false;
		waitJob.addJobChangeListener(new JobChangeAdapter() {

			public void done(IJobChangeEvent event) {
				mFinished = true;
			}
		});
		waitJob.schedule();
		while (!mFinished) {
			Display display = Display.getCurrent();
			if (!display.readAndDispatch()) {
				display.sleep();
			}
			// don't join a waiting or sleeping job when suspended (deadlock risk)
			if (Job.getJobManager().isSuspended()
					&& waitJob.getState() != Job.RUNNING) {
				break;
			}
		}
		return Sdk.getCurrent();
	}

    public void selectionChanged(IAction action, ISelection selection) {
        // nothing related to the current selection.
    }

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // nothing to do.
    }
}
