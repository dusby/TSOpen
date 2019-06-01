package symbolicExecution.symbolicValues;

import java.util.List;

import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;
import symbolicExecution.SymbolicExecutioner;

public class SymbolicValue implements SymbolicValueProvider {

	private Value base;
	private List<Value> args;
	private SootMethod method;
	private SymbolicExecutioner se;
	private String contextValue;

	public SymbolicValue(Value b, List<Value> a, SootMethod m, SymbolicExecutioner se) {
		this.base = b;
		this.args = a;
		this.method = m;
		this.se = se;
		this.contextValue = this.computeValue();
	}

	private String computeValue() {
		String value = "(";
		if(this.base != null) {
			value += getValueFromModelContext(this.base);
			value+="->";
		}
		value += this.method.getName();
		value += "(";
		for(Value arg : args) {
			value += getValueFromModelContext(arg);
			if(arg != this.args.get(this.args.size() - 1)) {
				value += ", ";
			}
		}
		value += "))";
		return value;
	}

	private String getValueFromModelContext(Value v) {
		if(this.se.getModelContext().containsKey(v)) {
			return this.se.getModelContext().get(v).getContextValue();
		}else if(v instanceof Constant){
			return ((Constant)v).toString();
		}
		return v.getType().toString();
	}

	public List<Value> getArgs() {
		return this.args;
	}

	public Value getBase() {
		return this.base;
	}

	public SootMethod getMethod() {
		return this.method;
	}

	public String getContextValue() {
		return contextValue;
	}
}
