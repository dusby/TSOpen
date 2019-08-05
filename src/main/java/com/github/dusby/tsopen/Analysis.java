package com.github.dusby.tsopen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.profiler.Profiler;
import org.slf4j.profiler.StopWatch;

import com.github.dusby.tsopen.logicBombs.PotentialLogicBombsRecovery;
import com.github.dusby.tsopen.pathPredicateRecovery.PathPredicateRecovery;
import com.github.dusby.tsopen.pathPredicateRecovery.SimpleBlockPredicateExtraction;
import com.github.dusby.tsopen.symbolicExecution.SymbolicExecution;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValue;
import com.github.dusby.tsopen.utils.CommandLineOptions;
import com.github.dusby.tsopen.utils.Constants;
import com.github.dusby.tsopen.utils.TimeOut;
import com.github.dusby.tsopen.utils.Utils;
import com.google.common.collect.Lists;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.IfStmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.toolkits.callgraph.Edge;

public class Analysis {

	private String pkgName;
	private CommandLineOptions options;
	private String fileName;
	private PotentialLogicBombsRecovery plbr;
	private InfoflowCFG icfg;
	private int dexSize;
	private int nbClasses;
	private String fileSha256;
	private SimpleBlockPredicateExtraction sbpe;
	private PathPredicateRecovery ppr;

	private Logger logger = LoggerFactory.getLogger(Main.class);
	private Profiler mainProfiler = new Profiler(Main.class.getName());

	public Analysis(String[] args) {
		this.options = new CommandLineOptions(args);
		this.fileName = this.options.getFile();
		this.pkgName = this.getPackageName(this.fileName);
		this.fileSha256  = this.getFileSha256(this.fileName);
		this.sbpe = null;
		this.plbr = null;
		this.icfg = null;
		this.ppr = null;
	}

	public void run() {
		try {
			this.launchAnalysis();
		} catch(Exception e) {
			this.logger.error("Something went wrong : {}", e.getMessage());
			this.logger.error("Ending program...");
			this.printResultsInFile(true);
			System.exit(0);
		}
	}

	private void launchAnalysis() {
		System.out.println(String.format("TSOpen v0.1 started on %s\n", new Date()));
		StopWatch stopWatchCG = new StopWatch("cg"),
				stopWatchSBPE = new StopWatch("sbpe"),
				stopWatchPPR = new StopWatch("ppr"),
				stopWatchSE = new StopWatch("se"),
				stopWatchPLBR = new StopWatch("plbr");
		this.mainProfiler.start("overall");
		InfoflowAndroidConfiguration ifac = new InfoflowAndroidConfiguration();
		ifac.setIgnoreFlowsInSystemPackages(false);
		SetupApplication sa = null;
		SootMethod dummyMainMethod = null;
		SymbolicExecution se = null;
		Thread sbpeThread = null,
				pprThread = null,
				seThread = null,
				plbrThread = null;
		TimeOut timeOut = new TimeOut(this.options.getTimeout(), this);
		int timeout = timeOut.getTimeout();
		timeOut.trigger();

		if(!this.options.hasQuiet()) {
			this.logger.info(String.format("%-35s : %s", "Package", this.getPackageName(this.fileName)));
			this.logger.info(String.format("%-35s : %3s %s", "Timeout", timeout, timeout > 1 ? "mins" : "min"));
		}

		stopWatchCG.start("CallGraph");
		ifac.getAnalysisFileConfig().setAndroidPlatformDir(this.options.getPlatforms());
		ifac.getAnalysisFileConfig().setTargetAPKFile(this.fileName);

		sa = new SetupApplication(ifac);
		sa.constructCallgraph();
		this.icfg = new InfoflowCFG();
		stopWatchCG.stop();

		this.nbClasses = Scene.v().getApplicationClasses().size();

		if(!this.options.hasQuiet()) {
			this.logger.info(String.format("%-35s : %s", "CallGraph construction", Utils.getFormattedTime(stopWatchCG.elapsedTime())));
		}

		dummyMainMethod = sa.getDummyMainMethod();
		this.sbpe = new SimpleBlockPredicateExtraction(this.icfg, dummyMainMethod);
		this.ppr = new PathPredicateRecovery(this.icfg, this.sbpe, dummyMainMethod, this.options.hasExceptions());
		se = new SymbolicExecution(this.icfg, dummyMainMethod);
		this.plbr = new PotentialLogicBombsRecovery(this.sbpe, se, this.ppr, this.icfg);

		sbpeThread = new Thread(this.sbpe, "sbpe");
		pprThread = new Thread(this.ppr, "pprr");
		seThread = new Thread(se, "syex");
		plbrThread = new Thread(this.plbr, "plbr");

		stopWatchSBPE.start("sbpe");
		sbpeThread.start();
		stopWatchSE.start("se");
		seThread.start();

		try {
			sbpeThread.join();
			stopWatchSBPE.stop();
			if(!this.options.hasQuiet()) {
				this.logger.info(String.format("%-35s : %s", "Simple Block Predicate Extraction", Utils.getFormattedTime(stopWatchSBPE.elapsedTime())));
			}
		} catch (InterruptedException e) {
			this.logger.error(e.getMessage());
		}

		stopWatchPPR.start("ppr");
		pprThread.start();

		try {
			pprThread.join();
			stopWatchPPR.stop();
			if(!this.options.hasQuiet()) {
				this.logger.info(String.format("%-35s : %s", "Path Predicate Recovery", Utils.getFormattedTime(stopWatchPPR.elapsedTime())));
			}
			seThread.join();
			stopWatchSE.stop();
			if(!this.options.hasQuiet()) {
				this.logger.info(String.format("%-35s : %s", "Symbolic Execution", Utils.getFormattedTime(stopWatchSE.elapsedTime())));
			}
		} catch (InterruptedException e) {
			this.logger.error(e.getMessage());
		}

		stopWatchPLBR.start("plbr");
		plbrThread.start();

		try {
			plbrThread.join();
			stopWatchPLBR.stop();
			if(!this.options.hasQuiet()) {
				this.logger.info(String.format("%-35s : %s", "Potential Logic Bombs Recovery", Utils.getFormattedTime(stopWatchPLBR.elapsedTime())));
			}
		} catch (InterruptedException e) {
			this.logger.error(e.getMessage());
		}

		this.mainProfiler.stop();

		if(!this.options.hasQuiet()) {
			this.logger.info(String.format("%-35s : %s", "Application Execution Time", Utils.getFormattedTime(this.mainProfiler.elapsedTime())));
		}


		if(this.options.hasOutput()) {
			this.printResultsInFile(false);
		}
		if (!this.options.hasQuiet()){
			this.printResults(this.plbr, this.icfg);
		}

		timeOut.cancel();
	}

	private void printResults(PotentialLogicBombsRecovery plbr, InfoflowCFG icfg) {
		SootMethod ifMethod = null;
		SootClass ifClass = null;
		String ifComponent = null;
		IfStmt ifStmt = null;
		if(plbr.hasPotentialLogicBombs()) {
			System.out.println("\nPotential Logic Bombs found : ");
			System.out.println("----------------------------------------------------------------");
			for(Entry<IfStmt, Pair<List<SymbolicValue>, SootMethod>> e : plbr.getPotentialLogicBombs().entrySet()) {
				ifStmt = e.getKey();
				ifMethod = icfg.getMethodOf(ifStmt);
				ifClass = ifMethod.getDeclaringClass();
				ifComponent = Utils.getComponentType(ifClass);
				System.out.println(String.format("- %-20s : if %s", "Statement", ifStmt.getCondition()));
				System.out.println(String.format("- %-20s : %s", "Class", ifClass));
				System.out.println(String.format("- %-20s : %s", "Method", ifMethod.getName()));
				System.out.println(String.format("- %-20s : %s", "Starting Component", this.getStartingComponent(ifMethod)));
				System.out.println(String.format("- %-20s : %s", "Ending Component", ifComponent));
				System.out.println(String.format("- %-20s : %s", "Size of formula", this.ppr.getSizeOfFullPath(ifStmt)));
				System.out.println(String.format("- %-20s : %s", "Sensitive method", e.getValue().getValue1().getSignature()));
				System.out.println(String.format("- %-20s : %s", "Reachable", Utils.isInCallGraph(ifMethod) ? "Yes" : "No"));
				for(SymbolicValue sv : e.getValue().getValue0()) {
					System.out.println(String.format("- %-20s : %s (%s)", "Predicate", sv.getValue(), sv));
				}
				System.out.println("----------------------------------------------------------------\n");
			}
		}else {
			System.out.println("\nNo Logic Bomb found\n");
		}
	}

	private String getStartingComponent(SootMethod method) {
		return Utils.getComponentType(Scene.v().getSootClass(Lists.reverse(this.getLogicBombCallStack(method)).get(1).getReturnType().toString()));
	}

	private List<SootMethod> getLogicBombCallStack(SootMethod m){
		Iterator<Edge> it = Scene.v().getCallGraph().edgesInto(m);
		Edge next = null;
		List<SootMethod> methods = new LinkedList<SootMethod>();
		methods.add(m);

		while(it.hasNext()) {
			next = it.next();
			methods.addAll(this.getLogicBombCallStack(next.src()));
			return methods;
		}
		return methods;
	}

	/**
	 * Print analysis results in the given file
	 * Format :
	 * [sha256], [pkg_name], [count_of_triggers], [elapsed_time], [hasSuspiciousTrigger],
	 * [hasSuspiciousTriggerAfterControlDependency], [hasSuspiciousTriggerAfterPostFilters],
	 * [dex_size], [count_of_classes], [count_of_if], [if_depth], [count_of_objects]
	 * @param plbr
	 * @param icfg
	 * @param outputFile
	 */
	private void printResultsInFile(boolean timeoutReached) {
		PrintWriter writer = null;
		SootMethod ifMethod = null;
		SootClass ifClass = null;
		String ifStmtStr = null,
				ifComponent = null;
		List<SymbolicValue> values = null;
		IfStmt ifStmt = null;
		String result = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", this.fileSha256, this.pkgName,
				timeoutReached ? 0 : this.plbr.getPotentialLogicBombs().size(), timeoutReached ? -1 : TimeUnit.SECONDS.convert(this.mainProfiler.elapsedTime(), TimeUnit.NANOSECONDS),
						this.plbr == null ? 0 : this.plbr.ContainsSuspiciousCheck() ? 1 : 0, this.plbr == null ? 0 : this.plbr.ContainsSuspiciousCheckAfterControlDependency() ? 1 : 0,
								this.plbr == null ? 0 : this.plbr.ContainsSuspiciousCheckAfterPostFilterStep() ? 1 : 0, this.dexSize, this.nbClasses,
										this.sbpe == null ? 0 : this.sbpe.getIfCount(), this.sbpe == null ? 0 : this.sbpe.getIfDepthInMethods(), this.sbpe == null ? 0 : this.sbpe.getCountOfObject());
		String symbolicValues = null;
		try {
			writer = new PrintWriter(new FileOutputStream(new File(this.options.getOutput()), true));
			writer.append(result);
			for(Entry<IfStmt, Pair<List<SymbolicValue>, SootMethod>> e : this.plbr.getPotentialLogicBombs().entrySet()) {
				ifStmt = e.getKey();
				symbolicValues = "";
				ifMethod = this.icfg.getMethodOf(ifStmt);
				ifClass = ifMethod.getDeclaringClass();
				ifStmtStr = String.format("if %s", ifStmt.getCondition());
				ifComponent = Utils.getComponentType(ifMethod.getDeclaringClass());
				symbolicValues += String.format("%s%s;%s;%s;%s;%s;%s;%s;%s", Constants.FILE_LOGIC_BOMBS_DELIMITER,
						ifStmtStr, ifClass, ifMethod.getName(), e.getValue().getValue1().getSignature(), ifComponent,
						this.ppr.getSizeOfFullPath(ifStmt), Utils.isInCallGraph(ifMethod) ? 1 : 0, this.getStartingComponent(ifMethod));
				values = e.getValue().getValue0();
				for(SymbolicValue sv : values) {
					symbolicValues += String.format("%s (%s)", sv.getValue(), sv);
					if(sv != values.get(values.size() - 1)) {
						symbolicValues += ":";
					}else {
						symbolicValues += "\n";
					}
				}
				writer.append(symbolicValues);
			}
			writer.close();
		} catch (Exception e) {
			this.logger.error(e.getMessage());
		}
	}

	public void timeoutReachedPrintResults() {
		this.printResultsInFile(true);
	}

	private String getPackageName(String fileName) {
		String pkgName = null;
		ProcessManifest pm = null;
		try {
			pm = new ProcessManifest(fileName);
			pkgName = pm.getPackageName();
			this.dexSize = pm.getApk().getInputStream(Constants.CLASSES_DEX).available();
			pm.close();
		} catch (Exception e) {
			this.logger.error(e.getMessage());
		}
		return pkgName;
	}

	private String getFileSha256(String file) {
		MessageDigest sha256 = null;
		FileInputStream fis = null;
		StringBuffer sb = null;
		byte[] data = null;
		int read = 0;
		byte[] hashBytes = null;
		try {

			sha256 = MessageDigest.getInstance("SHA-256");
			fis = new FileInputStream(file);

			data = new byte[1024];
			read = 0;
			while ((read = fis.read(data)) != -1) {
				sha256.update(data, 0, read);
			};
			hashBytes = sha256.digest();

			sb = new StringBuffer();
			for (int i = 0; i < hashBytes.length; i++) {
				sb.append(Integer.toString((hashBytes[i] & 0xff) + 0x100, 16).substring(1));
			}
			fis.close();
		} catch (Exception e) {
			this.logger.error(e.getMessage());
		}
		return sb.toString();
	}
}
