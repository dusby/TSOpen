package com.github.dusby.tsopen.symbolicExecution.typeRecognition;

import java.util.LinkedList;
import java.util.List;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dusby.tsopen.symbolicExecution.SymbolicExecution;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.MethodRepresentationValue;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.ObjectValue;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValue;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;

public abstract class TypeRecognitionHandler implements TypeRecognition {

	private TypeRecognitionHandler next;
	protected SymbolicExecution se;
	protected InfoflowCFG icfg;
	protected List<String> authorizedTypes;

	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	public TypeRecognitionHandler(TypeRecognitionHandler next, SymbolicExecution se, InfoflowCFG icfg) {
		this.next = next;
		this.se = se;
		this.icfg = icfg;
		this.authorizedTypes = new LinkedList<String>();
	}

	@Override
	public List<Pair<Value, SymbolicValue>> recognizeType(Unit node) {

		List<Pair<Value, SymbolicValue>> result = null;

		if(node instanceof DefinitionStmt) {
			result = this.processDefinitionStmt((DefinitionStmt) node);
		}else if (node instanceof InvokeStmt) {
			result = this.processInvokeStmt((InvokeStmt) node);
		}

		if(result != null && !result.isEmpty()) {
			return result;
		}
		if(this.next != null) {
			return this.next.recognizeType(node);
		}
		else {
			return null;
		}
	}

	@Override
	public List<Pair<Value, SymbolicValue>> processDefinitionStmt(DefinitionStmt defUnit) {
		if(this.isAuthorizedType(defUnit.getLeftOp().getType().toString())) {
			return this.handleDefinitionStmt(defUnit);
		}
		return null;
	}

	@Override
	public List<Pair<Value, SymbolicValue>> processInvokeStmt(InvokeStmt invUnit) {
		Value base = null;
		InvokeExpr invExprUnit = invUnit.getInvokeExpr();
		SootMethod m = invExprUnit.getMethod();
		List<Pair<Value, SymbolicValue>> results = new LinkedList<Pair<Value,SymbolicValue>>();

		if(invExprUnit instanceof InstanceInvokeExpr) {
			base = ((InstanceInvokeExpr) invExprUnit).getBase();
			if(base != null) {
				if(this.isAuthorizedType(base.getType().toString())) {
					if(m.isConstructor()) {
						this.handleConstructor(invExprUnit, base, results);
					}else if(invExprUnit instanceof InstanceInvokeExpr){
						this.handleInvokeStmt(invExprUnit, base, results);
					}
				}
			}
		}
		return results;
	}

	protected void handleInvokeStmt(InvokeExpr invExprUnit, Value base, List<Pair<Value, SymbolicValue>> results) {
		SootMethod method = invExprUnit.getMethod();
		List<Value> args = invExprUnit.getArgs();
		SymbolicValue object = new MethodRepresentationValue(base, args, method, this.se);
		this.handleInvokeTag(args, base, object, method);
		results.add(new Pair<Value, SymbolicValue>(base, object));
	}

	protected abstract void handleInvokeTag(List<Value> args, Value base, SymbolicValue object, SootMethod method);

	@Override
	public void handleConstructor(InvokeExpr invExprUnit, Value base, List<Pair<Value, SymbolicValue>> results) {
		List<Value> args = invExprUnit.getArgs();
		ObjectValue object = new ObjectValue(base.getType(), args, this.se);
		this.handleConstructorTag(args, object);
		results.add(new Pair<Value, SymbolicValue>(base, object));
	}

	protected boolean isAuthorizedType(String type) {
		return this.authorizedTypes.contains(type);
	}
}