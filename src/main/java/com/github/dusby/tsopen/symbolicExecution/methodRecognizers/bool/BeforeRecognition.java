package com.github.dusby.tsopen.symbolicExecution.methodRecognizers.bool;

import java.util.List;

import com.github.dusby.tsopen.symbolicExecution.SymbolicExecution;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValue;
import com.github.dusby.tsopen.utils.Constants;
import com.github.dusby.tsopen.utils.Utils;

import soot.SootMethod;
import soot.Value;
import soot.tagkit.StringConstantValueTag;

public class BeforeRecognition extends BooleanMethodsRecognitionHandler {

	public BeforeRecognition(BooleanMethodsRecognitionHandler next, SymbolicExecution se) {
		super(next, se);
	}

	@Override
	public boolean processBooleanMethod(SootMethod method, Value base, SymbolicValue sv, List<Value> args) {
		Value firstArg = null;
		String methodName = method.getName();
		if(methodName.equals(Constants.BEFORE)) {
			firstArg = args.get(0);
			if(Utils.containsTag(base, Constants.NOW_TAG, this.se)) {
				if(firstArg.getType().toString().equals(Constants.JAVA_UTIL_DATE)) {
					sv.addTag(new StringConstantValueTag(Constants.SUSPICIOUS_CMP));
					return true;
				}
			}
		}
		return false;
	}

}
