package symbolicExecution.typeRecognizers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.StringConstant;
import symbolicExecution.SymbolicExecutioner;
import symbolicExecution.symbolicValues.ConcreteValue;
import symbolicExecution.symbolicValues.SymbolicValueProvider;
import symbolicExecution.symbolicValues.SymbolicValue;

public class StringRecognizer extends RecognizerProcessor{

	public StringRecognizer(RecognizerProcessor next, SymbolicExecutioner se) {
		super(next, se);
	}

	@Override
	public Map<Value, SymbolicValueProvider> processRecognition(DefinitionStmt def) {
		Value leftOp = def.getLeftOp();
		String leftOpType = leftOp.getType().toQuotedString(); 
		if(leftOpType.equals("java.lang.String")
				|| leftOpType.equals("java.lang.StringBuilder")
				|| leftOpType.equals("java.lang.StringBuffer")) {
			HashMap<Value, SymbolicValueProvider> result = new HashMap<Value, SymbolicValueProvider>();
			Value rightOp = def.getRightOp();

			if(rightOp instanceof StringConstant) {
				result.put(leftOp, new ConcreteValue((StringConstant)rightOp));
			}
			
			if(rightOp instanceof Local) {
				result.put(leftOp, this.se.getModelContext().get(rightOp));
			}
			
			if(rightOp instanceof NewExpr) {
				// TODO maybe retrieve string in constructor
				result.put(leftOp, new ConcreteValue(StringConstant.v("")));
			}

			if(rightOp instanceof InvokeExpr) {
				InvokeExpr rightOpInvokeExpr = (InvokeExpr) rightOp;
				SootMethod m = rightOpInvokeExpr.getMethod();
				List<Value> args = rightOpInvokeExpr.getArgs();
				Value base = rightOpInvokeExpr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) rightOpInvokeExpr).getBase() : null;
				result.put(leftOp, new SymbolicValue(base, args, m, this.se));
			}
			return result;
		}
		return null;
	}

}
