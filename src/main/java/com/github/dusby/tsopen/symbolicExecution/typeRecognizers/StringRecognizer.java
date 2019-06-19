package com.github.dusby.tsopen.symbolicExecution.typeRecognizers;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.javatuples.Pair;

import com.github.dusby.tsopen.symbolicExecution.ContextualValues;
import com.github.dusby.tsopen.symbolicExecution.SymbolicExecutioner;
import com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings.AppendRecognizer;
import com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings.StringMethodsRecognizerProcessor;
import com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings.SubStringRecognizer;
import com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings.ToStringRecognizer;
import com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings.ValueOfRecognizer;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.ConcreteValue;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.MethodRepresentationValue;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValueProvider;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ParameterRef;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;

public class StringRecognizer extends TypeRecognizerProcessor{

	private static final String UNKNOWN_STRING = "UNKNOWN_STRING";
	private static final String EMPTY_STRING = "";

	private StringMethodsRecognizerProcessor smrp;

	public StringRecognizer(TypeRecognizerProcessor next, SymbolicExecutioner se, InfoflowCFG icfg) {
		super(next, se, icfg);
		this.smrp = new AppendRecognizer(null, se);
		this.smrp = new ValueOfRecognizer(this.smrp, se);
		this.smrp = new ToStringRecognizer(this.smrp, se);
		this.smrp = new SubStringRecognizer(this.smrp, se);
		this.authorizedTypes.add("java.lang.String");
		this.authorizedTypes.add("java.lang.StringBuilder");
		this.authorizedTypes.add("java.lang.StringBuffer");
	}

	@Override
	public List<Pair<Value, SymbolicValueProvider>> processRecognitionOfDefStmt(DefinitionStmt defUnit) {
		Value leftOp = null,
				rightOp = null,
				callerRightOp = null,
				base = null;
		InvokeExpr rightOpInvokeExpr = null,
				invExprCaller = null;
		String leftOpType = null;
		SootMethod m = null;
		List<Value> args = null;
		List<Pair<Value, SymbolicValueProvider>> results = new LinkedList<Pair<Value,SymbolicValueProvider>>();
		CastExpr rightOpExpr = null;
		ContextualValues contextualValues = null;
		Collection<Unit> callers = null;
		InvokeStmt invStmtCaller = null;
		AssignStmt assignCaller = null;
		List<SymbolicValueProvider> recognizedValues = null;

		leftOp = defUnit.getLeftOp();
		rightOp = defUnit.getRightOp();
		leftOpType = leftOp.getType().toString();
		if(this.isAuthorizedType(leftOpType)) {
			if(rightOp instanceof StringConstant) {
				results.add(new Pair<Value, SymbolicValueProvider>(leftOp, new ConcreteValue((StringConstant)rightOp)));
			}else if(rightOp instanceof ParameterRef) {
				callers = this.icfg.getCallersOf(this.icfg.getMethodOf(defUnit));
				for(Unit caller : callers) {
					if(caller instanceof InvokeStmt) {
						invStmtCaller = (InvokeStmt) caller;
						invExprCaller = invStmtCaller.getInvokeExpr();
					}else if(caller instanceof AssignStmt) {
						assignCaller = (AssignStmt) caller;
						callerRightOp = assignCaller.getRightOp();
						if(callerRightOp instanceof InvokeExpr) {
							invExprCaller = (InvokeExpr)callerRightOp;
						}else if(callerRightOp instanceof InvokeStmt) {
							invExprCaller = ((InvokeStmt)callerRightOp).getInvokeExpr();
						}
					}
					contextualValues = this.se.getContext().get(invExprCaller.getArg(((ParameterRef) rightOp).getIndex()));
					this.checkAndProcessContextValues(contextualValues, results, leftOp);
				}
			}else if(rightOp instanceof Local) {
				contextualValues = this.se.getContext().get(rightOp);
				this.checkAndProcessContextValues(contextualValues, results, leftOp);
			}else if (rightOp instanceof CastExpr) {
				rightOpExpr = (CastExpr) rightOp;
				contextualValues = this.se.getContext().get(rightOpExpr.getOp());
				this.checkAndProcessContextValues(contextualValues, results, leftOp);
			}else if(rightOp instanceof InvokeExpr) {
				rightOpInvokeExpr = (InvokeExpr) rightOp;
				m = rightOpInvokeExpr.getMethod();
				args = rightOpInvokeExpr.getArgs();
				base = rightOpInvokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) rightOpInvokeExpr).getBase() : null;
				recognizedValues = this.smrp.recognize(m, base, args);
				if(recognizedValues != null) {
					for(SymbolicValueProvider s : recognizedValues) {
						results.add(new Pair<Value, SymbolicValueProvider>(leftOp, s));
					}
				}else {
					results.add(new Pair<Value, SymbolicValueProvider>(leftOp, new MethodRepresentationValue(base, args, m, this.se)));
				}
			}
		}
		return results;
	}

	@Override
	public List<Pair<Value, SymbolicValueProvider>> processRecognitionOfInvokeStmt(InvokeStmt invUnit) {
		Value base = null,
				arg = null;
		InvokeExpr invExprUnit = null;
		SootMethod m = null;
		List<Value> args = null;
		List<Pair<Value, SymbolicValueProvider>> results = new LinkedList<Pair<Value,SymbolicValueProvider>>();
		ContextualValues contextualValues = null;

		invExprUnit = invUnit.getInvokeExpr();
		if(invExprUnit instanceof SpecialInvokeExpr) {
			m = invExprUnit.getMethod();
			if(m.isConstructor()) {
				base = ((SpecialInvokeExpr) invExprUnit).getBase();
				if(this.isAuthorizedType(base.getType().toString())) {
					args = invExprUnit.getArgs();
					if(args.size() == 0) {
						results.add(new Pair<Value, SymbolicValueProvider>(base, new ConcreteValue(StringConstant.v(EMPTY_STRING))));
					}else {
						arg = args.get(0);
						if(arg instanceof Local) {
							contextualValues = this.se.getContext().get(arg);
							this.checkAndProcessContextValues(contextualValues, results, base);
						}else if(arg instanceof StringConstant) {
							results.add(new Pair<Value, SymbolicValueProvider>(base, new ConcreteValue((StringConstant)arg)));
						}else {
							results.add(new Pair<Value, SymbolicValueProvider>(base, new ConcreteValue(StringConstant.v(EMPTY_STRING))));
						}
					}
				}
			}
		}
		return results;
	}

	private void checkAndProcessContextValues(ContextualValues contextualValues, List<Pair<Value, SymbolicValueProvider>> results, Value leftOp) {
		List<SymbolicValueProvider> values = null;
		if(contextualValues == null) {
			results.add(new Pair<Value, SymbolicValueProvider>(leftOp, new ConcreteValue(StringConstant.v(UNKNOWN_STRING))));
		}else {
			values = contextualValues.getLastCoherentValues();
			for(SymbolicValueProvider svp : values) {
				results.add(new Pair<Value, SymbolicValueProvider>(leftOp, svp));
			}
		}
	}
}
