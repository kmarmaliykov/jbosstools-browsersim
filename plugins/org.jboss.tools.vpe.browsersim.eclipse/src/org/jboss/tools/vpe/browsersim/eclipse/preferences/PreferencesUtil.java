/*******************************************************************************
 * Copyright (c) 2007-2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.vpe.browsersim.eclipse.preferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.jdt.internal.launching.LaunchingPlugin;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.osgi.util.NLS;
import org.jboss.tools.vpe.browsersim.browser.PlatformUtil;
import org.jboss.tools.vpe.browsersim.eclipse.Activator;
import org.jboss.tools.vpe.browsersim.eclipse.Messages;
import org.jboss.tools.vpe.browsersim.eclipse.launcher.ExternalProcessLauncher;

/**
 * @author Konstantin Marmalyukov (kmarmaliykov)
 * @author Ilya Buziuk (ibuziuk)
 */
@SuppressWarnings("restriction")
public class PreferencesUtil {
	/**
	 * @since 3.3 OSX environment variable specifying JRE to use
	 */
	protected static final String JAVA_JVM_VERSION = "JAVA_JVM_VERSION"; //$NON-NLS-1$
	
	/**
	 * @return JVMs which can run BrowserSim, but no more than {@code limit}.
	 * If {@code limit <= 0} returns all suitable JVMs not limiting their number.
	 * The less {@code limit}, the faster the method performs. 
	 */
	public static List<IVMInstall> getSuitableJvms(int limit) {
		String eclipseOs = PlatformUtil.getOs();
		String eclipseArch = PlatformUtil.getArch();
		
		List<IVMInstall> vms = new ArrayList<IVMInstall>();
		for(IVMInstallType type : JavaRuntime.getVMInstallTypes()) {
			for(IVMInstall vm : type.getVMInstalls()) {
				if (vm instanceof AbstractVMInstall) {
					AbstractVMInstall abstractVm = (AbstractVMInstall) vm;
					String javaVersion = abstractVm.getJavaVersion();
					if (javaVersion != null && javaVersion.compareTo("1.6") >= 0) { //$NON-NLS-1$
						if (PlatformUtil.OS_WIN32.equals(eclipseOs)) {
							// can use only 32 bit jvm on windows
							if (isX86(abstractVm) && !conflictsWithWebKit(abstractVm)) {
								vms.add(abstractVm);
							}
						} else if (PlatformUtil.OS_MACOSX.equals(eclipseOs)) {
							if (PlatformUtil.ARCH_X86.equals(eclipseArch)) {
								// Only Java 6 supports 32-bit mode on Mac
								if (javaVersion.startsWith("1.6")) { //$NON-NLS-1$
									vms.add(abstractVm);
								}
							} else {
								vms.add(abstractVm);
							}
						} else if (PlatformUtil.OS_LINUX.equals(eclipseOs)) {
							if (PlatformUtil.ARCH_X86.equals(eclipseArch) == isX86(abstractVm)) {
								vms.add(abstractVm);
							}
						}
					}
				}
				
				if (limit > 0 && vms.size() >= limit) {// if limit <= 0, return all
					return vms;
				}
			}
		}
		
		return vms;
	}
	
	public static List<IVMInstall> getSuitableJvms() {
		return getSuitableJvms(0);
	}
	
	public static IVMInstall getJVM(String id) {
		for(IVMInstallType type : JavaRuntime.getVMInstallTypes()) {
			for(IVMInstall vm : type.getVMInstalls()) {
				if (id.equals(vm.getId())) {
					return vm;
				}
			}
		}
		return null;
	}
	
	public static String getArchitecture(IVMInstall defaultVMInstall) {
		String javaCommand = getJavaCommand(defaultVMInstall);
		if (javaCommand != null) {
			try {
				return generateLibraryInfo(defaultVMInstall.getInstallLocation(), javaCommand);
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}
		}
		return null;
	}
	
	private static boolean isX86(IVMInstall vm) {
		return PlatformUtil.ARCH_X86.equals(PreferencesUtil.getArchitecture(vm));
	}
	
	private static boolean conflictsWithWebKit(IVMInstall vm) {
		File libxml2 = new File(vm.getInstallLocation().getAbsolutePath() + "/bin/libxml2.dll"); //$NON-NLS-1$
		return libxml2.exists();
	}
	
	public static String getJavaCommand(IVMInstall jvm) {
		if (jvm != null) {
			File installLocation = jvm.getInstallLocation();
			if (installLocation != null) {
				File javaExecutable = StandardVMType.findJavaExecutable(installLocation);
				if (javaExecutable != null) {
					return javaExecutable.getAbsolutePath();
				}
			}
		}
		return null;
	}

	/**
	 * @see org.eclipse.jdt.internal.launching.StandardVMType#generateLibraryInfo
	 */
	@SuppressWarnings({ "unchecked" })
	public static String generateLibraryInfo(File javaHome, String javaCommand) throws IOException {
		String arch = null;
		String currentBundleLocation = ExternalProcessLauncher
				.getBundleLocation(Platform.getBundle(Activator.PLUGIN_ID));
		String[] cmdLine = new String[] {javaCommand, "-cp", currentBundleLocation, "org.jboss.tools.vpe.browsersim.eclipse.preferences.ArchitectureDetector" }; //$NON-NLS-1$ //$NON-NLS-2$
		Process p = null;
		try {
			String envp[] = null;
			if (Platform.OS_MACOSX.equals(Platform.getOS())) {
				Map<String, String> map = DebugPlugin.getDefault().getLaunchManager().getNativeEnvironmentCasePreserved();
				if (map.remove(JAVA_JVM_VERSION) != null) {
					envp = new String[map.size()];
					Iterator<Entry<String, String>> iterator = map.entrySet().iterator();
					int i = 0;
					while (iterator.hasNext()) {
						Entry<String, String> entry = iterator.next();
						envp[i] = entry.getKey() + "=" + entry.getValue(); //$NON-NLS-1$
						i++;
					}
				}
			}
			p = DebugPlugin.exec(cmdLine, null, envp);
			IProcess process = DebugPlugin.newProcess(new Launch(null, ILaunchManager.RUN_MODE, null), p, Messages.BrowserSim_LIBRARY_DETECTION);
			for (int i = 0; i < 600; i++) {
				// Wait no more than 30 seconds (600 * 50 milliseconds)
				if (process.isTerminated()) {
					break;
				}
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
			arch = getArchitecture(process);
		} catch (CoreException ioe) {
			LaunchingPlugin.log(ioe);
		} finally {
			if (p != null) {
				p.destroy();
			}
		}
		if (arch == null) {
		    // log error that we were unable to generate library information - see bug 70011
		    LaunchingPlugin.log(NLS.bind(Messages.BrowserSim_LIBRARY_DETECTION_FAILURE, new String[]{javaHome.getAbsolutePath()}));
		}
		return arch;
	}

	private static String getArchitecture(IProcess process) {
		IStreamsProxy streamsProxy = process.getStreamsProxy();
		String text = null;
		if (streamsProxy != null) {
			text = streamsProxy.getOutputStreamMonitor().getContents();
		}
		if (text != null && text.length() > 0) {
			return PlatformUtil.parseArch(text);
		} 
		return null;
	}
	
	public static String argumentsListToString(List<String> list) {
		String str = ""; //$NON-NLS-1$
		for (String s : list) {
			s = surroundSpaces(s);
			str += s + " "; //$NON-NLS-1$
 		}
		return str;
	}
	
	private static String surroundSpaces(String str) {
		if (str.indexOf(' ') >= 0) {
			return '\"' + str + '\"';
		}
		return str;
	}
}