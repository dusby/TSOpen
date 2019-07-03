package com.github.dusby.tsopen.symbolicExecution.methodRecognizers.strings;

import java.util.ArrayList;
import java.util.List;

import com.github.dusby.tsopen.symbolicExecution.SymbolicExecution;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.ConstantValue;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValue;
import com.github.dusby.tsopen.utils.Constants;
import com.github.dusby.tsopen.utils.Utils;

import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;

public class ValueOfRecognition extends StringMethodsRecognitionHandler {

	public ValueOfRecognition(StringMethodsRecognitionHandler next, SymbolicExecution se) {
		super(next, se);
	}

	@Override
	public List<SymbolicValue> processStringMethod(SootMethod method, Value base, List<Value> args) {
		List<SymbolicValue> results = new ArrayList<SymbolicValue>();
		Value effectiveArg = null;
		if(method.getName().equals(Constants.VALUEOF)) {
			effectiveArg = args.get(0);
			if(effectiveArg instanceof Constant) {
				results.add(new ConstantValue((Constant)effectiveArg, this.se));
			}else {
				this.addSimpleResult(effectiveArg, results);
				for(SymbolicValue sv : results) {
					Utils.propagateTags(effectiveArg, sv, this.se);
				}
			}
			return results;
		}
		return null;
	}
}
